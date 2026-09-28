package com.example.geosamplemanager.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.8.11-e4-ui-1:
 * Тесты порога показа кнопки «Наверх».
 *
 * Правило: показываем, если firstVisibleItemIndex > 10.
 */
class SearchScrollTopTest {

    @Test
    fun hiddenAtTop() {
        assertFalse(shouldShowScrollTop(0))
    }

    @Test
    fun hiddenAtSmallScroll() {
        assertFalse(shouldShowScrollTop(5))
    }

    @Test
    fun hiddenAtThreshold() {
        assertFalse(shouldShowScrollTop(10))
    }

    @Test
    fun visibleAboveThreshold() {
        assertTrue(shouldShowScrollTop(11))
    }

    @Test
    fun visibleFarAbove() {
        assertTrue(shouldShowScrollTop(50))
    }
}
