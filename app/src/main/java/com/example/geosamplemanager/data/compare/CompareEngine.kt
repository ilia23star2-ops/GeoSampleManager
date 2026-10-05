package com.example.geosamplemanager.data.compare

import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.SampleEntity

/**
 * FIX 5.9-db-compare (fix-2):
 * Строит 4 дерева сравнения:
 *   - inArchive — только в архиве,
 *   - matched — совпадает,
 *   - different — отличается,
 *   - myOnly — только в текущей БД.
 *
 * Ключи сопоставления:
 *   участки — area_name,
 *   наряды — (area_name, order_number),
 *   пробы — (area_name, order_number, sample_number),
 *   скважины — (area_name, order_number, well_number).
 *
 * Чистая функция — принимает готовые списки. Покрывается
 * юнит-тестами.
 */
object CompareEngine {

    fun buildResult(
        fileName: String,
        myAreas: List<AreaEntity>,
        theirAreas: List<AreaEntity>,
        myOrders: List<OrderEntity>,
        theirOrders: List<OrderEntity>,
        mySamples: List<SampleEntity>,
        theirSamples: List<SampleEntity>,
        myWellsByOrder: Map<Long, List<String>>,
        theirWellsByOrder: Map<Long, List<String>>,
        conflicts: List<ConflictInfo>
    ): CompareResult {
        val myAreaById = myAreas.associateBy { it.id }
        val theirAreaById = theirAreas.associateBy { it.id }
        val myOrderById = myOrders.associateBy { it.id }
        val theirOrderById = theirOrders.associateBy { it.id }

        val myOrderKeys = myOrders.mapNotNull { o ->
            myAreaById[o.areaId]?.let { it.areaName to o.orderNumber }
        }.toSet()
        val theirOrderKeys = theirOrders.mapNotNull { o ->
            theirAreaById[o.areaId]?.let { it.areaName to o.orderNumber }
        }.toSet()

        val mySampleKeys = mySamples.mapNotNull { s ->
            val ord = myOrderById[s.orderId] ?: return@mapNotNull null
            val areaName = myAreaById[ord.areaId]?.areaName ?: return@mapNotNull null
            Triple(areaName, ord.orderNumber, s.sampleNumber)
        }.toSet()
        val theirSampleKeys = theirSamples.mapNotNull { s ->
            val ord = theirOrderById[s.orderId] ?: return@mapNotNull null
            val areaName = theirAreaById[ord.areaId]?.areaName ?: return@mapNotNull null
            Triple(areaName, ord.orderNumber, s.sampleNumber)
        }.toSet()

        val myWellKeys = myWellsByOrder.flatMap { (orderId, list) ->
            val ord = myOrderById[orderId] ?: return@flatMap emptyList()
            val areaName = myAreaById[ord.areaId]?.areaName ?: return@flatMap emptyList()
            list.map { Triple(areaName, ord.orderNumber, it) }
        }.toSet()
        val theirWellKeys = theirWellsByOrder.flatMap { (orderId, list) ->
            val ord = theirOrderById[orderId] ?: return@flatMap emptyList()
            val areaName = theirAreaById[ord.areaId]?.areaName ?: return@flatMap emptyList()
            list.map { Triple(areaName, ord.orderNumber, it) }
        }.toSet()

        val inArchive = buildTree(
            areas = theirAreas,
            orders = theirOrders,
            samples = theirSamples,
            wellsByOrder = theirWellsByOrder,
            includeOrder = { a, o -> (a to o) !in myOrderKeys },
            includeSample = { a, o, s -> Triple(a, o, s) !in mySampleKeys },
            includeWell = { a, o, w -> Triple(a, o, w) !in myWellKeys }
        )

        val matched = buildTree(
            areas = theirAreas,
            orders = theirOrders,
            samples = theirSamples,
            wellsByOrder = theirWellsByOrder,
            includeOrder = { a, o -> (a to o) in myOrderKeys },
            includeSample = { a, o, s -> Triple(a, o, s) in mySampleKeys },
            includeWell = { a, o, w -> Triple(a, o, w) in myWellKeys }
        )

        val myOnly = buildTree(
            areas = myAreas,
            orders = myOrders,
            samples = mySamples,
            wellsByOrder = myWellsByOrder,
            includeOrder = { a, o -> (a to o) !in theirOrderKeys },
            includeSample = { a, o, s -> Triple(a, o, s) !in theirSampleKeys },
            includeWell = { a, o, w -> Triple(a, o, w) !in theirWellKeys }
        )

        val different = buildDifferentTree(conflicts)

        return CompareResult(
            fileName = fileName,
            inArchive = inArchive,
            matched = matched,
            different = different,
            myOnly = myOnly
        )
    }

    private fun buildTree(
        areas: List<AreaEntity>,
        orders: List<OrderEntity>,
        samples: List<SampleEntity>,
        wellsByOrder: Map<Long, List<String>>,
        includeOrder: (String, String) -> Boolean,
        includeSample: (String, String, String) -> Boolean,
        includeWell: (String, String, String) -> Boolean
    ): CompareTree {
        val areaById = areas.associateBy { it.id }
        val samplesByOrderId = samples.groupBy { it.orderId }

        val areaMap = LinkedHashMap<String, LinkedHashMap<String, MutableBucket>>()

        for (order in orders) {
            val area = areaById[order.areaId] ?: continue
            val areaName = area.areaName
            val orderNumber = order.orderNumber

            val includedSamples = samplesByOrderId[order.id]
                .orEmpty()
                .filter { includeSample(areaName, orderNumber, it.sampleNumber) }

            val includedWells = wellsByOrder[order.id]
                .orEmpty()
                .filter { includeWell(areaName, orderNumber, it) }

            val includeThisOrder = includeOrder(areaName, orderNumber) ||
                    includedSamples.isNotEmpty() ||
                    includedWells.isNotEmpty()

            if (!includeThisOrder) continue

            val bucket = areaMap
                .getOrPut(areaName) { LinkedHashMap() }
                .getOrPut(orderNumber) { MutableBucket() }
            bucket.samples.addAll(includedSamples.map { it.sampleNumber })
            bucket.wells.addAll(includedWells)
        }

        return CompareTree(
            areas = areaMap.entries
                .sortedBy { it.key }
                .map { (areaName, orderMap) ->
                    CompareAreaNode(
                        areaName = areaName,
                        orders = orderMap.entries
                            .sortedBy { it.key }
                            .map { (orderNumber, bucket) ->
                                CompareOrderNode(
                                    orderNumber = orderNumber,
                                    samples = bucket.samples
                                        .distinct()
                                        .sorted()
                                        .map { CompareSampleNode(sampleNumber = it) },
                                    wells = bucket.wells
                                        .distinct()
                                        .sorted()
                                        .map { CompareWellNode(wellNumber = it) }
                                )
                            }
                    )
                }
        )
    }

    private fun buildDifferentTree(conflicts: List<ConflictInfo>): CompareTree {
        if (conflicts.isEmpty()) return CompareTree()

        val areaMap = LinkedHashMap<String, LinkedHashMap<String, MutableList<CompareSampleNode>>>()

        for (c in conflicts) {
            areaMap
                .getOrPut(c.areaName) { LinkedHashMap() }
                .getOrPut(c.orderNumber) { mutableListOf() }
                .add(
                    CompareSampleNode(
                        sampleNumber = c.sampleNumber,
                        note = c.fieldLabels
                    )
                )
        }

        return CompareTree(
            areas = areaMap.entries
                .sortedBy { it.key }
                .map { (areaName, orderMap) ->
                    CompareAreaNode(
                        areaName = areaName,
                        orders = orderMap.entries
                            .sortedBy { it.key }
                            .map { (orderNumber, samples) ->
                                CompareOrderNode(
                                    orderNumber = orderNumber,
                                    samples = samples.sortedBy { it.sampleNumber }
                                )
                            }
                    )
                }
        )
    }

    private class MutableBucket {
        val samples = mutableListOf<String>()
        val wells = mutableListOf<String>()
    }
}