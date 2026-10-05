package com.example.geosamplemanager.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-db-clean:
 * Юнит-тесты чистой модели CleanConfirmState.
 * Гарантируют, что кнопка «Очистить» активна только при явной
 * галочке подтверждения.
 */
class CleanConfirmStateTest {

    @Test
    fun initial_confirmDisabled() {
        val s = CleanConfirmState.Initial
        assertFalse(s.confirmed)
        assertFalse(s.confirmEnabled)
    }

    @Test
    fun toggle_enablesConfirm() {
        val s = CleanConfirmState.Initial.toggle()
        assertTrue(s.confirmed)
        assertTrue(s.confirmEnabled)
    }

    @Test
    fun toggleTwice_returnsToDisabled() {
        val s = CleanConfirmState.Initial.toggle().toggle()
        assertFalse(s.confirmed)
        assertFalse(s.confirmEnabled)
    }

    @Test
    fun withConfirmedTrue_confirmEnabled() {
        val s = CleanConfirmState(confirmed = true)
        assertTrue(s.confirmEnabled)
    }

    @Test
    fun withConfirmedFalse_confirmDisabled() {
        val s = CleanConfirmState(confirmed = false)
        assertFalse(s.confirmEnabled)
    }

    @Test
    fun copy_preservesFlag() {
        val s = CleanConfirmState(confirmed = true)
        assertEquals(s, s.copy())
    }
}