package com.example.geosamplemanager.data.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.10-stat-session:
 * Тесты на SessionTimeAccumulator — чистые расчёты.
 */
class SessionTimeAccumulatorTest {

    @Test
    fun initial_state_isZero() {
        val a = SessionTimeAccumulator()
        assertEquals(0, a.fgSec)
        assertEquals(0, a.bgSec)
    }

    @Test
    fun resume_then_pause_accumulatesFg() {
        val a = SessionTimeAccumulator()
        a.resume(1_000L)
        a.pause(11_000L)  // 10 сек fg
        assertEquals(10, a.fgSec)
        assertEquals(0, a.bgSec)
    }

    @Test
    fun pause_then_resume_accumulatesBg() {
        val a = SessionTimeAccumulator()
        a.pause(1_000L)
        a.resume(6_000L)  // 5 сек bg
        assertEquals(0, a.fgSec)
        assertEquals(5, a.bgSec)
    }

    @Test
    fun fullCycle_fg_and_bg() {
        val a = SessionTimeAccumulator()
        a.resume(0L)
        a.pause(10_000L)   // fg += 10
        a.resume(20_000L)  // bg += 10
        a.pause(25_000L)   // fg += 5
        assertEquals(15, a.fgSec)
        assertEquals(10, a.bgSec)
    }

    @Test
    fun duplicate_resume_isNoOp() {
        val a = SessionTimeAccumulator()
        a.resume(1_000L)
        a.resume(3_000L)  // не должно сбросить счётчик
        a.pause(11_000L)
        assertEquals(10, a.fgSec)
    }

    @Test
    fun duplicate_pause_isNoOp() {
        val a = SessionTimeAccumulator()
        a.pause(1_000L)
        a.pause(3_000L)
        a.resume(11_000L)
        assertEquals(10, a.bgSec)
    }

    @Test
    fun flush_closesFgInterval() {
        val a = SessionTimeAccumulator()
        a.resume(1_000L)
        a.flush(4_000L)
        assertEquals(3, a.fgSec)
    }

    @Test
    fun flush_closesBgInterval() {
        val a = SessionTimeAccumulator()
        a.pause(1_000L)
        a.flush(4_000L)
        assertEquals(3, a.bgSec)
    }

    @Test
    fun backInTime_isClampedToZero() {
        val a = SessionTimeAccumulator()
        a.resume(10_000L)
        a.pause(5_000L)  // to < from → 0
        assertEquals(0, a.fgSec)
    }

    @Test
    fun subSecond_interval_isZero() {
        val a = SessionTimeAccumulator()
        a.resume(1_000L)
        a.pause(1_500L)  // 0.5 сек → 0
        assertEquals(0, a.fgSec)
    }
}