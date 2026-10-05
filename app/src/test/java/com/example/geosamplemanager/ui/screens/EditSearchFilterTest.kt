package com.example.geosamplemanager.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-edit-screen-search:
 * Тесты чистой функции applyEditFilters.
 *
 * Покрываем:
 *  - пустой запрос и фильтр — дерево без изменений;
 *  - поиск по № пробы (подстрока);
 *  - поиск по № скважины (подстрока);
 *  - поиск по характеристике (без регистра);
 *  - фильтры: найден, не найден, отложен, ВК, холостая, ошибка;
 *  - комбинация поиска и фильтра;
 *  - вычистка пустых нарядов и участков.
 */
class EditSearchFilterTest {

    private fun sample(
        id: String,
        sampleNum: String,
        wellNum: String = "NV1366",
        characteristic: String = "",
        found: Boolean = false,
        postponed: Boolean = false,
        weightControl: Boolean = false,
        isBlank: Boolean = false,
        hasImportError: Boolean = false
    ): SampleRow = SampleRow(
        id = id,
        groupId = "g1",
        serialNumber = id.toIntOrNull() ?: 1,
        wellNumber = wellNum,
        sampleNumber = sampleNum,
        numberInWell = id.toIntOrNull() ?: 1,
        intervalFrom = "0",
        intervalTo = "1",
        weight = null,
        controlWeight = null,
        type = SampleType.AUGER,
        status = if (isBlank) SampleStatus.BLANK else SampleStatus.NORMAL,
        characteristic = characteristic,
        found = found,
        postponed = postponed,
        weightControl = weightControl,
        hasNote = false,
        hasPhoto = false,
        hasImportError = hasImportError
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

    private fun sampleIds(tree: EditTreeData): List<String> =
        tree.areas.flatMap { a -> a.orders.flatMap { o -> o.samples.map { it.id } } }

    // ================================================================
    // Без запроса и без фильтров
    // ================================================================

    @Test
    fun noQueryNoFiltersReturnsSameTree() {
        val tree = treeOf(
            area(1, "А", listOf(order(10, "1", listOf(sample("1", "N1")))))
        )
        val result = applyEditFilters(tree, "", emptySet())
        assertEquals(sampleIds(tree), sampleIds(result))
    }

    @Test
    fun whitespaceQueryNoFiltersReturnsSameTree() {
        val tree = treeOf(
            area(1, "А", listOf(order(10, "1", listOf(sample("1", "N1")))))
        )
        val result = applyEditFilters(tree, "   ", emptySet())
        assertEquals(sampleIds(tree), sampleIds(result))
    }

    // ================================================================
    // Поиск по номеру пробы
    // ================================================================

    @Test
    fun searchByExactSampleNumber() {
        val tree = treeOf(
            area(1, "А", listOf(
                order(10, "1", listOf(
                    sample("1", "NV136601"),
                    sample("2", "NV136602")
                ))
            ))
        )
        val result = applyEditFilters(tree, "NV136601", emptySet())
        assertEquals(listOf("1"), sampleIds(result))
    }

    @Test
    fun searchBySampleSubstring() {
        val tree = treeOf(
            area(1, "А", listOf(
                order(10, "1", listOf(
                    sample("1", "NV136601"),
                    sample("2", "NV136602"),
                    sample("3", "NV136703")
                ))
            ))
        )
        val result = applyEditFilters(tree, "13660", emptySet())
        assertEquals(listOf("1", "2"), sampleIds(result))
    }

    // ================================================================
    // Поиск по номеру скважины
    // ================================================================

    @Test
    fun searchByWellNumberDigits() {
        val tree = treeOf(
            area(1, "А", listOf(
                order(10, "1", listOf(
                    sample("1", "NV136601", wellNum = "NV1366"),
                    sample("2", "NV136701", wellNum = "NV1367")
                ))
            ))
        )
        val result = applyEditFilters(tree, "1367", emptySet())
        assertEquals(listOf("2"), sampleIds(result))
    }

    // ================================================================
    // Поиск по характеристике
    // ================================================================

    @Test
    fun searchByCharacteristicCaseInsensitive() {
        val tree = treeOf(
            area(1, "А", listOf(
                order(10, "1", listOf(
                    sample("1", "NV1", characteristic = "Делювий"),
                    sample("2", "NV2", characteristic = "Элювий")
                ))
            ))
        )
        val result = applyEditFilters(tree, "дел", emptySet())
        assertEquals(listOf("1"), sampleIds(result))
    }

    @Test
    fun searchByCharacteristicCyrillicMixedCase() {
        val tree = treeOf(
            area(1, "А", listOf(
                order(10, "1", listOf(
                    sample("1", "NV1", characteristic = "ДЕЛЮВИЙ")
                ))
            ))
        )
        val result = applyEditFilters(tree, "делювий", emptySet())
        assertEquals(listOf("1"), sampleIds(result))
    }

    // ================================================================
    // Фильтры
    // ================================================================

    @Test
    fun filterFound() {
        val tree = treeOf(
            area(1, "А", listOf(
                order(10, "1", listOf(
                    sample("1", "N1", found = true),
                    sample("2", "N2", found = false)
                ))
            ))
        )
        val result = applyEditFilters(tree, "", setOf(EditFilter.FOUND))
        assertEquals(listOf("1"), sampleIds(result))
    }

    @Test
    fun filterNotFound() {
        val tree = treeOf(
            area(1, "А", listOf(
                order(10, "1", listOf(
                    sample("1", "N1", found = true),
                    sample("2", "N2", found = false)
                ))
            ))
        )
        val result = applyEditFilters(tree, "", setOf(EditFilter.NOT_FOUND))
        assertEquals(listOf("2"), sampleIds(result))
    }

    @Test
    fun filterPostponed() {
        val tree = treeOf(
            area(1, "А", listOf(
                order(10, "1", listOf(
                    sample("1", "N1", postponed = true),
                    sample("2", "N2")
                ))
            ))
        )
        val result = applyEditFilters(tree, "", setOf(EditFilter.POSTPONED))
        assertEquals(listOf("1"), sampleIds(result))
    }

    @Test
    fun filterWeightControl() {
        val tree = treeOf(
            area(1, "А", listOf(
                order(10, "1", listOf(
                    sample("1", "N1", weightControl = true),
                    sample("2", "N2")
                ))
            ))
        )
        val result = applyEditFilters(tree, "", setOf(EditFilter.WEIGHT_CONTROL))
        assertEquals(listOf("1"), sampleIds(result))
    }

    @Test
    fun filterBlank() {
        val tree = treeOf(
            area(1, "А", listOf(
                order(10, "1", listOf(
                    sample("1", "N1", isBlank = true),
                    sample("2", "N2")
                ))
            ))
        )
        val result = applyEditFilters(tree, "", setOf(EditFilter.BLANK))
        assertEquals(listOf("1"), sampleIds(result))
    }

    @Test
    fun filterErrors() {
        val tree = treeOf(
            area(1, "А", listOf(
                order(10, "1", listOf(
                    sample("1", "N1", hasImportError = true),
                    sample("2", "N2")
                ))
            ))
        )
        val result = applyEditFilters(tree, "", setOf(EditFilter.ERRORS))
        assertEquals(listOf("1"), sampleIds(result))
    }

    // ================================================================
    // Комбинация поиска и фильтров
    // ================================================================

    @Test
    fun queryAndFilterCombined() {
        val tree = treeOf(
            area(1, "А", listOf(
                order(10, "1", listOf(
                    sample("1", "NV136601", found = true),
                    sample("2", "NV136602", found = false)
                ))
            ))
        )
        val result = applyEditFilters(tree, "NV13660", setOf(EditFilter.FOUND))
        assertEquals(listOf("1"), sampleIds(result))
    }

    @Test
    fun multipleFiltersAllLogic() {
        val tree = treeOf(
            area(1, "А", listOf(
                order(10, "1", listOf(
                    sample("1", "N1", found = true, postponed = true),
                    sample("2", "N2", found = true),
                    sample("3", "N3", postponed = true)
                ))
            ))
        )
        // Только проба, которая и найдена, и отложена.
        val result = applyEditFilters(
            tree, "", setOf(EditFilter.FOUND, EditFilter.POSTPONED)
        )
        assertEquals(listOf("1"), sampleIds(result))
    }

    // ================================================================
    // Вычистка пустых узлов
    // ================================================================

    @Test
    fun emptyOrdersAndAreasAreRemoved() {
        val tree = treeOf(
            area(1, "А", listOf(
                order(10, "1", listOf(sample("1", "N1", found = true))),
                order(11, "2", listOf(sample("2", "N2", found = false)))
            )),
            area(2, "Б", listOf(
                order(20, "3", listOf(sample("3", "N3", found = false)))
            ))
        )
        val result = applyEditFilters(tree, "", setOf(EditFilter.FOUND))
        assertEquals(1, result.areas.size)
        assertEquals("А", result.areas[0].areaName)
        assertEquals(1, result.areas[0].orders.size)
        assertEquals("1", result.areas[0].orders[0].orderNumber)
    }

    @Test
    fun noMatchesProducesEmptyTree() {
        val tree = treeOf(
            area(1, "А", listOf(
                order(10, "1", listOf(sample("1", "N1")))
            ))
        )
        val result = applyEditFilters(tree, "ZZZ", emptySet())
        assertTrue(result.areas.isEmpty())
    }

    @Test
    fun emptyTreeStaysEmpty() {
        val tree = treeOf()
        val result = applyEditFilters(tree, "query", setOf(EditFilter.FOUND))
        assertTrue(result.areas.isEmpty())
    }
}