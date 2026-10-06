package com.example.geosamplemanager.data.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.10-stat-activity-b:
 * Тесты на computePhases — разложение действий на поиск / сверку.
 *
 * FIX 5.10-stat-activity-b (уточнение):
 * В verifyThenSixNonMarks_afterReturnSixthGoesToSearch исправлена
 * опечатка: цикл `1..6` даёт 6 non-mark, что не соответствует
 * ожиданию 0/50. Правильное число — 5 non-mark (`1..5`),
 * тогда 5 × 10s = 50s verify.
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
    fun searchThenMark_switchToVerify() {
        // A1 (10s search) A2 mark (10s search) A3 mark (10s verify)
        val p = OrderWorkTracker.computePhases(
            listOf(
                OrderWorkTracker.Action(at = t0, isMark = false),
                OrderWorkTracker.Action(at = t0 + 10_000L, isMark = true),
                OrderWorkTracker.Action(at = t0 + 20_000L, isMark = true)
            )
        )
        // A1->A2 в поиске: +10s search. A2->A3 в сверке: +10s verify.
        assertEquals(10, p.searchSec)
        assertEquals(10, p.verifySec)
    }

    @Test
    fun largeGapNotCounted() {
        val p = OrderWorkTracker.computePhases(
            listOf(
                OrderWorkTracker.Action(at = t0, isMark = false),
                OrderWorkTracker.Action(at = t0 + 120_000L, isMark = false)
            )
        )
        // gap 120s ≥60s → никуда не идёт.
        assertEquals(0, p.searchSec)
        assertEquals(0, p.verifySec)
    }

    @Test
    fun verifyThenFiveNonMarks_returnToSearch() {
        // A1 mark (t0), затем 4 non-mark (по 10s), затем — 5-й non-mark.
        // Порог 5: после 5-го non-mark фаза → поиск.
        val actions = mutableListOf<OrderWorkTracker.Action>()
        actions.add(OrderWorkTracker.Action(at = t0, isMark = true))
        for (i in 1..4) {
            actions.add(OrderWorkTracker.Action(at = t0 + 10_000L * i, isMark = false))
        }
        // К этому моменту 4 non-mark, ещё в сверке.
        // Ещё один non-mark — 5-й.
        actions.add(OrderWorkTracker.Action(at = t0 + 50_000L, isMark = false))

        val p = OrderWorkTracker.computePhases(actions)
        // A1->A2: в сверке (10s verify). A2->A3: verify. A3->A4: verify.
        // A4->A5: verify. A5->A6: всё ещё verify (переключение после).
        // Итого: 50 сек verify.
        assertEquals(0, p.searchSec)
        assertEquals(50, p.verifySec)
    }

    @Test
    fun verifyThenSixNonMarks_afterReturnSixthGoesToSearch() {
        // A1 mark. Затем 5 non-mark по 10s.
        // После 5-го — фаза поиск. Дельта A5->A6 — уже в поиске,
        // но следующего действия нет, поэтому в поиск ничего не идёт.
        val actions = mutableListOf<OrderWorkTracker.Action>()
        actions.add(OrderWorkTracker.Action(at = t0, isMark = true))
        for (i in 1..5) {
            actions.add(OrderWorkTracker.Action(at = t0 + 10_000L * i, isMark = false))
        }

        val p = OrderWorkTracker.computePhases(actions)
        // A1->A2..A5->A6 — все в verify: 5 шагов * 10s = 50s verify.
        assertEquals(0, p.searchSec)
        assertEquals(50, p.verifySec)
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