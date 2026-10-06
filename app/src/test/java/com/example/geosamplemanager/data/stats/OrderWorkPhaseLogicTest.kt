package com.example.geosamplemanager.data.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.10-stat-activity-b:
 * Тесты чистой логики фаз наряда. Без Android, без БД.
 *
 * FIX 5.10-stat-activity-b-2 (смена модели):
 *  - тесты на decideAfterSearch / decideAfterMark удалены;
 *  - оставлены computeStatus и accumulate.
 */
class OrderWorkPhaseLogicTest {

    // ============================================================
    // computeStatus
    // ============================================================

    @Test
    fun status_allFound_isDone() {
        assertEquals(
            OrderWorkStatus.DONE,
            OrderWorkPhaseLogic.computeStatus(
                totalSamples = 15,
                foundSamples = 15,
                hasVerifyActivity = true
            )
        )
    }

    @Test
    fun status_allFound_withoutVerify_stillDone() {
        assertEquals(
            OrderWorkStatus.DONE,
            OrderWorkPhaseLogic.computeStatus(
                totalSamples = 10,
                foundSamples = 10,
                hasVerifyActivity = false
            )
        )
    }

    @Test
    fun status_partiallyFound_withVerify_isHalfDone() {
        assertEquals(
            OrderWorkStatus.HALF_DONE,
            OrderWorkPhaseLogic.computeStatus(
                totalSamples = 15,
                foundSamples = 8,
                hasVerifyActivity = true
            )
        )
    }

    @Test
    fun status_partiallyFound_withoutVerify_isInProgress() {
        assertEquals(
            OrderWorkStatus.IN_PROGRESS,
            OrderWorkPhaseLogic.computeStatus(
                totalSamples = 15,
                foundSamples = 3,
                hasVerifyActivity = false
            )
        )
    }

    @Test
    fun status_nothingFound_withoutVerify_isInProgress() {
        assertEquals(
            OrderWorkStatus.IN_PROGRESS,
            OrderWorkPhaseLogic.computeStatus(
                totalSamples = 15,
                foundSamples = 0,
                hasVerifyActivity = false
            )
        )
    }

    @Test
    fun status_emptyOrder_isInProgress() {
        assertEquals(
            OrderWorkStatus.IN_PROGRESS,
            OrderWorkPhaseLogic.computeStatus(
                totalSamples = 0,
                foundSamples = 0,
                hasVerifyActivity = false
            )
        )
    }

    @Test
    fun status_emptyOrder_withVerify_isHalfDone() {
        assertEquals(
            OrderWorkStatus.HALF_DONE,
            OrderWorkPhaseLogic.computeStatus(
                totalSamples = 0,
                foundSamples = 0,
                hasVerifyActivity = true
            )
        )
    }

    @Test
    fun status_foundMoreThanTotal_isDone() {
        assertEquals(
            OrderWorkStatus.DONE,
            OrderWorkPhaseLogic.computeStatus(
                totalSamples = 10,
                foundSamples = 12,
                hasVerifyActivity = true
            )
        )
    }

    // ============================================================
    // accumulate
    // ============================================================

    @Test
    fun accumulate_zeroElapsed_noop() {
        val r = OrderWorkPhaseLogic.accumulate(0L, 60_000L)
        assertEquals(0, r.seconds)
        assertEquals(0L, r.advanceMs)
    }

    @Test
    fun accumulate_negativeElapsed_noop() {
        val r = OrderWorkPhaseLogic.accumulate(-500L, 60_000L)
        assertEquals(0, r.seconds)
        assertEquals(0L, r.advanceMs)
    }

    @Test
    fun accumulate_800ms_returnsZeroSeconds() {
        val r = OrderWorkPhaseLogic.accumulate(800L, 60_000L)
        assertEquals(0, r.seconds)
        assertEquals(0L, r.advanceMs)
    }

    @Test
    fun accumulate_1600ms_returnsOneSecond() {
        val r = OrderWorkPhaseLogic.accumulate(1600L, 60_000L)
        assertEquals(1, r.seconds)
        assertEquals(1000L, r.advanceMs)
    }

    @Test
    fun accumulate_2400ms_returnsTwoSeconds() {
        val r = OrderWorkPhaseLogic.accumulate(2400L, 60_000L)
        assertEquals(2, r.seconds)
        assertEquals(2000L, r.advanceMs)
    }

    @Test
    fun accumulate_59999ms_returns59Seconds() {
        val r = OrderWorkPhaseLogic.accumulate(59_999L, 60_000L)
        assertEquals(59, r.seconds)
        assertEquals(59_000L, r.advanceMs)
    }

    @Test
    fun accumulate_60000ms_isIdle() {
        val r = OrderWorkPhaseLogic.accumulate(60_000L, 60_000L)
        assertEquals(0, r.seconds)
        assertEquals(-1L, r.advanceMs)
    }

    @Test
    fun accumulate_120000ms_isIdle() {
        val r = OrderWorkPhaseLogic.accumulate(120_000L, 60_000L)
        assertEquals(0, r.seconds)
        assertEquals(-1L, r.advanceMs)
    }

    @Test
    fun accumulate_threeQuickEvents_sumToTwoSeconds() {
        var phaseStartedAt = 0L
        var accumulated = 0
        val eventTimes = listOf(800L, 1600L, 2400L)
        for (t in eventTimes) {
            val r = OrderWorkPhaseLogic.accumulate(t - phaseStartedAt, 60_000L)
            accumulated += r.seconds
            phaseStartedAt += r.advanceMs
        }
        assertEquals(2, accumulated)
    }
}