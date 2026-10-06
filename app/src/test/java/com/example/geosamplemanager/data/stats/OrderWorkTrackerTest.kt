package com.example.geosamplemanager.data.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.10-stat-activity-b:
 * Тесты на computePhases — разложение действий на поиск / сверку.
 *
 * FIX 5.10-stat-activity-b-2 (смена модели):
 *  - правило «интервал идёт в фазу, которой начался»;
 *  - фазу интервала определяет действие cur;
 *  - удалены тесты про порог «5 событий без отметок».
 */
class OrderWorkTrackerTest {

    private val t0 = 1_000_000L

    @Test
    fun emptyActions_zeroBoth() {
        val p = OrderWorkTracker.computePhases(emptyList())
        assertEquals(0, p.searchSec)
        assertEquals(0, p.verifySec)
    }

    @Test
    fun singleAction_noDeltas_zero() {
        val p = OrderWorkTracker.computePhases(
            listOf(OrderWorkTracker.Action(at = t0, isMark = false))
        )
        assertEquals(0, p.searchSec)
        assertEquals(0, p.verifySec)
    }

    @Test
    fun twoSearchActions_allSearch() {
        val p = OrderWorkTracker.computePhases(
            listOf(
                OrderWorkTracker.Action(at = t0, isMark = false),
                OrderWorkTracker.Action(at = t0 + 10_000L, isMark = false)
            )
        )
        assertEquals(10, p.searchSec)
        assertEquals(0, p.verifySec)
    }

    @Test
    fun searchThenMark_firstIntervalIsSearch() {
        // з1 → о1 — SEARCH (интервал начался с поиска).
        val p = OrderWorkTracker.computePhases(
            listOf(
                OrderWorkTracker.Action(at = t0, isMark = false),
                OrderWorkTracker.Action(at = t0 + 10_000L, isMark = true)
            )
        )
        assertEquals(10, p.searchSec)
        assertEquals(0, p.verifySec)
    }

    @Test
    fun searchThenTwoMarks_secondIntervalIsVerify() {
        // з1 → о1 — SEARCH, о1 → о2 — VERIFY.
        val p = OrderWorkTracker.computePhases(
            listOf(
                OrderWorkTracker.Action(at = t0, isMark = false),
                OrderWorkTracker.Action(at = t0 + 10_000L, isMark = true),
                OrderWorkTracker.Action(at = t0 + 20_000L, isMark = true)
            )
        )
        assertEquals(10, p.searchSec)
        assertEquals(10, p.verifySec)
    }

    @Test
    fun markThenSearch_markIntervalIsVerify() {
        // о1 → з2 — VERIFY (интервал начался с отметки).
        val p = OrderWorkTracker.computePhases(
            listOf(
                OrderWorkTracker.Action(at = t0, isMark = true),
                OrderWorkTracker.Action(at = t0 + 15_000L, isMark = false)
            )
        )
        assertEquals(0, p.searchSec)
        assertEquals(15, p.verifySec)
    }

    @Test
    fun seriesOfMarks_allVerify() {
        // о1 → о2 → о3 → о4 — всё VERIFY.
        val p = OrderWorkTracker.computePhases(
            listOf(
                OrderWorkTracker.Action(at = t0, isMark = true),
                OrderWorkTracker.Action(at = t0 + 10_000L, isMark = true),
                OrderWorkTracker.Action(at = t0 + 20_000L, isMark = true),
                OrderWorkTracker.Action(at = t0 + 30_000L, isMark = true)
            )
        )
        assertEquals(0, p.searchSec)
        assertEquals(30, p.verifySec)
    }

    @Test
    fun mixedWork_searchMarkMarkSearchMark() {
        // з1 → з2 → о1 → о2 → з3 → о3
        //  з1→з2: SEARCH 10s
        //  з2→о1: SEARCH 10s
        //  о1→о2: VERIFY 10s
        //  о2→з3: VERIFY 10s
        //  з3→о3: SEARCH 10s
        val p = OrderWorkTracker.computePhases(
            listOf(
                OrderWorkTracker.Action(at = t0, isMark = false),
                OrderWorkTracker.Action(at = t0 + 10_000L, isMark = false),
                OrderWorkTracker.Action(at = t0 + 20_000L, isMark = true),
                OrderWorkTracker.Action(at = t0 + 30_000L, isMark = true),
                OrderWorkTracker.Action(at = t0 + 40_000L, isMark = false),
                OrderWorkTracker.Action(at = t0 + 50_000L, isMark = true)
            )
        )
        assertEquals(30, p.searchSec)
        assertEquals(20, p.verifySec)
    }

    @Test
    fun largeGapNotCounted() {
        val p = OrderWorkTracker.computePhases(
            listOf(
                OrderWorkTracker.Action(at = t0, isMark = false),
                OrderWorkTracker.Action(at = t0 + 120_000L, isMark = false)
            )
        )
        assertEquals(0, p.searchSec)
        assertEquals(0, p.verifySec)
    }

    @Test
    fun largeGapBetweenMarks_notCounted() {
        val p = OrderWorkTracker.computePhases(
            listOf(
                OrderWorkTracker.Action(at = t0, isMark = true),
                OrderWorkTracker.Action(at = t0 + 120_000L, isMark = true)
            )
        )
        assertEquals(0, p.searchSec)
        assertEquals(0, p.verifySec)
    }

    @Test
    fun computeStatus_done() {
        assertEquals(
            OrderWorkStatus.DONE.code,
            OrderWorkTracker.computeStatus(total = 15, found = 15)
        )
    }

    @Test
    fun computeStatus_inProgress() {
        assertEquals(
            OrderWorkStatus.IN_PROGRESS.code,
            OrderWorkTracker.computeStatus(total = 15, found = 5)
        )
    }

    @Test
    fun computeStatus_emptyIsInProgress() {
        assertEquals(
            OrderWorkStatus.IN_PROGRESS.code,
            OrderWorkTracker.computeStatus(total = 0, found = 0)
        )
    }
}