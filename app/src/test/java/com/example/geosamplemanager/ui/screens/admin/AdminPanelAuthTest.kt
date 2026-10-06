package com.example.geosamplemanager.ui.screens.admin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.10-stat-admin-password:
 * Тесты проверки пароля. Имена — латиница.
 */
class AdminPanelAuthTest {

    @Test
    fun correct_returnsTrue() {
        assertTrue(AdminPanelAuth.checkPassword("0000"))
    }

    @Test
    fun correctWithSpaces_returnsTrue() {
        assertTrue(AdminPanelAuth.checkPassword("  0000  "))
    }

    @Test
    fun empty_returnsFalse() {
        assertFalse(AdminPanelAuth.checkPassword(""))
    }

    @Test
    fun wrongDigits_returnsFalse() {
        assertFalse(AdminPanelAuth.checkPassword("1234"))
    }

    @Test
    fun shorter_returnsFalse() {
        assertFalse(AdminPanelAuth.checkPassword("000"))
    }

    @Test
    fun longer_returnsFalse() {
        assertFalse(AdminPanelAuth.checkPassword("00000"))
    }

    @Test
    fun onlySpaces_returnsFalse() {
        assertFalse(AdminPanelAuth.checkPassword("     "))
    }

    @Test
    fun lettersAndDigits_returnsFalse() {
        assertFalse(AdminPanelAuth.checkPassword("00a0"))
    }
}