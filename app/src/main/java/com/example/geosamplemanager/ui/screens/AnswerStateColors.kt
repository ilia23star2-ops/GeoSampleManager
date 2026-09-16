package com.example.geosamplemanager.ui.screens

import androidx.compose.ui.graphics.Color
import com.example.geosamplemanager.data.voice.AnswerState

/**
 * Палитра состояний ответа. Единый источник цветов.
 *
 * Строгое правило: одно состояние — один цвет, без оттенков.
 * Все «внимания» (другой наряд / участок / несколько) — одного жёлтого.
 */
object AnswerStateColors {

    /**
     * Пара цветов для состояния.
     *
     *  • background — полупрозрачный фон карточки/баннера.
     *  • accent     — цвет иконки и полоски слева.
     */
    data class Colors(
        val background: Color,
        val accent: Color
    )

    // OK — зелёный.
    private val OkBg = Color(0xFFA5D6A7).copy(alpha = 0.40f)
    private val OkAccent = Color(0xFF2E7D32)

    // ATTENTION — жёлтый.
    private val AttentionBg = Color(0xFFFFE082).copy(alpha = 0.55f)
    private val AttentionAccent = Color(0xFFB8860B)

    // ERROR — красный.
    private val ErrorBg = Color(0xFFEF9A9A).copy(alpha = 0.35f)
    private val ErrorAccent = Color(0xFFC62828)

    // IDLE — серый.
    private val IdleBg = Color(0xFFE0E0E0).copy(alpha = 0.45f)
    private val IdleAccent = Color(0xFF757575)

    fun of(state: AnswerState): Colors = when (state) {
        AnswerState.OK -> Colors(OkBg, OkAccent)
        AnswerState.ATTENTION -> Colors(AttentionBg, AttentionAccent)
        AnswerState.ERROR -> Colors(ErrorBg, ErrorAccent)
        AnswerState.IDLE -> Colors(IdleBg, IdleAccent)
    }
}
