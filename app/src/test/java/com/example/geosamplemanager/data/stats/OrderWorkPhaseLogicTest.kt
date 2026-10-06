package com.example.geosamplemanager.data.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.10-stat-activity-b:
 * Тесты чистой логики фаз наряда. Без Android, без БД.
 */
class OrderWorkPhaseLogicTest {

    // ============================================================
    // decideAfterSearch
    // ============================================================

    @Test
    fun search_inSearchPhase_staysInSearch() {
        val result = OrderWorkPhaseLogic.decideAfterSearch(
            currentPhase = OrderWorkPhase.SEARCH,
            eventsSinceLastMark = 100,
            threshold = 5
        )
        assertEquals(OrderWorkPhase.SEARCH, result)
    }

    @Test
    fun search_inVerifyPhase_belowThreshold_staysInVerify() {
        val result = OrderWorkPhaseLogic.decideAfterSearch(
            currentPhase = OrderWorkPhase.VERIFY,
            eventsSinceLastMark = 4,
            threshold = 5
        )
        assertEquals(OrderWorkPhase.VERIFY, result)
    }

    @Test
    fun search_inVerifyPhase_atThreshold_switchesToSearch() {
        val result = OrderWorkPhaseLogic.decideAfterSearch(
            currentPhase = OrderWorkPhase.VERIFY,
            eventsSinceLastMark = 5,
            threshold = 5
        )
        assertEquals(OrderWorkPhase.SEARCH, result)
    }

    @Test
    fun search_inVerifyPhase_aboveThreshold_switchesToSearch() {
        val result = OrderWorkPhaseLogic.decideAfterSearch(
            currentPhase = OrderWorkPhase.VERIFY,
            eventsSinceLastMark = 12,
            threshold = 5
        )
        assertEquals(OrderWorkPhase.SEARCH, result)
    }

    @Test
    fun search_thresholdOne_switchesAfterFirstEvent() {
        val result = OrderWorkPhaseLogic.decideAfterSearch(
            currentPhase = OrderWorkPhase.VERIFY,
            eventsSinceLastMark = 1,
            threshold = 1
        )
        assertEquals(OrderWorkPhase.SEARCH, result)
    }

    // ============================================================
    // decideAfterMark
    // ============================================================

    @Test
    fun mark_fromSearch_switchesToVerify() {
        assertEquals(
            OrderWorkPhase.VERIFY,
            OrderWorkPhaseLogic.decideAfterMark(OrderWorkPhase.SEARCH)
        )
    }

    @Test
    fun mark_fromVerify_staysInVerify() {
        assertEquals(
            OrderWorkPhase.VERIFY,
            OrderWorkPhaseLogic.decideAfterMark(OrderWorkPhase.VERIFY)
        )
    }

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
        // Защита: если каким-то образом found > total — всё равно done.
        assertEquals(
            OrderWorkStatus.DONE,
            OrderWorkPhaseLogic.computeStatus(
                totalSamples = 10,
                foundSamples = 12,
                hasVerifyActivity = true
            )
        )
    }
}