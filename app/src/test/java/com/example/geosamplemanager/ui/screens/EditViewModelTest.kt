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
 * HOTFIX 5.9-edit-add-sample:
 *  - сортировка проб в buildEditTree изменена с serialNumber на
 *    (wellNumber, numberInWell). Тест samplesWithinOrder*
 *    обновлён: sampleNumber теперь начинается с wellNumber, чтобы
 *    numberInWell извлекался корректно.
 *  - добавлен тест сортировки по wellNumber.
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

    /**
     * Пробы в наряде сортируются по (wellNumber, numberInWell).
     * numberInWell вычисляется из sampleNumber относительно wellNumber,
     * поэтому sampleNumber должен начинаться с wellNumber.
     */
    @Test
    fun samplesWithinOrderSortedByWellAndNumberInWell() {
        val tree = buildEditTree(
            listOf(area(1, "Test")),
            listOf(order(10, 1, "27")),
            listOf(
                sample(100, 10, 3, "NV136603"),
                sample(101, 10, 1, "NV136601"),
                sample(102, 10, 2, "NV136602")
            )
        )
        assertEquals(
            listOf("NV136601", "NV136602", "NV136603"),
            tree.areas[0].orders[0].samples.map { it.sampleNumber }
        )
    }

    @Test
    fun samplesWithinOrderSortedByWellFirst() {
        // Пробы из двух скважин. Сортировка: сначала NV1366, потом NV1367.
        val tree = buildEditTree(
            listOf(area(1, "Test")),
            listOf(order(10, 1, "27")),
            listOf(
                sample(100, 10, 2, "NV136701", wellNum = "NV1367"),
                sample(101, 10, 1, "NV136601", wellNum = "NV1366")
            )
        )
        assertEquals(
            listOf("NV136601", "NV136701"),
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
        val tree = buildEditTree(
            listOf(area(1, "Test")),
            listOf(order(10, 1, "27")),
            listOf(sample(100, 999, 1, "NV136601"))
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
                sample(100, 10, 1, "NV136601"),
                sample(101, 10, 2, "NV136602"),
                sample(102, 11, 1, "NV136601"),
                sample(103, 20, 1, "NV136601")
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
            listOf(sample(100, 10, 1, "NV136601"))
        )
        val found = tree.findSample("100")
        assertNotNull(found)
        assertEquals("NV136601", found!!.sampleNumber)
    }

    @Test
    fun findSampleReturnsNullForMissingId() {
        val tree = buildEditTree(
            listOf(area(1, "Test")),
            listOf(order(10, 1, "27")),
            listOf(sample(100, 10, 1, "NV136601"))
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
                sample(100, 10, 1, "NV136601"),
                sample(200, 20, 1, "NV136601")
            )
        )
        val found = tree.findSample("200")
        assertNotNull(found)
        assertEquals("200", found!!.id)
    }

    // ================================================================
    // EditTreeData.findOrder — FIX 5.9-edit-add-sample.
    // ================================================================

    @Test
    fun findOrderReturnsExistingOrder() {
        val tree = buildEditTree(
            listOf(area(1, "Test")),
            listOf(order(10, 1, "27")),
            emptyList()
        )
        val found = tree.findOrder(10L)
        assertNotNull(found)
        assertEquals("27", found!!.orderNumber)
    }

    @Test
    fun findOrderReturnsNullForMissingId() {
        val tree = buildEditTree(
            listOf(area(1, "Test")),
            listOf(order(10, 1, "27")),
            emptyList()
        )
        assertNull(tree.findOrder(999L))
    }

    @Test
    fun findOrderReturnsNullOnEmptyTree() {
        val tree = buildEditTree(emptyList(), emptyList(), emptyList())
        assertNull(tree.findOrder(1L))
    }
}