package com.example.geosamplemanager.data.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.10-stat-tabs:
 * Тесты на чистые решения TabVisitTracker. Без Android, без БД.
 */
class TabVisitTrackerTest {

    // ============================================================
    // decide
    // ============================================================

    @Test
    fun decide_noCurrent_opens() {
        assertEquals(
            TabVisitTracker.Decision.OPEN,
            TabVisitTracker.decide(null, "main")
        )
    }

    @Test
    fun decide_sameTab_keeps() {
        assertEquals(
            TabVisitTracker.Decision.KEEP,
            TabVisitTracker.decide("search", "search")
        )
    }

    @Test
    fun decide_differentTab_switches() {
        assertEquals(
            TabVisitTracker.Decision.SWITCH,
            TabVisitTracker.decide("search", "stats")
        )
    }

    // ============================================================
    // durationSec
    // ============================================================

    @Test
    fun durationSec_normal() {
        assertEquals(
            10,
            TabVisitTracker.durationSec(1_000_000L, 1_010_000L)
        )
    }

    @Test
    fun durationSec_negativeDelta_zero() {
        assertEquals(
            0,
            TabVisitTracker.durationSec(1_010_000L, 1_000_000L)
        )
    }

    @Test
    fun durationSec_zeroDelta_zero() {
        assertEquals(
            0,
            TabVisitTracker.durationSec(1_000_000L, 1_000_000L)
        )
    }
}