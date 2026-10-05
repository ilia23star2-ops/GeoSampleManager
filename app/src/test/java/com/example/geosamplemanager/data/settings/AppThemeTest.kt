package com.example.geosamplemanager.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.9-settings-theme:
 * Тесты на enum AppTheme — чистые функции.
 */
class AppThemeTest {

    @Test
    fun fromName_system_returnsSystem() {
        assertEquals(AppTheme.SYSTEM, AppTheme.fromName("SYSTEM"))
    }

    @Test
    fun fromName_light_returnsLight() {
        assertEquals(AppTheme.LIGHT, AppTheme.fromName("LIGHT"))
    }

    @Test
    fun fromName_dark_returnsDark() {
        assertEquals(AppTheme.DARK, AppTheme.fromName("DARK"))
    }

    @Test
    fun fromName_unknown_returnsSystem() {
        assertEquals(AppTheme.SYSTEM, AppTheme.fromName("UNKNOWN"))
        assertEquals(AppTheme.SYSTEM, AppTheme.fromName(null))
        assertEquals(AppTheme.SYSTEM, AppTheme.fromName(""))
    }

    @Test
    fun everyThemeHasNonEmptyTitle() {
        AppTheme.values().forEach { t ->
            assert(t.title.isNotBlank()) { "AppTheme.${t.name} без названия" }
        }
    }

    @Test
    fun defaultAppearance_isSystem() {
        assertEquals(AppTheme.SYSTEM, AppearanceSettings().theme)
    }

    @Test
    fun appearanceCopyTheme_works() {
        val a = AppearanceSettings().copy(theme = AppTheme.DARK)
        assertEquals(AppTheme.DARK, a.theme)
        assertEquals(UiScale.NORMAL, a.scale)
    }
}