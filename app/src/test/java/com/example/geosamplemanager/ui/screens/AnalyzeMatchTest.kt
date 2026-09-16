package com.example.geosamplemanager.ui.screens

import com.example.geosamplemanager.data.voice.AnswerReason
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Тесты analyzeMatch — определение причины ответа.
 *
 * Все 8 причин:
 *  • IDLE_WAITING
 *  • OK_SINGLE
 *  • FOUND_OTHER_ORDER
 *  • FOUND_OTHER_AREA
 *  • FOUND_MULTIPLE
 *  • FOUND_MULTIPLE_AREA
 *  • NOT_FOUND
 *  • SEARCH_FAILED (не тестируется через analyzeMatch — возникает только
 *    при ошибке парсера/поиска, не в этой функции).
 */
class AnalyzeMatchTest {

    // ================================================================
    // Фабрики
    // ================================================================

    private fun row(
        id: String = "r1",
        groupId: String = "g1",
        wellNumber: String = "NV1524",
        sampleNumber: String = "NV152401"
    ): SampleRow = SampleRow(
        id = id,
        groupId = groupId,
        wellNumber = wellNumber,
        sampleNumber = sampleNumber,
        numberInWell = 1,
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

    private fun group(
        id: String = "g1",
        areaTitle: String = "Тестовый",
        orderTitle: String = "Наряд №1",
        wellNumber: String = "NV1524",
        sampleNumber: String = "NV152401"
    ): SampleGroup = SampleGroup(
        id = id,
        areaTitle = areaTitle,
        orderTitle = orderTitle,
        subtitle = "",
        rows = listOf(
            row(
                groupId = id,
                wellNumber = wellNumber,
                sampleNumber = sampleNumber
            )
        )
    )

    // ================================================================
    // IDLE_WAITING
    // ================================================================

    @Test
    fun emptyQuery_returnsIdle() {
        val info = analyzeMatch("", null, null, emptyList())
        assertEquals(AnswerReason.IDLE_WAITING, info.reason)
    }

    @Test
    fun whitespaceQuery_returnsIdle() {
        val info = analyzeMatch("   ", null, null, emptyList())
        assertEquals(AnswerReason.IDLE_WAITING, info.reason)
    }

    // ================================================================
    // NOT_FOUND
    // ================================================================

    @Test
    fun noMatch_returnsNotFound() {
        val g = group(wellNumber = "NV1524", sampleNumber = "NV152401")
        val info = analyzeMatch("9999", null, null, listOf(g))
        assertEquals(AnswerReason.NOT_FOUND, info.reason)
    }

    @Test
    fun emptyGroups_returnsNotFound() {
        val info = analyzeMatch("1524", null, null, emptyList())
        assertEquals(AnswerReason.NOT_FOUND, info.reason)
    }

    // ================================================================
    // OK_SINGLE
    // ================================================================

    @Test
    fun singleMatch_noSelection_returnsOk() {
        val g = group()
        val info = analyzeMatch("1524", null, null, listOf(g))
        assertEquals(AnswerReason.OK_SINGLE, info.reason)
    }

    @Test
    fun singleMatch_sameArea_returnsOk() {
        val g = group(areaTitle = "Тестовый")
        val info = analyzeMatch("1524", "Тестовый", null, listOf(g))
        assertEquals(AnswerReason.OK_SINGLE, info.reason)
    }

    @Test
    fun singleMatch_sameAreaAndOrder_returnsOk() {
        val g = group(areaTitle = "Тестовый", orderTitle = "Наряд №1")
        val info = analyzeMatch("1524", "Тестовый", "Наряд №1", listOf(g))
        assertEquals(AnswerReason.OK_SINGLE, info.reason)
    }

    /**
     * Ключевой тест: если выбран ТОЛЬКО участок (не наряд), то результат
     * в другом наряде того же участка — НЕ «другой наряд». Мы же не сказали,
     * в каком наряде искать.
     */
    @Test
    fun singleMatch_sameAreaOtherOrder_noSelectedOrder_returnsOk() {
        val g = group(areaTitle = "Тестовый", orderTitle = "Наряд №2")
        val info = analyzeMatch("1524", "Тестовый", null, listOf(g))
        assertEquals(AnswerReason.OK_SINGLE, info.reason)
    }

    // ================================================================
    // FOUND_OTHER_AREA
    // ================================================================

    @Test
    fun singleMatch_otherArea_returnsOtherArea() {
        val g = group(areaTitle = "Тестовый")
        val info = analyzeMatch("1524", "Коптеловский", null, listOf(g))
        assertEquals(AnswerReason.FOUND_OTHER_AREA, info.reason)
    }

    @Test
    fun singleMatch_otherArea_withSelectedOrder_returnsOtherArea() {
        // selectedOrder выбран, но ответ вообще из другого участка —
        // сначала срабатывает OTHER_AREA (более сильное условие).
        val g = group(areaTitle = "Тестовый", orderTitle = "Наряд №1")
        val info = analyzeMatch("1524", "Коптеловский", "Наряд №5", listOf(g))
        assertEquals(AnswerReason.FOUND_OTHER_AREA, info.reason)
    }

    // ================================================================
    // FOUND_OTHER_ORDER
    // ================================================================

    @Test
    fun singleMatch_otherOrder_sameArea_returnsOtherOrder() {
        val g = group(areaTitle = "Тестовый", orderTitle = "Наряд №2")
        val info = analyzeMatch("1524", "Тестовый", "Наряд №1", listOf(g))
        assertEquals(AnswerReason.FOUND_OTHER_ORDER, info.reason)
    }

    // ================================================================
    // FOUND_MULTIPLE
    // ================================================================

    @Test
    fun twoGroups_sameArea_returnsMultiple() {
        val g1 = group(id = "g1", areaTitle = "Тестовый", orderTitle = "Наряд №1")
        val g2 = group(id = "g2", areaTitle = "Тестовый", orderTitle = "Наряд №2")
        val info = analyzeMatch("1524", null, null, listOf(g1, g2))
        assertEquals(AnswerReason.FOUND_MULTIPLE, info.reason)
    }

    @Test
    fun threeGroups_sameArea_returnsMultiple() {
        val g1 = group(id = "g1", areaTitle = "Тестовый", orderTitle = "Наряд №1")
        val g2 = group(id = "g2", areaTitle = "Тестовый", orderTitle = "Наряд №2")
        val g3 = group(id = "g3", areaTitle = "Тестовый", orderTitle = "Наряд №7")
        val info = analyzeMatch("1524", null, null, listOf(g1, g2, g3))
        assertEquals(AnswerReason.FOUND_MULTIPLE, info.reason)
    }

    // ================================================================
    // FOUND_MULTIPLE_AREA
    // ================================================================

    @Test
    fun twoGroups_differentAreas_returnsMultipleArea() {
        val g1 = group(id = "g1", areaTitle = "Тестовый", orderTitle = "Наряд №1")
        val g2 = group(id = "g2", areaTitle = "Коптеловский", orderTitle = "Наряд №27")
        val info = analyzeMatch("1524", null, null, listOf(g1, g2))
        assertEquals(AnswerReason.FOUND_MULTIPLE_AREA, info.reason)
    }

    @Test
    fun twoGroups_differentAreas_withSelectedArea_returnsMultipleArea() {
        // Выбран участок «Тестовый», но матч нашёлся и в Коптеловском тоже.
        val g1 = group(id = "g1", areaTitle = "Тестовый", orderTitle = "Наряд №1")
        val g2 = group(id = "g2", areaTitle = "Коптеловский", orderTitle = "Наряд №27")
        val info = analyzeMatch("1524", "Тестовый", null, listOf(g1, g2))
        assertEquals(AnswerReason.FOUND_MULTIPLE_AREA, info.reason)
    }

    // ================================================================
    // Сопоставление по sample_number
    // ================================================================

    @Test
    fun matchBySampleNumber_returnsOk() {
        val g = group(wellNumber = "NV1524", sampleNumber = "NV152401")
        val info = analyzeMatch("152401", null, null, listOf(g))
        assertEquals(AnswerReason.OK_SINGLE, info.reason)
    }

    // ================================================================
    // Сопоставление по нормализованному номеру (с буквами)
    // ================================================================

    @Test
    fun matchByNormalizedDigits_returnsOk() {
        // «NV1524» нормализуется в «1524».
        val g = group(wellNumber = "NV1524", sampleNumber = "NV152401")
        val info = analyzeMatch("1524", null, null, listOf(g))
        assertEquals(AnswerReason.OK_SINGLE, info.reason)
    }

    // ================================================================
    // Приоритет причин
    // ================================================================

    /**
     * Если выбран участок, ответ в другом участке, но при этом их несколько —
     * побеждает FOUND_MULTIPLE_AREA (более специфичное).
     */
    @Test
    fun multipleOtherAreas_returnsMultipleAreaNotOtherArea() {
        val g1 = group(id = "g1", areaTitle = "Коптеловский", orderTitle = "Наряд №27")
        val g2 = group(id = "g2", areaTitle = "Актайский", orderTitle = "Наряд №7")
        val info = analyzeMatch("1524", "Тестовый", null, listOf(g1, g2))
        assertEquals(AnswerReason.FOUND_MULTIPLE_AREA, info.reason)
    }
}
