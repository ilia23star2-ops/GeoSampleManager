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

/**
 * FIX 5.8.11-e4-weight-queue:
 * Тип пробы в очереди веса.
 *
 * BLANK          — холостая (status == blank), нужен weight.
 * WEIGHT_CONTROL — весовая (weightControl), нужен controlWeight.
 */
enum class WeightQueueKind {
    BLANK,
    WEIGHT_CONTROL
}

/**
 * FIX 5.8.11-e4-weight-queue:
 * Одна проба в очереди веса.
 *
 * @param sampleNumber номер пробы (полный, как в БД).
 * @param ordinal      порядковый номер в скважине.
 * @param kind         тип (холостая / ВК) — для озвучки.
 */
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

    /**
     * FIX 5.8.11-e4-pin-7:
     * В FOUND_PINNED распознали номер пробы, но такой пробы в скважине нет.
     */
    data class MarkOrdinalNotFound(
        val ordinal: Int,
        val hintOrdinal: Int?
    ) : VoiceExecResult()

    data class MarkedMultiple(val sampleNumbers: List<String>) : VoiceExecResult()

    data class MarkedAll(val count: Int) : VoiceExecResult()

    /**
     * FIX 5.8.11-e4-weight-queue:
     * После «отметь все» — часть отмечена, для остальных нужен вес.
     * ГП задаёт вопрос по одной пробе из очереди.
     *
     * @param item     текущая проба (номер, порядковый, тип).
     * @param index    1-based позиция в очереди.
     * @param total    всего в очереди.
     * @param marked   сколько уже отмечено (включая текущую, если она
     *                 отмечена частично — не считаем, пока не получим вес).
     * @param skipped  сколько уже пропущено.
     */
    data class WeightQueueAsked(
        val item: WeightQueueItem,
        val index: Int,
        val total: Int,
        val marked: Int = 0,
        val skipped: Int = 0
    ) : VoiceExecResult()

    /**
     * FIX 5.8.11-e4-weight-queue:
     * Очередь веса завершена (все отметили или пропустили).
     */
    data class WeightQueueDone(
        val marked: Int,
        val skipped: Int
    ) : VoiceExecResult()

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
