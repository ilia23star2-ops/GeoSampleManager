package com.example.geosamplemanager.data.voice

import androidx.compose.ui.graphics.Color
import com.example.geosamplemanager.ui.screens.AnswerStateColors

/**
 * FIX 5.8.11-d2 (SEARCH_MODEL §8):
 * Единая точка рендера Response.
 *
 * Один Response → один RenderedResponse:
 *   - visual — что показать в UI;
 *   - spoken — что произнести через TTS;
 *   - sound  — какой звук сыграть.
 *
 * UI и ГП получают один и тот же ответ. Формулировки совпадают
 * гарантированно. Б3 закрыт.
 */
object Presenter {

    /**
     * Финальный рендер. Заменяет два места:
     *   - describeResult()  (UI, было в VoiceDialog)
     *   - buildFoundOnePhrase() и др. (TTS, было в VoiceDialog)
     *
     * Правила:
     *   1. primary — всегда первым.
     *   2. details — через точку.
     *   3. Если pause == true — добавляется вопрос.
     *   4. Если kind == UNKNOWN и state == LISTENING — пустой ответ
     *      (молчание). Это решение зафиксировано в SEARCH_MODEL §8.5.
     */
    fun render(
        response: Response,
        state: VoiceState
    ): RenderedResponse {
        // Молчание при Unknown в LISTENING.
        if (response.kind == ResponseKind.UNKNOWN && state == VoiceState.LISTENING) {
            return RenderedResponse(
                visual = VisualRender(
                    state = androidx.compose.ui.graphics.Color.Transparent.let { _ ->
                        com.example.geosamplemanager.data.voice.AnswerState.IDLE
                    },
                    title = "",
                    shortStatus = "",
                    reasonLabel = "",
                    indicatorColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                spoken = "",
                sound = SoundKind.NONE
            )
        }

        val visual = renderVisual(response)
        val spoken = renderSpoken(response)

        return RenderedResponse(
            visual = visual,
            spoken = spoken,
            sound = response.sound
        )
    }

    private fun renderVisual(response: Response): VisualRender {
        val state = response.reason.state
        val colors = AnswerStateColors.of(state)
        return VisualRender(
            state = state,
            title = response.primary,
            shortStatus = response.reason.shortLabel,
            reasonLabel = response.reason.detailLabel,
            indicatorColor = colors.accent
        )
    }

    /**
     * Сборка фразы для TTS.
     *
     * primary + ". " + details.joinToString(". ") + "."
     * Если pause — добавляется вопрос.
     */
    private fun renderSpoken(response: Response): String {
        if (response.kind == ResponseKind.STOPPED) return ""

        val sb = StringBuilder()

        // Основная строка.
        if (response.primary.isNotEmpty()) {
            sb.append(response.primary)
            if (!response.primary.endsWith(".")) sb.append(".")
        }

        // Детали.
        if (response.details.isNotEmpty()) {
            for (d in response.details) {
                sb.append(' ').append(d)
                if (!d.endsWith(".")) sb.append(".")
            }
        }

        // Вопрос при ожидании.
        if (response.pause) {
            val question = when (response.kind) {
                ResponseKind.ASKING_WEIGHT -> "Вес?"
                ResponseKind.ASKING_CHOICE -> "Снять, отложить или пропустить?"
                ResponseKind.ASKING_CONTINUE -> "Скажите продолжить или стоп."
                else -> null
            }
            if (question != null) {
                sb.append(' ').append(question)
            }
        }

        return sb.toString().replace(Regex(" +"), " ").trim()
    }
}

/**
 * Готовый ответ для UI и TTS.
 */
data class RenderedResponse(
    val visual: VisualRender,
    val spoken: String,
    val sound: SoundKind
)

/**
 * То, что нужно UI: заголовок, статус, причина, цвет.
 */
data class VisualRender(
    val state: AnswerState,
    val title: String,
    val shortStatus: String,
    val reasonLabel: String,
    val indicatorColor: Color
)
