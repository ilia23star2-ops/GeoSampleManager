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
        /**
         * FIX 5.8.11-e4-pin-3:
         * Размер очереди мультизапроса, включая текущую скважину.
         * 0 или 1 — обычный одиночный поиск. >1 — мультизапрос,
         * VoiceDialog озвучит «Найдено N скважин» перед первым.
         */
        val queueSize: Int = 0
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