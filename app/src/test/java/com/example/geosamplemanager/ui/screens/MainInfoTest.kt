package com.example.geosamplemanager.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-main-a:
 * Тесты на MainInfo, UnfinishedOrder, ContinueInfo — чистые расчёты.
 */
class MainInfoTest {

    @Test
    fun notFound_isDifference() {
        val info = info(samples = 10, found = 3)
        assertEquals(7, info.notFound)
    }

    @Test
    fun notFound_neverNegative() {
        val info = info(samples = 5, found = 8)
        assertEquals(0, info.notFound)
    }

    @Test
    fun progressPercent_zeroWhenNoSamples() {
        val info = info(samples = 0, found = 0)
        assertEquals(0, info.progressPercent)
    }

    @Test
    fun progressPercent_fullWhenAllFound() {
        val info = info(samples = 10, found = 10)
        assertEquals(100, info.progressPercent)
    }

    @Test
    fun progressPercent_half() {
        val info = info(samples = 10, found = 5)
        assertEquals(50, info.progressPercent)
    }

    @Test
    fun progressPercent_clampedWhenFoundOver() {
        val info = info(samples = 10, found = 15)
        assertEquals(100, info.progressPercent)
    }

    @Test
    fun progressFraction_inRange() {
        val info = info(samples = 8, found = 2)
        assertEquals(0.25f, info.progressFraction, 0.001f)
    }

    @Test
    fun inProgressOrders_isOrdersMinusReady() {
        val info = info(samples = 10, found = 5, orders = 10, readyOrders = 3)
        assertEquals(7, info.inProgressOrders)
    }

    @Test
    fun inProgressOrders_neverNegative() {
        val info = info(samples = 10, found = 5, orders = 3, readyOrders = 5)
        assertEquals(0, info.inProgressOrders)
    }

    @Test
    fun isEmpty_trueWhenAllZero() {
        val info = info(samples = 0, found = 0, areas = 0, orders = 0)
        assertTrue(info.isEmpty)
    }

    @Test
    fun isEmpty_falseWhenAnyNonZero() {
        assertFalse(info(samples = 1, found = 0, areas = 0, orders = 0).isEmpty)
        assertFalse(info(samples = 0, found = 0, areas = 1, orders = 0).isEmpty)
        assertFalse(info(samples = 0, found = 0, areas = 0, orders = 1).isEmpty)
    }

    // ============ UnfinishedOrder ============

    @Test
    fun unfinished_percent_half() {
        val u = UnfinishedOrder(1L, "Наряд №27", "Коптеловский", 10, 5)
        assertEquals(50, u.percent)
    }

    @Test
    fun unfinished_percent_zeroOnEmpty() {
        val u = UnfinishedOrder(1L, "Наряд №27", "Коптеловский", 0, 0)
        assertEquals(0, u.percent)
    }

    // ============ ContinueInfo ============

    @Test
    fun continue_percent_and_fraction_consistent() {
        val c = ContinueInfo(1L, "Наряд №27", "Коптеловский", 4, 3, 0L)
        assertEquals(75, c.percent)
        assertEquals(0.75f, c.progressFraction, 0.001f)
    }

    private fun info(
        samples: Int,
        found: Int,
        areas: Int = 1,
        orders: Int = 1,
        readyOrders: Int = 0
    ) = MainInfo(
        areas = areas,
        orders = orders,
        wells = 0,
        samples = samples,
        found = found,
        photos = 0,
        notes = 0,
        readyOrders = readyOrders,
        dbSizeBytes = 0L,
        lastModified = 0L,
        freeBytes = 0L
    )
}