package com.example.geosamplemanager.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-settings-help-1:
 * Тесты на enum HelpTopic — чистые функции, без Android.
 */
class HelpTopicTest {

    @Test
    fun fromName_start_returnsStart() {
        assertEquals(HelpTopic.START, HelpTopic.fromName("START"))
    }

    @Test
    fun fromName_import_returnsImport() {
        assertEquals(HelpTopic.IMPORT, HelpTopic.fromName("IMPORT"))
    }

    @Test
    fun fromName_voice_returnsVoice() {
        assertEquals(HelpTopic.VOICE, HelpTopic.fromName("VOICE"))
    }

    @Test
    fun fromName_unknown_returnsStart() {
        assertEquals(HelpTopic.START, HelpTopic.fromName("UNKNOWN"))
        assertEquals(HelpTopic.START, HelpTopic.fromName(null))
        assertEquals(HelpTopic.START, HelpTopic.fromName(""))
    }

    @Test
    fun everyTopicHasNonEmptyTitle() {
        HelpTopic.values().forEach { t ->
            assertTrue(
                "HelpTopic.${t.name} без названия",
                t.title.isNotBlank()
            )
        }
    }

    @Test
    fun topicsCount_isEight() {
        // 8 разделов: START, IMPORT, SEARCH, VOICE, EDIT, DB, STATS, SETTINGS.
        assertEquals(8, HelpTopic.values().size)
    }
}