package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.geosamplemanager.data.excel.ExcelAnalyzer

/**
 * Цвета для ролей колонок. Одинаково контрастны в светлой и тёмной темах.
 * Используются в редакторе маппинга и в диалоге предпросмотра.
 */
object RoleColors {

    private val LIGHT = mapOf(
        ExcelAnalyzer.Roles.SERIAL to Color(0xFFE0E0E0),
        ExcelAnalyzer.Roles.WELL to Color(0xFF90CAF9),
        ExcelAnalyzer.Roles.SAMPLE to Color(0xFFA5D6A7),
        ExcelAnalyzer.Roles.INT_FROM to Color(0xFFFFCC80),
        ExcelAnalyzer.Roles.INT_TO to Color(0xFFFFF59D),
        ExcelAnalyzer.Roles.WEIGHT to Color(0xFFCE93D8),
        ExcelAnalyzer.Roles.TYPE to Color(0xFFF48FB1),
        ExcelAnalyzer.Roles.MATERIAL to Color(0xFFBCAAA4)
    )

    private val DARK = mapOf(
        ExcelAnalyzer.Roles.SERIAL to Color(0xFF3D3D3D),
        ExcelAnalyzer.Roles.WELL to Color(0xFF1E5A8E),
        ExcelAnalyzer.Roles.SAMPLE to Color(0xFF2E7D32),
        ExcelAnalyzer.Roles.INT_FROM to Color(0xFFB26500),
        ExcelAnalyzer.Roles.INT_TO to Color(0xFF807300),
        ExcelAnalyzer.Roles.WEIGHT to Color(0xFF6A1B9A),
        ExcelAnalyzer.Roles.TYPE to Color(0xFFAD1457),
        ExcelAnalyzer.Roles.MATERIAL to Color(0xFF4E342E)
    )

    fun forRole(role: String, dark: Boolean): Color =
        (if (dark) DARK else LIGHT)[role] ?: Color(0xFFEEEEEE)

    @Composable
    fun forRole(role: String): Color = forRole(role, isSystemInDarkTheme())

    /** Возвращает чёрный или белый текст в зависимости от яркости фона. */
    fun textOn(color: Color): Color {
        val lum = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
        return if (lum > 0.6f) Color.Black else Color.White
    }

    /** Человеческое название роли. */
    fun roleTitle(role: String): String = when (role) {
        ExcelAnalyzer.Roles.SERIAL   -> "Серийный №"
        ExcelAnalyzer.Roles.WELL     -> "Скважина / Выработка"
        ExcelAnalyzer.Roles.SAMPLE   -> "Проба"
        ExcelAnalyzer.Roles.INT_FROM -> "Интервал от"
        ExcelAnalyzer.Roles.INT_TO   -> "Интервал до"
        ExcelAnalyzer.Roles.WEIGHT   -> "Вес"
        ExcelAnalyzer.Roles.TYPE     -> "Тип пробы"
        ExcelAnalyzer.Roles.MATERIAL -> "Характеристика"
        else -> role
    }

    /** Все роли в порядке отображения. */
    val ALL_ROLES = listOf(
        ExcelAnalyzer.Roles.SERIAL,
        ExcelAnalyzer.Roles.WELL,
        ExcelAnalyzer.Roles.SAMPLE,
        ExcelAnalyzer.Roles.INT_FROM,
        ExcelAnalyzer.Roles.INT_TO,
        ExcelAnalyzer.Roles.WEIGHT,
        ExcelAnalyzer.Roles.TYPE,
        ExcelAnalyzer.Roles.MATERIAL
    )
}