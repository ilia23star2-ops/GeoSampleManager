package com.example.geosamplemanager.data.logs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-logs-2:
 * Проверяем JSON-сериализацию details. Gson доступен в
 * test classpath (transitively от main).
 */
class DetailsJsonTest {

    @Test
    fun encodeEmptyMapProducesEmptyObject() {
        assertEquals("{}", DetailsJson.encode(emptyMap()))
    }

    @Test
    fun encodeSingleStringValue() {
        val json = DetailsJson.encode(mapOf("k" to "v"))
        assertEquals("{\"k\":\"v\"}", json)
    }

    @Test
    fun encodeIntValue() {
        val json = DetailsJson.encode(mapOf("samples" to 30))
        assertEquals("{\"samples\":30}", json)
    }

    @Test
    fun encodeMixedValues() {
        val json = DetailsJson.encode(
            mapOf(
                "samples" to 30,
                "file" to "x.gsmbackup",
                "ok" to true
            )
        )
        // Порядок ключей зависит от LinkedHashMap — сохраняем
        // как есть, Gson не сортирует.
        assertTrue(json.contains("\"samples\":30"))
        assertTrue(json.contains("\"file\":\"x.gsmbackup\""))
        assertTrue(json.contains("\"ok\":true"))
    }

    @Test
    fun encodeSkipsNullValues() {
        val json = DetailsJson.encode(mapOf("k" to null, "v" to 1))
        // Gson по умолчанию пропускает null — это ожидаемое
        // поведение, details не должен раздуваться.
        assertEquals(false, json.contains("null"))
        assertTrue(json.contains("\"v\":1"))
    }

    @Test
    fun decodeRoundTrip() {
        val original = mapOf("count" to 5, "name" to "test")
        val json = DetailsJson.encode(original)
        val decoded = DetailsJson.decode(json)
        assertNotNull(decoded)
        // Gson парсит числа как Double — проверяем через toString.
        assertEquals("5.0", decoded!!["count"].toString())
        assertEquals("test", decoded["name"])
    }

    @Test
    fun decodeInvalidReturnsNull() {
        assertNull(DetailsJson.decode("{not-json"))
    }

    @Test
    fun decodeEmptyStringReturnsNull() {
        assertNull(DetailsJson.decode(""))
    }
}