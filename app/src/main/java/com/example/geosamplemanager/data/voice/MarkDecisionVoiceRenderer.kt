package com.example.geosamplemanager.data.voice

import com.example.geosamplemanager.data.reconciliation.MarkDecision

/**
 * FIX 5.8.9d-3c1: рендер доменного решения в голосовую фразу.
 *
 * Единственное место, где тексты для ГП связаны с [MarkDecision].
 * UI-рендер диалогов — отдельно, в `ReconciliationDialogs.kt`.
 *
 * Только текст — без звука, без действий, без состояния. Звук и
 * переходы VM выбирает сама по типу решения.
 *
 * Тексты — короткие, под темп рабочего (см. VOICE.md §13.7).
 * Нормализация под русский TTS — в 5.8.9i.
 */
object MarkDecisionVoiceRenderer {

    /**
     * Построить фразу по решению.
     *
     * Если порядковое слово неизвестно (ordinal вне 1..30), fallback —
     * номер пробы через [VoiceSpeaker.spellOut].
     */
    fun render(decision: MarkDecision): String {
        val subject = subjectOf(decision)
        return when (decision) {
            is MarkDecision.CanMark ->
                "$subject отмечена."

            is MarkDecision.MarkWithWeight ->
                "$subject. Вес ${decision.weight}. Отмечена."

            is MarkDecision.AlreadyFound ->
                "$subject уже отмечена. Снять, отложить или пропустить?"

            is MarkDecision.ImportError ->
                "$subject — ошибка импорта. Проверьте на экране."

            is MarkDecision.Postponed ->
                "$subject отложена. Отметить или снять?"

            is MarkDecision.NeedsControlWeight ->
                "$subject — весовой контроль. Вес?"

            is MarkDecision.NeedsBlankWeight ->
                "$subject — холостая. Вес?"
        }
    }

    /**
     * Подлежащее фразы: «Первая» — если порядковое известно,
     * иначе «Проба эн вэ 15 26 01».
     */
    private fun subjectOf(decision: MarkDecision): String {
        val word = VoiceOrdinals.word(decision.ordinal)
        return word?.replaceFirstChar { it.uppercase() }
            ?: "Проба ${VoiceSpeaker.spellOut(decision.sampleNumber)}"
    }
}
