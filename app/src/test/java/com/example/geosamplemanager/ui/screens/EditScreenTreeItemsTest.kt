package com.example.geosamplemanager.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-edit-screen-base:
 * Тесты чистой функции buildTreeItems.
 *
 * Покрываем:
 *  - пустое дерево;
 *  - все участки свёрнуты;
 *  - один участок развёрнут — видны наряды;
 *  - участок и наряд развёрнуты — видны пробы;
 *  - порядок элементов при развороте;
 *  - ключи уникальны.
 *
 * Не покрываем (только device):
 *  - сам Compose-рендер;
 *  - поведение кликов.
 */
class EditScreenTreeItemsTest {

    private fun sample(id: String, sampleNum: String = "S$id"): SampleRow = SampleRow(
        id = id,
        groupId = "g1",
        serialNumber = id.toIntOrNull() ?: 1,
        wellNumber = "NV1366",
        sampleNumber = sampleNum,
        numberInWell = id.toIntOrNull() ?: 1,
        intervalFrom = "0",
        intervalTo = "1",
        weight = null,
        controlWeight = null,
        type = SampleType.AUGER,
        status = SampleStatus.NORMAL,
        characteristic = "",
        found = false,
        postponed = false,
        weightControl = false,
        hasNote = false,
        hasPhoto = false,
        hasImportError = false
    )

    private fun order(id: Long, num: String, samples: List<SampleRow>): EditOrderUi =
        EditOrderUi(
            orderId = id,
            orderNumber = num,
            orderTitle = "Наряд №$num",
            samples = samples
        )

    private fun area(id: Long, name: String, orders: List<EditOrderUi>): EditAreaUi =
        EditAreaUi(areaId = id, areaName = name, orders = orders)

    private fun treeOf(vararg areas: EditAreaUi) = EditTreeData(areas = areas.toList())

    @Test
    fun emptyTreeProducesEmptyItems() {
        val items = buildTreeItems(treeOf(), emptySet(), emptySet())
        assertTrue(items.isEmpty())
    }

    @Test
    fun collapsedAreaProducesOnlyAreaHeader() {
        val tree = treeOf(
            area(1, "А", listOf(order(10, "27", listOf(sample("1")))))
        )
        val items = buildTreeItems(tree, emptySet(), emptySet())
        assertEquals(1, items.size)
        assertTrue(items[0] is EditTreeItem.AreaHeader)
        assertEquals(false, (items[0] as EditTreeItem.AreaHeader).expanded)
    }

    @Test
    fun expandedAreaProducesAreaAndOrderHeaders() {
        val tree = treeOf(
            area(1, "А", listOf(
                order(10, "27", listOf(sample("1"))),
                order(11, "28", emptyList())
            ))
        )
        val items = buildTreeItems(tree, setOf(1L), emptySet())
        assertEquals(3, items.size)
        assertTrue(items[0] is EditTreeItem.AreaHeader)
        assertTrue(items[1] is EditTreeItem.OrderHeader)
        assertTrue(items[2] is EditTreeItem.OrderHeader)
    }

    @Test
    fun expandedAreaAndOrderProducesSampleItems() {
        val tree = treeOf(
            area(1, "А", listOf(
                order(10, "27", listOf(sample("1"), sample("2")))
            ))
        )
        val items = buildTreeItems(tree, setOf(1L), setOf(10L))
        assertEquals(4, items.size)
        assertTrue(items[0] is EditTreeItem.AreaHeader)
        assertTrue(items[1] is EditTreeItem.OrderHeader)
        assertTrue(items[2] is EditTreeItem.SampleItem)
        assertTrue(items[3] is EditTreeItem.SampleItem)
    }

    @Test
    fun orderCollapsedButAreaExpandedProducesNoSamples() {
        val tree = treeOf(
            area(1, "А", listOf(
                order(10, "27", listOf(sample("1")))
            ))
        )
        val items = buildTreeItems(tree, setOf(1L), emptySet())
        assertEquals(2, items.size)
        assertTrue(items[0] is EditTreeItem.AreaHeader)
        assertTrue(items[1] is EditTreeItem.OrderHeader)
    }

    @Test
    fun twoAreasOneExpandedOneNot() {
        val tree = treeOf(
            area(1, "А", listOf(order(10, "1", listOf(sample("1"))))),
            area(2, "Б", listOf(order(20, "2", listOf(sample("2")))))
        )
        val items = buildTreeItems(tree, setOf(1L), setOf(10L))
        // А (развёрнут) → Наряд 1 (развёрнут) → Sample 1
        // Б (свёрнут)
        assertEquals(4, items.size)
        assertTrue(items[0] is EditTreeItem.AreaHeader)
        assertTrue(items[1] is EditTreeItem.OrderHeader)
        assertTrue(items[2] is EditTreeItem.SampleItem)
        assertTrue(items[3] is EditTreeItem.AreaHeader)
    }

    @Test
    fun keysAreUnique() {
        val tree = treeOf(
            area(1, "А", listOf(
                order(10, "1", listOf(sample("1"), sample("2"))),
                order(11, "2", listOf(sample("3")))
            )),
            area(2, "Б", listOf(order(20, "3", listOf(sample("4")))))
        )
        val items = buildTreeItems(
            tree,
            setOf(1L, 2L),
            setOf(10L, 11L, 20L)
        )
        val keys = items.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun sampleOrderMatchesTreeOrder() {
        val tree = treeOf(
            area(1, "А", listOf(
                order(10, "1", listOf(sample("1", "S1"), sample("2", "S2"))),
                order(11, "2", listOf(sample("3", "S3")))
            ))
        )
        val items = buildTreeItems(tree, setOf(1L), setOf(10L, 11L))
        val samples = items.filterIsInstance<EditTreeItem.SampleItem>()
        assertEquals(listOf("S1", "S2", "S3"), samples.map { it.row.sampleNumber })
    }
}