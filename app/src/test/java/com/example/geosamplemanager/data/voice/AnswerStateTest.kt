package com.example.geosamplemanager.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Тесты маппера AnswerReason → AnswerState и лейблов индикатора.
 */
class AnswerStateTest {

    // ================================================================
    // Маппер reason → state
    // ================================================================

    @Test
    fun idleWaiting_mapsToIdle() {
        assertEquals(AnswerState.IDLE, AnswerReason.IDLE_WAITING.state)
    }

    @Test
    fun okSingle_mapsToOk() {
        assertEquals(AnswerState.OK, AnswerReason.OK_SINGLE.state)
    }

    @Test
    fun foundOtherOrder_mapsToAttention() {
        assertEquals(AnswerState.ATTENTION, AnswerReason.FOUND_OTHER_ORDER.state)
    }

    @Test
    fun foundOtherArea_mapsToAttention() {
        assertEquals(AnswerState.ATTENTION, AnswerReason.FOUND_OTHER_AREA.state)
    }

    @Test
    fun foundMultiple_mapsToAttention() {
        assertEquals(AnswerState.ATTENTION, AnswerReason.FOUND_MULTIPLE.state)
    }

    @Test
    fun foundMultipleArea_mapsToAttention() {
        assertEquals(AnswerState.ATTENTION, AnswerReason.FOUND_MULTIPLE_AREA.state)
    }

    @Test
    fun notFound_mapsToError() {
        assertEquals(AnswerState.ERROR, AnswerReason.NOT_FOUND.state)
    }

    @Test
    fun searchFailed_mapsToError() {
        assertEquals(AnswerState.ERROR, AnswerReason.SEARCH_FAILED.state)
    }

    // ================================================================
    // shortLabel
    // ================================================================

    @Test
    fun shortLabel_idle() {
        assertEquals("Жду", AnswerReason.IDLE_WAITING.shortLabel)
    }

    @Test
    fun shortLabel_ok() {
        assertEquals("Найдено", AnswerReason.OK_SINGLE.shortLabel)
    }

    @Test
    fun shortLabel_attention() {
        assertEquals("Внимание", AnswerReason.FOUND_OTHER_ORDER.shortLabel)
        assertEquals("Внимание", AnswerReason.FOUND_OTHER_AREA.shortLabel)
        assertEquals("Внимание", AnswerReason.FOUND_MULTIPLE.shortLabel)
        assertEquals("Внимание", AnswerReason.FOUND_MULTIPLE_AREA.shortLabel)
    }

    @Test
    fun shortLabel_notFound() {
        assertEquals("Не найдено", AnswerReason.NOT_FOUND.shortLabel)
    }

    @Test
    fun shortLabel_searchFailed() {
        assertEquals("Ошибка", AnswerReason.SEARCH_FAILED.shortLabel)
    }

    // ================================================================
    // detailLabel
    // ================================================================

    @Test
    fun detailLabel_empty_forIdle() {
        assertEquals("", AnswerReason.IDLE_WAITING.detailLabel)
    }

    @Test
    fun detailLabel_empty_forOk() {
        assertEquals("", AnswerReason.OK_SINGLE.detailLabel)
    }

    @Test
    fun detailLabel_otherOrder() {
        assertEquals("Другой наряд", AnswerReason.FOUND_OTHER_ORDER.detailLabel)
    }

    @Test
    fun detailLabel_otherArea() {
        assertEquals("Другой участок", AnswerReason.FOUND_OTHER_AREA.detailLabel)
    }

    @Test
    fun detailLabel_multipleOrders() {
        assertEquals("Несколько нарядов", AnswerReason.FOUND_MULTIPLE.detailLabel)
    }

    @Test
    fun detailLabel_multipleAreas() {
        assertEquals("Несколько участков", AnswerReason.FOUND_MULTIPLE_AREA.detailLabel)
    }

    @Test
    fun detailLabel_empty_forNotFound() {
        assertEquals("", AnswerReason.NOT_FOUND.detailLabel)
    }

    @Test
    fun detailLabel_empty_forSearchFailed() {
        assertEquals("", AnswerReason.SEARCH_FAILED.detailLabel)
    }

    // ================================================================
    // Полный перебор — все причины имеют состояние
    // ================================================================

    @Test
    fun allReasons_haveState() {
        AnswerReason.values().forEach { reason ->
            // Просто проверяем, что .state не падает с NPE.
            val state = reason.state
            // Один из четырёх — не бывает «никакого».
            assert(
                state == AnswerState.IDLE ||
                        state == AnswerState.OK ||
                        state == AnswerState.ATTENTION ||
                        state == AnswerState.ERROR
            )
        }
    }
}
