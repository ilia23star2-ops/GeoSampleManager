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
        val isSortMode: Boolean = false,
        val groups: List<DigitGroup> = emptyList(),
        val queueSize: Int = 0
    ) : VoiceExecResult()

    data class FoundMany(val query: String, val variants: Int) : VoiceExecResult()

    data class Marked(
        val sampleNumber: String,
        val ordinal: Int,
        val isWeightControl: Boolean,
        val needsWeight: Boolean
    ) : VoiceExecResult()

    /**
     * FIX 5.8.11-e4-pin-7:
     * В FOUND_PINNED распознали номер пробы, но такой пробы в скважине нет.
     *
     * Раньше (pin-5/pin-6) здесь работал fallback: 14 → 4, 40 → 4 и т.д.
     * Но Vosk путает «четвёртая» ↔ «четырнадцатая» в обе стороны, и
     * отличить намерение нельзя. Fallback молча делал предположение —
     * и иногда ошибался (юзер сказал «четырнадцатая», хотел 14, а
     * отметилась 4).
     *
     * Теперь вместо подмены — честная ошибка + подсказка:
     *   ordinal     — что распознали («14»).
     *   hintOrdinal — какое число пользователь, вероятно, имел в виду
     *                 («4»), если распознанное — «спорное» число
     *                 (4..9 × 10/100/1000). Иначе — null.
     *
     * UI и озвучка говорят: «Пробы №14 нет. Если нужна №4 — скажите
     * „четыре"». Пользователь уточняет числом — Vosk числа не путает.
     */
    data class MarkOrdinalNotFound(
        val ordinal: Int,
        val hintOrdinal: Int?
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
