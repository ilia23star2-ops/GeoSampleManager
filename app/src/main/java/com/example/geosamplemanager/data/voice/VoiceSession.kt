package com.example.geosamplemanager.data.voice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Режим голосовой сессии.
 */
enum class VoiceSessionMode {
    SEARCH,
    SORT
}

/**
 * Тип ожидаемого выбора для проблемной пробы.
 */
enum class PendingMarkChoiceType {
    ALREADY_FOUND,
    POSTPONED
}

/**
 * Контекст ожидаемого выбора.
 */
data class PendingMarkChoice(
    val type: PendingMarkChoiceType,
    val ordinal: Int,
    val sampleNumber: String
)

/**
 * FIX 5.8.11-e4-pin-1:
 * Контекст закрепления скважины.
 *
 * После успешного одиночного поиска скважины она «закрепляется» —
 * ГП продолжает работать именно с ней. Голое число идёт в отметку,
 * а не в поиск новой скважины.
 *
 * Выход из закрепления:
 *   - «следующая»;
 *   - «отмена»;
 *   - ручной ввод в строке поиска UI;
 *   - новый успешный поиск (перезаписывает).
 */
data class PinnedScope(
    val orderId: Long,
    val orderTitle: String,
    val areaTitle: String,
    val wellNumber: String
)

/**
 * Контекст голосовой сессии.
 *
 * FIX 5.8.11-e4-pin-1:
 * - поле `pinned` — текущее закрепление;
 * - методы pin / unpin / isPinned;
 * - `state` → FOUND_PINNED при активном закреплении.
 */
class VoiceSession {

    var currentOrderTitle: String? = null
    var currentOrderId: Long? = null
    var currentAreaTitle: String? = null
    var currentQuery: String? = null
    var currentWellNumber: String? = null

    var currentSampleNumber: String? = null
    var currentSampleOrdinal: Int? = null

    var lastMarkedRowId: String? = null
    var lastMarkedSampleNumber: String? = null
    var isAutoMode: Boolean = false
    var isPaused: Boolean = false

    var mode: VoiceSessionMode by mutableStateOf(VoiceSessionMode.SEARCH)

    var awaitingWeight: Boolean = false
    var awaitingContinue: Boolean = false

    var pendingMarkChoice: PendingMarkChoice? by mutableStateOf<PendingMarkChoice?>(null)

    /**
     * FIX 5.8.11-e4-pin-1: текущее закрепление скважины.
     * null — закрепления нет.
     */
    var pinned: PinnedScope? by mutableStateOf<PinnedScope?>(null)

    private var pausedBeforePending: Boolean = false

    val state: VoiceState
        get() = when {
            pendingMarkChoice != null -> VoiceState.AWAITING_CHOICE
            isPaused -> VoiceState.PAUSED
            awaitingWeight -> VoiceState.AWAITING_WEIGHT
            awaitingContinue -> VoiceState.AWAITING_CONTINUE
            pinned != null -> VoiceState.FOUND_PINNED
            else -> VoiceState.LISTENING
        }

    val hasContext: Boolean
        get() = currentOrderId != null && currentQuery != null

    val isPinned: Boolean
        get() = pinned != null

    /**
     * FIX 5.8.11-e4-pin-1:
     * Закрепить скважину. Перезаписывает текущее закрепление.
     */
    fun pin(
        orderId: Long,
        orderTitle: String,
        areaTitle: String,
        wellNumber: String
    ) {
        pinned = PinnedScope(
            orderId = orderId,
            orderTitle = orderTitle,
            areaTitle = areaTitle,
            wellNumber = wellNumber
        )
    }

    /**
     * FIX 5.8.11-e4-pin-1:
     * Снять закрепление.
     */
    fun unpin() {
        pinned = null
    }

    fun startPendingMarkChoice(
        type: PendingMarkChoiceType,
        ordinal: Int,
        sampleNumber: String
    ) {
        if (pendingMarkChoice == null) {
            pausedBeforePending = isPaused
        }

        pendingMarkChoice = PendingMarkChoice(
            type = type,
            ordinal = ordinal,
            sampleNumber = sampleNumber
        )

        awaitingWeight = false
        awaitingContinue = false
        isPaused = true
    }

    fun clearPendingMarkChoice() {
        if (pendingMarkChoice == null) {
            return
        }

        pendingMarkChoice = null
        isPaused = pausedBeforePending
        pausedBeforePending = false
    }

    fun clear() {
        currentOrderTitle = null
        currentOrderId = null
        currentAreaTitle = null
        currentQuery = null
        currentWellNumber = null
        currentSampleNumber = null
        currentSampleOrdinal = null
        lastMarkedRowId = null
        lastMarkedSampleNumber = null
        isAutoMode = false
        isPaused = false
        mode = VoiceSessionMode.SEARCH
        awaitingWeight = false
        awaitingContinue = false
        pendingMarkChoice = null
        pausedBeforePending = false
        pinned = null
    }

    fun advanceToNext() {
        currentQuery = null
        currentWellNumber = null
        currentSampleNumber = null
        currentSampleOrdinal = null
        lastMarkedRowId = null
        lastMarkedSampleNumber = null
        isAutoMode = false
        mode = VoiceSessionMode.SEARCH
        awaitingWeight = false
        awaitingContinue = false
        pendingMarkChoice = null
        pausedBeforePending = false
        isPaused = false
        pinned = null
    }
}