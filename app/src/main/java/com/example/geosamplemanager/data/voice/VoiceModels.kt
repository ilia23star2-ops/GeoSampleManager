package com.example.geosamplemanager.data.voice

sealed class VoiceStatus {
    data object Idle : VoiceStatus()
    data object Listening : VoiceStatus()
    data class Heard(val text: String) : VoiceStatus()
    data object Searching : VoiceStatus()
    data class Found(val display: String) : VoiceStatus()
    data class Marked(val sampleNumber: String) : VoiceStatus()
    data class Error(val message: String) : VoiceStatus()
    data object Paused : VoiceStatus()
}

sealed class VoiceExecResult {
    data class FoundOne(
        val query: String,
        val orderTitle: String,
        val wellNumber: String,
        val totalSamples: Int,
        val foundSamples: Int,
        val isSample: Boolean,
        val blanks: Int = 0,
        val weightControls: Int = 0,
        val postponed: Int = 0,
        val attentionReason: AnswerReason? = null,
        val otherAreaTitle: String? = null,
        val otherOrderNumber: String? = null,
        /**
         * FIX 5.8.9f-2a-fix-6: true — ответ в режиме сортировки.
         * VoiceDialog не выводит статистику (всего проб / отмечено /
         * холостых / ВК / отложено) — только скважину и наряд.
         */
        val isSortMode: Boolean = false,
        /**
         * FIX 5.8.11-e4e-bundle/2: структура ввода для мимикрии озвучки.
         * Если заполнено — VoiceDialog использует VoiceSpeaker.spellOut(groups)
         * вместо spellOut(query). Это даёт «капэдэ сто девять ноль ноль
         * тридцать один» вместо «ка пэ дэ 10 90 03 1».
         * Пустой список — старый путь (для обратной совместимости).
         */
        val groups: List<DigitGroup> = emptyList()
    ) : VoiceExecResult()

    data class FoundMany(val query: String, val variants: Int) : VoiceExecResult()

    data class Marked(
        val sampleNumber: String,
        val ordinal: Int,
        val isWeightControl: Boolean,
        val needsWeight: Boolean
    ) : VoiceExecResult()

    data class MarkedMultiple(val sampleNumbers: List<String>) : VoiceExecResult()

    data class MarkedAll(val count: Int) : VoiceExecResult()

    data class WeightSet(val sampleNumber: String, val weight: Double) : VoiceExecResult()
    data class Unmarked(val sampleNumber: String) : VoiceExecResult()
    data class Message(val text: String) : VoiceExecResult()
    data class ModeChanged(val mode: VoiceSessionMode) : VoiceExecResult()

    data object Next : VoiceExecResult()
    data object Undone : VoiceExecResult()
    data object Redone : VoiceExecResult()
    data object Stopped : VoiceExecResult()
    data object NotFound : VoiceExecResult()
}