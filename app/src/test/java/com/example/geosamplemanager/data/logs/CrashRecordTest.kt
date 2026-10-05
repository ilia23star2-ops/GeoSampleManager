package com.example.geosamplemanager.data.logs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-logs-3:
 * Проверяем сериализацию CrashRecord. Round-trip — главное,
 * что должно работать без потерь.
 */
class CrashRecordTest {

    private fun sample(
        type: String = "java.lang.IllegalStateException",
        message: String? = "Something went wrong"
    ) = CrashRecord(
        timestamp = 1_700_000_000_000L,
        sessionId = "session-abc",
        threadName = "main",
        exceptionType = type,
        message = message,
        stackTrace = "at com.example.Foo.bar(Foo.kt:42)"
    )

    @Test
    fun encodeProducesValidJson() {
        val json = sample().encode()
        assertTrue(json.contains("\"sessionId\":\"session-abc\""))
        assertTrue(json.contains("\"threadName\":\"main\""))
        assertTrue(json.contains("java.lang.IllegalStateException"))
    }

    @Test
    fun roundTripPreservesAllFields() {
        val original = sample()
        val decoded = CrashRecord.decode(original.encode())
        assertNotNull(decoded)
        assertEquals(original.timestamp, decoded!!.timestamp)
        assertEquals(original.sessionId, decoded.sessionId)
        assertEquals(original.threadName, decoded.threadName)
        assertEquals(original.exceptionType, decoded.exceptionType)
        assertEquals(original.message, decoded.message)
        assertEquals(original.stackTrace, decoded.stackTrace)
    }

    @Test
    fun roundTripWithNullMessage() {
        val original = sample(message = null)
        val decoded = CrashRecord.decode(original.encode())
        assertNotNull(decoded)
        assertNull(decoded!!.message)
    }

    @Test
    fun decodeInvalidJsonReturnsNull() {
        assertNull(CrashRecord.decode("{not-json"))
    }

    @Test
    fun decodeEmptyStringReturnsNull() {
        assertNull(CrashRecord.decode(""))
    }

    @Test
    fun decodeRoundTripKeepsStackTrace() {
        val original = sample()
        val decoded = CrashRecord.decode(original.encode())
        assertNotNull(decoded)
        assertTrue(decoded!!.stackTrace.contains("Foo.kt:42"))
    }
}