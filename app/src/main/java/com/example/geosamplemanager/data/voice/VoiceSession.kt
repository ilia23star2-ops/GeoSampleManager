package com.example.geosamplemanager.data.voice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Режим голосовой сессии.
 *
 *   SEARCH — поиск со статистикой и отметками (по умолчанию).
 *   SORT   — сортировка: только «X — наряд Y», без отметок.
 *
 * FIX 5.8.11-a (SEARCH_MODEL §3):
 * Режим ортогонален состоянию (VoiceState). Может быть SEARCH при
 * любом состоянии и SORT при любом. Переключение режима не меняет
 * состояние. Отметки блокируются только в SORT.
 */
enum class VoiceSessionMode {
    SEARCH,
    SORT
}

/**
 * FIX 5.8.9d-3c2b1: тип ожидаемого выбора для проблемной пробы.
 *
 * ALREADY_FOUND — проба уже отмечена.
 * POSTPONED     — проба отложена.
 */
enum class PendingMarkChoiceType {
    ALREADY_FOUND,
    POSTPONED
}

/**
 * FIX 5.8.9d-3c2b1: контекст ожидаемого выбора.
 */
data class PendingMarkChoice(
    val type: PendingMarkChoiceType,
    val ordinal: Int,
    val sampleNumber: String
)

/**
 * Контекст голосовой сессии.
 *
 * FIX 5.8.11-a (SEARCH_MODEL §3):
 * Добавлено вычисляемое свойство `state: VoiceState`. Источник правды —
 * существующие флаги (`isPaused`, `awaitingWeight`, `awaitingContinue`,
 * `pendingMarkChoice`). Enum VoiceState добавлен как read-only view —
 * парсер и презентер смогут читать состояние одним полем.
 *
 * Порядок приоритетов (сверху вниз):
 *   1. pendingMarkChoice != null → AWAITING_CHOICE
 *   2. isPaused                  → PAUSED
 *   3. awaitingWeight            → AWAITING_WEIGHT
 *   4. awaitingContinue          → AWAITING_CONTINUE
 *   5. иначе                     → LISTENING
 *
 * FIX 5.8.9f-2b: поле `mode` — Compose mutableStateOf.
 *
 * FIX 5.8.9d-2a: currentSampleNumber / currentSampleOrdinal.
 *
 * FIX 5.8.9d-3c2b1: pendingMarkChoice.
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

    /**
     * Режим. По умолчанию — SEARCH.
     * Ортогонален состоянию [state].
     */
    var mode: VoiceSessionMode by mutableStateOf(VoiceSessionMode.SEARCH)

    /**
     * true — ГП только что спросил «Вес?» и ждёт ответа.
     */
    var awaitingWeight: Boolean = false

    /**
     * true — ГП только что ответил «Найден в нескольких нарядах».
     */
    var awaitingContinue: Boolean = false

    /**
     * FIX 5.8.9d-3c2b1:
     * true — ГП ждёт выбор действия для уже отмеченной / отложенной пробы.
     */
    var pendingMarkChoice: PendingMarkChoice? by mutableStateOf<PendingMarkChoice?>(null)

    /**
     * Было ли сессионное ожидание выбора запущено поверх ручной паузы.
     */
    private var pausedBeforePending: Boolean = false

    // ================================================================
    // FIX 5.8.11-a: состояние ГП
    // ================================================================

    /**
     * Вычисляемое состояние. Источник правды — существующие флаги.
     *
     * Порядок важен: AWAITING_CHOICE перекрывает PAUSED, потому что
     * `startPendingMarkChoice` сам ставит `isPaused = true` (см. ниже).
     * Если проверять `isPaused` первым, мы потеряем состояние выбора.
     */
    val state: VoiceState
        get() = when {
            pendingMarkChoice != null -> VoiceState.AWAITING_CHOICE
            isPaused -> VoiceState.PAUSED
            awaitingWeight -> VoiceState.AWAITING_WEIGHT
            awaitingContinue -> VoiceState.AWAITING_CONTINUE
            else -> VoiceState.LISTENING
        }

    // ================================================================

    val hasContext: Boolean
        get() = currentOrderId != null && currentQuery != null

    /**
     * FIX 5.8.9d-3c2b1:
     * Запустить ожидание выбора и автоматически поставить сессию на паузу.
     *
     * Пауза нужна, чтобы ГП не продолжал слушать фоновые команды,
     * пока пользователь не ответил на уточняющий вопрос.
     */
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

    /**
     * FIX 5.8.9d-3c2b1:
     * Очистить ожидание выбора и восстановить прежний флаг паузы.
     */
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
    }
}
