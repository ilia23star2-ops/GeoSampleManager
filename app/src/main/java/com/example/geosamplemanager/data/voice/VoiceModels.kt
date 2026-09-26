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

enum class WeightQueueKind {
    BLANK,
    WEIGHT_CONTROL
}

data class WeightQueueItem(
    val sampleNumber: String,
    val ordinal: Int,
    val kind: WeightQueueKind
)

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

    data class MarkOrdinalNotFound(
        val ordinal: Int,
        val hintOrdinal: Int?
    ) : VoiceExecResult()

    data class MarkedMultiple(val sampleNumbers: List<String>) : VoiceExecResult()

    data class MarkedAll(val count: Int) : VoiceExecResult()

    data class WeightQueueAsked(
        val item: WeightQueueItem,
        val index: Int,
        val total: Int,
        val marked: Int = 0,
        val skipped: Int = 0
    ) : VoiceExecResult()

    data class WeightQueueDone(
        val marked: Int,
        val skipped: Int
    ) : VoiceExecResult()

    data class WeightSet(val sampleNumber: String, val weight: Double) : VoiceExecResult()
    data class Unmarked(val sampleNumber: String) : VoiceExecResult()

    /**
     * FIX 5.8.11-sort-fix-4:
     * Поле display — канонический номер для UI-поля «Распознано»
     * (в SORT-режиме). spoken — озвучка для TTS. text — fallback для обоих.
     */
    data class Message(
        val text: String,
        val spoken: String? = null,
        val display: String? = null
    ) : VoiceExecResult()

    data class ModeChanged(val mode: VoiceSessionMode) : VoiceExecResult()

    data object Next : VoiceExecResult()
    data object Undone : VoiceExecResult()
    data object Redone : VoiceExecResult()
    data object Stopped : VoiceExecResult()
    data object NotFound : VoiceExecResult()
}