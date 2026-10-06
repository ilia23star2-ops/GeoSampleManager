package com.example.geosamplemanager.data.stats

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * FIX 5.10-stat-errors-a:
 * Тесты на AutoWarnRules. Пороги и поведение счётчиков.
 * Имена тестов — латиница (правило TESTING.md).
 *
 * FIX 5.10-stat-errors-a (уточнение):
 * В reset_clearsErrorCounter исправлен ассерт: после reset()
 * три вызова одного ключа корректно достигают порога 3 и
 * триггерят (assertTrue). Раньше стоял assertFalse — ошибка.
 */
class AutoWarnRulesTest {

    @Before
    fun setUp() {
        AutoWarnRules.reset()
    }

    @After
    fun tearDown() {
        AutoWarnRules.reset()
    }

    // ============================================================
    // Поиск
    // ============================================================

    @Test
    fun fourNotFound_notTriggered() {
        repeat(4) {
            assertFalse(AutoWarnRules.onSearchNotFound())
        }
    }

    @Test
    fun fiveNotFound_triggers() {
        repeat(4) { AutoWarnRules.onSearchNotFound() }
        assertTrue(AutoWarnRules.onSearchNotFound())
    }

    @Test
    fun successResetsCounter() {
        repeat(4) { AutoWarnRules.onSearchNotFound() }
        AutoWarnRules.onSearchSuccess()
        repeat(4) {
            assertFalse(AutoWarnRules.onSearchNotFound())
        }
    }

    @Test
    fun afterTrigger_counterResets() {
        repeat(5) { AutoWarnRules.onSearchNotFound() }
        // Счётчик сброшен, следующие 4 не триггерят.
        repeat(4) {
            assertFalse(AutoWarnRules.onSearchNotFound())
        }
        assertTrue(AutoWarnRules.onSearchNotFound())
    }

    // ============================================================
    // Ошибки
    // ============================================================

    @Test
    fun twoSameErrors_notTriggered() {
        assertFalse(AutoWarnRules.onError("db", "Ошибка чтения"))
        assertFalse(AutoWarnRules.onError("db", "Ошибка чтения"))
    }

    @Test
    fun threeSameErrors_triggers() {
        AutoWarnRules.onError("db", "Ошибка чтения")
        AutoWarnRules.onError("db", "Ошибка чтения")
        assertTrue(AutoWarnRules.onError("db", "Ошибка чтения"))
    }

    @Test
    fun differentErrorBreaksSequence() {
        // A, A, B, A, A, A — три A подряд только на 4..6.
        assertFalse(AutoWarnRules.onError("db", "A"))
        assertFalse(AutoWarnRules.onError("db", "A"))
        assertFalse(AutoWarnRules.onError("db", "B"))
        assertFalse(AutoWarnRules.onError("db", "A"))
        assertFalse(AutoWarnRules.onError("db", "A"))
        assertTrue(AutoWarnRules.onError("db", "A"))
    }

    @Test
    fun differentCategories_areDifferentErrors() {
        AutoWarnRules.onError("db", "Ошибка X")
        AutoWarnRules.onError("edit", "Ошибка X")
        // Категория разная — ключ разный, счётчик начал заново.
        assertFalse(AutoWarnRules.onError("voice", "Ошибка X"))
    }

    @Test
    fun afterTrigger_errorCounterResets() {
        AutoWarnRules.onError("db", "A")
        AutoWarnRules.onError("db", "A")
        assertTrue(AutoWarnRules.onError("db", "A"))
        // После срабатывания счётчик в 0, следующие 2 не триггерят.
        assertFalse(AutoWarnRules.onError("db", "A"))
        assertFalse(AutoWarnRules.onError("db", "A"))
        assertTrue(AutoWarnRules.onError("db", "A"))
    }

    // ============================================================
    // reset
    // ============================================================

    @Test
    fun reset_clearsSearchCounter() {
        repeat(4) { AutoWarnRules.onSearchNotFound() }
        AutoWarnRules.reset()
        repeat(4) {
            assertFalse(AutoWarnRules.onSearchNotFound())
        }
    }

    @Test
    fun reset_clearsErrorCounter() {
        AutoWarnRules.onError("db", "A")
        AutoWarnRules.onError("db", "A")
        AutoWarnRules.reset()
        AutoWarnRules.onError("db", "A")
        AutoWarnRules.onError("db", "A")
        assertTrue(AutoWarnRules.onError("db", "A"))
    }

    // ============================================================
    // Независимость счётчиков
    // ============================================================

    @Test
    fun searchAndErrorCountersAreIndependent() {
        repeat(4) { AutoWarnRules.onSearchNotFound() }
        AutoWarnRules.onError("db", "A")
        AutoWarnRules.onError("db", "A")
        // Успех поиска сбрасывает только счётчик поиска.
        AutoWarnRules.onSearchSuccess()
        assertTrue(AutoWarnRules.onError("db", "A"))
    }
}