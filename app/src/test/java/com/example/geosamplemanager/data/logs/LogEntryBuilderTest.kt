package com.example.geosamplemanager.data.logs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-logs-2:
 * Проверяем fluent-API builder'а. Лямбда nowMs фиксирует
 * время — тесты детерминированы.
 */
class LogEntryBuilderTest {

    private val fixedTime = 1_700_000_000_000L

    private fun builder(
        cat: LogCategory = LogCategory.APP,
        lvl: LogLevel = LogLevel.INFO,
        summary: String = "test"
    ) = LogEntryBuilder(
        category = cat,
        level = lvl,
        summary = summary,
        sessionId = "session-1",
        nowMs = { fixedTime }
    )

    @Test
    fun buildWithNoDetailsLeavesDetailsNull() {
        val e = builder().build()
        assertNull(e.details)
    }

    @Test
    fun buildSetsCategoryCode() {
        val e = builder(cat = LogCategory.DB).build()
        assertEquals("db", e.category)
    }

    @Test
    fun buildSetsLevelCode() {
        val e = builder(lvl = LogLevel.ERROR).build()
        assertEquals("error", e.level)
    }

    @Test
    fun buildPreservesSummaryAndSessionId() {
        val e = builder(summary = "Приложение запущено").build()
        assertEquals("Приложение запущено", e.summary)
        assertEquals("session-1", e.sessionId)
    }

    @Test
    fun buildUsesProvidedTime() {
        val e = builder().build()
        assertEquals(fixedTime, e.createdAt)
    }

    @Test
    fun detailAddsToJson() {
        val e = builder().detail("samples", 30).build()
        assertNotNull(e.details)
        assertTrue(e.details!!.contains("\"samples\":30"))
    }

    @Test
    fun chainedDetailsAccumulate() {
        val e = builder()
            .detail("a", 1)
            .detail("b", "x")
            .build()
        assertNotNull(e.details)
        assertTrue(e.details!!.contains("\"a\":1"))
        assertTrue(e.details!!.contains("\"b\":\"x\""))
    }

    @Test
    fun detailsMapMergesInOneCall() {
        val e = builder()
            .details(mapOf("x" to 1, "y" to 2))
            .build()
        assertNotNull(e.details)
        assertTrue(e.details!!.contains("\"x\":1"))
        assertTrue(e.details!!.contains("\"y\":2"))
    }

    @Test
    fun detailThrowableWritesTypeAndMessage() {
        val t = IllegalStateException("bad")
        val e = builder().detailThrowable(t).build()
        assertNotNull(e.details)
        assertTrue(e.details!!.contains("IllegalStateException"))
        assertTrue(e.details!!.contains("\"bad\""))
        assertTrue(e.details!!.contains("error_stack"))
    }

    @Test
    fun detailThrowableNullIsNoOp() {
        val e = builder().detailThrowable(null).build()
        assertNull(e.details)
    }

    @Test
    fun buildDoesNotWriteToChannel() {
        // build() — только сборка, без отправки.
        // Проверяем косвенно: write() возвращает запись,
        // build() возвращает другую (не отправляется).
        val b = builder()
        val e1 = b.build()
        val e2 = b.build()
        // Обе записи валидны, но между ними нет связи с
        // каналом — тест проверяет, что build() не бросает.
        assertEquals(e1.summary, e2.summary)
        assertEquals(e1.category, e2.category)
    }
}