package com.example.geosamplemanager.data.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.10-stat-activity-a:
 * Тесты на ActivityAccumulator. Чистые функции.
 */
class ActivityAccumulatorTest {

    private val start = 1_000_000L

    @Test
    fun emptyEvents_returnsZero() {
        val active = ActivityAccumulator.computeActiveSec(
            eventsTs = emptyList(),
            sessionStart = start,
            sessionEnd = start + 60_000L
        )
        assertEquals(0, active)
    }

    @Test
    fun singleEvent_allInsideThreshold() {
        val active = ActivityAccumulator.computeActiveSec(
            eventsTs = listOf(start + 30_000L),
            sessionStart = start,
            sessionEnd = start + 60_000L
        )
        assertEquals(60, active)
    }

    @Test
    fun singleEvent_splitByLargeGap_tailCountedSeparately() {
        val active = ActivityAccumulator.computeActiveSec(
            eventsTs = listOf(start),
            sessionStart = start,
            sessionEnd = start + 600_000L
        )
        assertEquals(0, active)
    }

    @Test
    fun twoEvents_closeTogether_allActive() {
        val active = ActivityAccumulator.computeActiveSec(
            eventsTs = listOf(start + 30_000L, start + 60_000L),
            sessionStart = start,
            sessionEnd = start + 60_000L
        )
        assertEquals(60, active)
    }

    @Test
    fun twoEvents_farApart_idleGapSkipped() {
        val active = ActivityAccumulator.computeActiveSec(
            eventsTs = listOf(start, start + 300_000L),
            sessionStart = start,
            sessionEnd = start + 600_000L
        )
        assertEquals(0, active)
    }

    @Test
    fun thresholdBoundary_isIdle() {
        val active = ActivityAccumulator.computeActiveSec(
            eventsTs = listOf(start, start + 60_000L),
            sessionStart = start,
            sessionEnd = start + 60_000L
        )
        assertEquals(0, active)
    }

    @Test
    fun justUnderThreshold_isActive() {
        val active = ActivityAccumulator.computeActiveSec(
            eventsTs = listOf(start, start + 59_999L),
            sessionStart = start,
            sessionEnd = start + 59_999L
        )
        assertEquals(59, active)
    }

    @Test
    fun eventsOutsideWindow_areIgnored() {
        val active = ActivityAccumulator.computeActiveSec(
            eventsTs = listOf(
                start - 10_000L,
                start + 30_000L,
                start + 200_000L
            ),
            sessionStart = start,
            sessionEnd = start + 60_000L
        )
        assertEquals(60, active)
    }

    @Test
    fun unsortedEvents_areSorted() {
        val active = ActivityAccumulator.computeActiveSec(
            eventsTs = listOf(start + 30_000L, start + 10_000L, start + 20_000L),
            sessionStart = start,
            sessionEnd = start + 60_000L
        )
        assertEquals(60, active)
    }

    @Test
    fun invalidWindow_returnsZero() {
        val active = ActivityAccumulator.computeActiveSec(
            eventsTs = listOf(start),
            sessionStart = start + 100L,
            sessionEnd = start
        )
        assertEquals(0, active)
    }

    @Test
    fun idleSec_isFgMinusActive() {
        assertEquals(40, ActivityAccumulator.computeIdleSec(fgSec = 100, activeSec = 60))
    }

    @Test
    fun idleSec_neverNegative() {
        assertEquals(0, ActivityAccumulator.computeIdleSec(fgSec = 60, activeSec = 100))
    }

    @Test
    fun idleSec_zero() {
        assertEquals(0, ActivityAccumulator.computeIdleSec(fgSec = 0, activeSec = 0))
    }
}