package com.example.geosamplemanager.data.merge

import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-db-merge-v2/1:
 * Юнит-тесты чистой логики плана слияния.
 * IO (распаковка, temp Room, apply) — device-check.
 */
class MergeEngineTest {

    private fun area(id: Long, name: String): AreaEntity =
        AreaEntity(id = id, areaName = name, createdDate = id * 1000L)

    private fun order(id: Long, areaId: Long, number: String): OrderEntity =
        OrderEntity(
            id = id,
            areaId = areaId,
            orderNumber = number,
            createdDate = id * 1000L
        )

    // ============================================================
    // planAreas
    // ============================================================

    @Test
    fun planAreas_bothEmpty_returnsEmptyPlan() {
        val p = MergeEngine.planAreas(emptyList(), emptyList())
        assertEquals(0, p.existing.size)
        assertEquals(0, p.toAdd.size)
        assertEquals(0, p.duplicatesInMine.size)
    }

    @Test
    fun planAreas_onlyMine_returnsEmptyPlan() {
        val my = listOf(area(1, "Поле 1"), area(2, "Поле 2"))
        val p = MergeEngine.planAreas(my, emptyList())
        assertEquals(0, p.existing.size)
        assertEquals(0, p.toAdd.size)
    }

    @Test
    fun planAreas_onlyTheirs_allToAdd() {
        val their = listOf(area(10, "Поле 1"), area(11, "Поле 2"))
        val p = MergeEngine.planAreas(emptyList(), their)
        assertEquals(0, p.existing.size)
        assertEquals(2, p.toAdd.size)
        assertEquals(10L, p.toAdd[0].theirId)
        assertEquals("Поле 1", p.toAdd[0].entity.areaName)
    }

    @Test
    fun planAreas_matchByName_goesToExisting() {
        val my = listOf(area(1, "Поле 1"))
        val their = listOf(area(10, "Поле 1"))
        val p = MergeEngine.planAreas(my, their)
        assertEquals(1, p.existing.size)
        assertEquals(1L, p.existing[10L])
        assertEquals(0, p.toAdd.size)
    }

    @Test
    fun planAreas_noMatch_goesToAdd() {
        val my = listOf(area(1, "Поле A"))
        val their = listOf(area(10, "Поле B"))
        val p = MergeEngine.planAreas(my, their)
        assertEquals(0, p.existing.size)
        assertEquals(1, p.toAdd.size)
        assertEquals(10L, p.toAdd[0].theirId)
    }

    @Test
    fun planAreas_duplicatesInMine_picksMinId() {
        // У меня два «Поле 1» — берём MIN(id) = 2 (заведён раньше).
        val my = listOf(area(5, "Поле 1"), area(2, "Поле 1"))
        val their = listOf(area(10, "Поле 1"))
        val p = MergeEngine.planAreas(my, their)
        assertEquals(1, p.existing.size)
        assertEquals(2L, p.existing[10L])
        assertEquals(1, p.duplicatesInMine.size)
        assertEquals("Поле 1", p.duplicatesInMine[0])
    }

    @Test
    fun planAreas_duplicatesInMine_reportedEvenWithoutMatch() {
        val my = listOf(area(5, "Поле 1"), area(2, "Поле 1"))
        val their = listOf(area(10, "Другое"))
        val p = MergeEngine.planAreas(my, their)
        assertEquals(1, p.duplicatesInMine.size)
    }

    @Test
    fun planAreas_mixed_matchAndAdd() {
        val my = listOf(area(1, "Поле 1"), area(2, "Поле 2"))
        val their = listOf(
            area(10, "Поле 1"),
            area(11, "Поле 3"),
            area(12, "Поле 2")
        )
        val p = MergeEngine.planAreas(my, their)
        assertEquals(2, p.existing.size)
        assertEquals(1L, p.existing[10L])
        assertEquals(2L, p.existing[12L])
        assertEquals(1, p.toAdd.size)
        assertEquals(11L, p.toAdd[0].theirId)
    }

    // ============================================================
    // planOrders
    // ============================================================

    @Test
    fun planOrders_bothEmpty_returnsEmptyPlan() {
        val p = MergeEngine.planOrders(
            myOrders = emptyList(),
            theirOrders = emptyList(),
            areaIdMap = emptyMap()
        )
        assertEquals(0, p.existing.size)
        assertEquals(0, p.toAdd.size)
        assertEquals(0, p.skippedOrphans)
    }

    @Test
    fun planOrders_matchByAreaAndNumber_goesToExisting() {
        val my = listOf(order(1, areaId = 1, number = "42"))
        val their = listOf(order(10, areaId = 100, number = "42"))
        val p = MergeEngine.planOrders(
            myOrders = my,
            theirOrders = their,
            areaIdMap = mapOf(100L to 1L)
        )
        assertEquals(1, p.existing.size)
        assertEquals(1L, p.existing[10L])
        assertEquals(0, p.toAdd.size)
    }

    @Test
    fun planOrders_newAreaFromNewAreaMap_goesToAdd() {
        val p = MergeEngine.planOrders(
            myOrders = emptyList(),
            theirOrders = listOf(order(10, areaId = 100, number = "42")),
            areaIdMap = emptyMap(),
            newlyAddedAreaIds = mapOf(100L to 555L)
        )
        assertEquals(1, p.toAdd.size)
        assertEquals(10L, p.toAdd[0].theirId)
        assertEquals(555L, p.toAdd[0].entity.areaId)
        assertEquals("42", p.toAdd[0].entity.orderNumber)
    }

    @Test
    fun planOrders_unknownArea_goesToSkipped() {
        val p = MergeEngine.planOrders(
            myOrders = emptyList(),
            theirOrders = listOf(order(10, areaId = 999, number = "42")),
            areaIdMap = mapOf(100L to 1L)
        )
        assertEquals(0, p.toAdd.size)
        assertEquals(1, p.skippedOrphans)
    }

    @Test
    fun planOrders_sameAreaDifferentNumbers_allToAdd() {
        val p = MergeEngine.planOrders(
            myOrders = listOf(order(1, areaId = 1, number = "1")),
            theirOrders = listOf(
                order(10, areaId = 100, number = "2"),
                order(11, areaId = 100, number = "3")
            ),
            areaIdMap = mapOf(100L to 1L)
        )
        assertEquals(2, p.toAdd.size)
        assertEquals(0, p.existing.size)
        assertEquals(1L, p.toAdd[0].entity.areaId)
        assertEquals("2", p.toAdd[0].entity.orderNumber)
    }

    @Test
    fun planOrders_mixed_existingAndNew() {
        val my = listOf(order(1, areaId = 1, number = "1"))
        val their = listOf(
            order(10, areaId = 100, number = "1"),  // match
            order(11, areaId = 100, number = "2")   // add
        )
        val p = MergeEngine.planOrders(
            myOrders = my,
            theirOrders = their,
            areaIdMap = mapOf(100L to 1L)
        )
        assertEquals(1, p.existing.size)
        assertEquals(1L, p.existing[10L])
        assertEquals(1, p.toAdd.size)
        assertEquals(11L, p.toAdd[0].theirId)
    }

    @Test
    fun planOrders_sameNumberInDifferentAreas_noConflict() {
        // У меня "42" в areaId=1. В архиве "42" в areaId=100 -> areaId=2.
        // Это разные наряды, должен добавиться.
        val my = listOf(order(1, areaId = 1, number = "42"))
        val their = listOf(order(10, areaId = 100, number = "42"))
        val p = MergeEngine.planOrders(
            myOrders = my,
            theirOrders = their,
            areaIdMap = mapOf(100L to 2L)
        )
        assertEquals(0, p.existing.size)
        assertEquals(1, p.toAdd.size)
        assertEquals(2L, p.toAdd[0].entity.areaId)
    }

    // ============================================================
    // MergeStats
    // ============================================================

    @Test
    fun mergeStats_fromPlans_countsCorrectly() {
        val areaPlan = AreaPlan(
            existing = mapOf(10L to 1L, 11L to 2L),
            toAdd = listOf(
                AreaToAdd(20L, area(0, "Поле 3")),
                AreaToAdd(21L, area(0, "Поле 4"))
            ),
            duplicatesInMine = emptyList()
        )
        val orderPlan = OrderPlan(
            existing = mapOf(100L to 5L),
            toAdd = listOf(
                OrderToAdd(200L, order(0, 1, "1")),
                OrderToAdd(201L, order(0, 1, "2")),
                OrderToAdd(202L, order(0, 2, "3"))
            ),
            skippedOrphans = 7
        )
        val s = MergeStats.from(areaPlan, orderPlan)
        assertEquals(2, s.areasAdded)
        assertEquals(2, s.areasMatched)
        assertEquals(3, s.ordersAdded)
        assertEquals(1, s.ordersMatched)
        assertEquals(7, s.ordersSkipped)
    }

    @Test
    fun mergeStats_emptyPlans_allZero() {
        val areaPlan = AreaPlan(emptyMap(), emptyList(), emptyList())
        val orderPlan = OrderPlan(emptyMap(), emptyList(), 0)
        val s = MergeStats.from(areaPlan, orderPlan)
        assertEquals(0, s.areasAdded)
        assertEquals(0, s.areasMatched)
        assertEquals(0, s.ordersAdded)
        assertEquals(0, s.ordersMatched)
        assertEquals(0, s.ordersSkipped)
    }

    @Test
    fun planAreas_preservesCreatedDateOnAdd() {
        val their = listOf(AreaEntity(id = 10, areaName = "A", createdDate = 777L))
        val p = MergeEngine.planAreas(emptyList(), their)
        assertEquals(777L, p.toAdd[0].entity.createdDate)
    }

    @Test
    fun planOrders_zeroesIdsOnAdd() {
        val p = MergeEngine.planOrders(
            myOrders = emptyList(),
            theirOrders = listOf(order(10, areaId = 100, number = "42")),
            areaIdMap = mapOf(100L to 1L)
        )
        assertTrue(p.toAdd[0].entity.id == 0L)
    }
}