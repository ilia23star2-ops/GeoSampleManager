package com.example.geosamplemanager.ui.screens

import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-edit-viewmodel:
 * Тесты чистой логики ViewModel редактора.
 *
 * Покрываем:
 *  - buildEditTree: сортировка, фильтрация сирот, пустой случай;
 *  - EditTreeData.findSample: поиск и отсутствие.
 *
 * Не покрываем (только device-check):
 *  - подписка на Room Flow;
 *  - saveSample / deleteSample (нужен настоящий repo);
 *  - выбор и развёрнутость (Compose-состояние).
 */
class EditViewModelTest {

    private fun area(id: Long, name: String) =
        AreaEntity(id = id, areaName = name)

    private fun order(id: Long, areaId: Long, num: String) =
        OrderEntity(id = id, areaId = areaId, orderNumber = num)

    private fun sample(
        id: Long,
        orderId: Long,
        serial: Int,
        sampleNum: String,
        wellNum: String = "NV1366"
    ) = SampleEntity(
        id = id,
        orderId = orderId,
        serialNumber = serial,
        sampleNumber = sampleNum,
        wellNumber = wellNum
    )

    // ================================================================
    // buildEditTree
    // ================================================================

    @Test
    fun emptyInputProducesEmptyTree() {
        val tree = buildEditTree(emptyList(), emptyList(), emptyList())
        assertTrue(tree.areas.isEmpty())
    }

    @Test
    fun areasSortedAlphabetically() {
        val tree = buildEditTree(
            listOf(area(1, "Якутский"), area(2, "Абаканский"), area(3, "Коптеловский")),
            emptyList(),
            emptyList()
        )
        assertEquals(
            listOf("Абаканский", "Коптеловский", "Якутский"),
            tree.areas.map { it.areaName }
        )
    }

    @Test
    fun ordersWithinAreaSortedByNumber() {
        val tree = buildEditTree(
            listOf(area(1, "Test")),
            listOf(
                order(10, 1, "27"),
                order(11, 1, "5"),
                order(12, 1, "13"),
                order(13, 1, "100")
            ),
            emptyList()
        )
        assertEquals(
            listOf("100", "13", "27", "5"),
            tree.areas[0].orders.map { it.orderNumber }
        )
    }

    @Test
    fun samplesWithinOrderSortedBySerialNumber() {
        val tree = buildEditTree(
            listOf(area(1, "Test")),
            listOf(order(10, 1, "27")),
            listOf(
                sample(100, 10, 3, "N3"),
                sample(101, 10, 1, "N1"),
                sample(102, 10, 2, "N2")
            )
        )
        assertEquals(
            listOf("N1", "N2", "N3"),
            tree.areas[0].orders[0].samples.map { it.sampleNumber }
        )
    }

    @Test
    fun orderTitleBuiltFromNumber() {
        val tree = buildEditTree(
            listOf(area(1, "Test")),
            listOf(order(10, 1, "27")),
            emptyList()
        )
        assertEquals("Наряд №27", tree.areas[0].orders[0].orderTitle)
    }

    @Test
    fun orphanOrderWithoutAreaIsIgnored() {
        // Наряд ссылается на несуществующий участок id=2 — не показываем.
        val tree = buildEditTree(
            listOf(area(1, "Test")),
            listOf(order(10, 2, "27")),
            emptyList()
        )
        assertEquals(1, tree.areas.size)
        assertTrue(tree.areas[0].orders.isEmpty())
    }

    @Test
    fun orphanSamplesWithoutOrderIsIgnored() {
        // Проба ссылается на несуществующий наряд id=999 — не показываем.
        val tree = buildEditTree(
            listOf(area(1, "Test")),
            listOf(order(10, 1, "27")),
            listOf(sample(100, 999, 1, "N1"))
        )
        assertEquals(1, tree.areas.size)
        assertEquals(1, tree.areas[0].orders.size)
        assertTrue(tree.areas[0].orders[0].samples.isEmpty())
    }

    @Test
    fun multipleAreasWithOrdersAndSamples() {
        val tree = buildEditTree(
            listOf(area(1, "А"), area(2, "Б")),
            listOf(
                order(10, 1, "1"),
                order(11, 1, "2"),
                order(20, 2, "5")
            ),
            listOf(
                sample(100, 10, 1, "A1"),
                sample(101, 10, 2, "A2"),
                sample(102, 11, 1, "B1"),
                sample(103, 20, 1, "C1")
            )
        )
        assertEquals(2, tree.areas.size)
        assertEquals("А", tree.areas[0].areaName)
        assertEquals(2, tree.areas[0].orders.size)
        assertEquals(2, tree.areas[0].orders[0].samples.size)
        assertEquals(1, tree.areas[0].orders[1].samples.size)
        assertEquals(1, tree.areas[1].orders[0].samples.size)
    }

    @Test
    fun areaWithNoOrdersProducesEmptyOrderList() {
        val tree = buildEditTree(
            listOf(area(1, "Test")),
            emptyList(),
            emptyList()
        )
        assertEquals(1, tree.areas.size)
        assertTrue(tree.areas[0].orders.isEmpty())
    }

    @Test
    fun orderWithNoSamplesProducesEmptySampleList() {
        val tree = buildEditTree(
            listOf(area(1, "Test")),
            listOf(order(10, 1, "27")),
            emptyList()
        )
        assertEquals(1, tree.areas[0].orders.size)
        assertTrue(tree.areas[0].orders[0].samples.isEmpty())
    }

    // ================================================================
    // EditTreeData.findSample
    // ================================================================

    @Test
    fun findSampleReturnsExistingSample() {
        val tree = buildEditTree(
            listOf(area(1, "Test")),
            listOf(order(10, 1, "27")),
            listOf(sample(100, 10, 1, "N1"))
        )
        val found = tree.findSample("100")
        assertNotNull(found)
        assertEquals("N1", found!!.sampleNumber)
    }

    @Test
    fun findSampleReturnsNullForMissingId() {
        val tree = buildEditTree(
            listOf(area(1, "Test")),
            listOf(order(10, 1, "27")),
            listOf(sample(100, 10, 1, "N1"))
        )
        assertNull(tree.findSample("999"))
    }

    @Test
    fun findSampleReturnsNullOnEmptyTree() {
        val tree = buildEditTree(emptyList(), emptyList(), emptyList())
        assertNull(tree.findSample("1"))
    }

    @Test
    fun findSampleFindsInSecondArea() {
        val tree = buildEditTree(
            listOf(area(1, "А"), area(2, "Б")),
            listOf(order(10, 1, "1"), order(20, 2, "2")),
            listOf(
                sample(100, 10, 1, "A1"),
                sample(200, 20, 1, "B1")
            )
        )
        val found = tree.findSample("200")
        assertNotNull(found)
        assertEquals("B1", found!!.sampleNumber)
    }
}