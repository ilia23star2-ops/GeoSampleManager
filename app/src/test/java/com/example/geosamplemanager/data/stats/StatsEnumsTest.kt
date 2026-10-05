package com.example.geosamplemanager.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.10-stat-model:
 * Тесты на enum'ы теневой статистики. Чистые функции, без Android.
 */
class StatsEnumsTest {

    // ============ EventLevel ============

    @Test
    fun eventLevel_fromCode_knownCode_returnsEnum() {
        assertEquals(EventLevel.INFO, EventLevel.fromCode("info"))
        assertEquals(EventLevel.WARN, EventLevel.fromCode("warn"))
        assertEquals(EventLevel.ERROR, EventLevel.fromCode("error"))
    }

    @Test
    fun eventLevel_fromCode_unknown_returnsInfo() {
        assertEquals(EventLevel.INFO, EventLevel.fromCode("debug"))
        assertEquals(EventLevel.INFO, EventLevel.fromCode(null))
        assertEquals(EventLevel.INFO, EventLevel.fromCode(""))
    }

    @Test
    fun eventLevel_codesAreUnique() {
        val codes = EventLevel.values().map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    // ============ EventCategory ============

    @Test
    fun eventCategory_fromCode_knownCodes() {
        assertEquals(EventCategory.APP, EventCategory.fromCode("app"))
        assertEquals(EventCategory.NAV, EventCategory.fromCode("nav"))
        assertEquals(EventCategory.SEARCH, EventCategory.fromCode("search"))
        assertEquals(EventCategory.VOICE, EventCategory.fromCode("voice"))
        assertEquals(EventCategory.MARK, EventCategory.fromCode("mark"))
        assertEquals(EventCategory.EDIT, EventCategory.fromCode("edit"))
        assertEquals(EventCategory.DB, EventCategory.fromCode("db"))
        assertEquals(EventCategory.ERROR, EventCategory.fromCode("error"))
    }

    @Test
    fun eventCategory_fromCode_unknown_returnsNull() {
        assertNull(EventCategory.fromCode("other"))
        assertNull(EventCategory.fromCode(null))
        assertNull(EventCategory.fromCode(""))
    }

    @Test
    fun eventCategory_codesAreUnique() {
        val codes = EventCategory.values().map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    // ============ TabKind ============

    @Test
    fun tabKind_fromCode_knownCodes() {
        assertEquals(TabKind.MAIN, TabKind.fromCode("main"))
        assertEquals(TabKind.SEARCH, TabKind.fromCode("search"))
        assertEquals(TabKind.DB, TabKind.fromCode("db"))
        assertEquals(TabKind.SETTINGS, TabKind.fromCode("settings"))
    }

    @Test
    fun tabKind_fromCode_unknown_returnsNull() {
        assertNull(TabKind.fromCode("unknown"))
        assertNull(TabKind.fromCode(null))
    }

    @Test
    fun tabKind_everyTabHasNonEmptyTitle() {
        TabKind.values().forEach { t ->
            assertTrue("TabKind.${t.name} без названия", t.title.isNotBlank())
        }
    }

    @Test
    fun tabKind_codesAreUnique() {
        val codes = TabKind.values().map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    // ============ OrderWorkStatus ============

    @Test
    fun orderWorkStatus_fromCode_knownCodes() {
        assertEquals(OrderWorkStatus.IN_PROGRESS, OrderWorkStatus.fromCode("in_progress"))
        assertEquals(OrderWorkStatus.HALF_DONE, OrderWorkStatus.fromCode("half_done"))
        assertEquals(OrderWorkStatus.DONE, OrderWorkStatus.fromCode("done"))
    }

    @Test
    fun orderWorkStatus_fromCode_unknown_returnsInProgress() {
        assertEquals(OrderWorkStatus.IN_PROGRESS, OrderWorkStatus.fromCode("cancelled"))
        assertEquals(OrderWorkStatus.IN_PROGRESS, OrderWorkStatus.fromCode(null))
    }

    @Test
    fun orderWorkStatus_codesAreUnique() {
        val codes = OrderWorkStatus.values().map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }
}