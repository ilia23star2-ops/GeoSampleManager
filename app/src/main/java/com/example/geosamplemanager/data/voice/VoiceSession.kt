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
 * Контекст закрепления скважины.
 */
data class PinnedScope(
    val orderId: Long,
    val orderTitle: String,
    val areaTitle: String,
    val wellNumber: String
)

/**
 * Тип маркера намерения — какого действия ждём от пользователя.
 */
enum class PendingMarkIntentType {
    MARK,
    CLEAR,
    POSTPONE
}

/**
 * Контекст ожидания номера пробы после маркера намерения.
 */
data class PendingMarkIntent(
    val type: PendingMarkIntentType,
    val startedAt: Long = System.currentTimeMillis()
)

/**
 * Контекст ожидания подтверждения массового действия.
 */
data class PendingConfirm(
    val action: ConfirmedAction,
    val count: Int,
    val startedAt: Long = System.currentTimeMillis()
)

/**
 * Виды массовых действий, требующих подтверждения.
 */
enum class ConfirmedAction {
    MARK_ALL,
    CLEAR_ALL
}

/**
 * Контекст голосовой сессии.
 *
 * FIX 5.8.11-e4-weight-queue:
 * - поля `weightQueue`, `weightQueueIndex`, `weightQueueStartedAt`
 *   — очередь веса после массовой отметки;
 * - методы `startWeightQueue`, `currentWeightItem`,
 *   `advanceWeightQueue`, `clearWeightQueue`, `hasWeightQueue`.
 *
 * FIX 5.8.11-sort-fix:
 * - `advanceToNext()` больше НЕ сбрасывает `mode` в SEARCH.
 *   Режим ортогонален состоянию: если в SORT сказать «далее»,
 *   пользователь остаётся в SORT.
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
    var pendingMarkIntent: PendingMarkIntent? by mutableStateOf<PendingMarkIntent?>(null)
    var pendingConfirm: PendingConfirm? by mutableStateOf<PendingConfirm?>(null)

    /**
     * FIX 5.8.11-e4-weight-queue:
     * Очередь веса. Появляется после «отметь все» + подтверждения,
     * если остались пробы, которым нужен вес.
     */
    var weightQueue: List<WeightQueueItem> by mutableStateOf(emptyList())

    /**
     * FIX 5.8.11-e4-weight-queue:
     * Индекс текущей пробы в очереди (0-based).
     */
    var weightQueueIndex: Int by mutableStateOf(0)

    /**
     * FIX 5.8.11-e4-weight-queue:
     * Время старта вопроса по текущей пробе — для тайм-аута.
     */
    var weightQueueStartedAt: Long by mutableStateOf(0L)

    /**
     * FIX 5.8.11-e4-weight-queue:
     * Сколько уже отмечено / пропущено в текущей очереди.
     */
    var weightQueueMarked: Int by mutableStateOf(0)
    var weightQueueSkipped: Int by mutableStateOf(0)

    /**
     * Текущее закрепление скважины.
     */
    var pinned: PinnedScope? by mutableStateOf<PinnedScope?>(null)

    /**
     * Очередь скважин мультизапроса.
     */
    var queue: List<PinnedScope> by mutableStateOf(emptyList())

    private var pausedBeforePending: Boolean = false

    /**
     * Вычисляемое состояние.
     *
     * FIX 5.8.11-e4-weight-queue:
     * AWAITING_WEIGHT_QUEUE идёт сразу после pendingConfirm.
     */
    val state: VoiceState
        get() = when {
            weightQueue.isNotEmpty() -> VoiceState.AWAITING_WEIGHT_QUEUE
            pendingConfirm != null -> VoiceState.AWAITING_CONFIRM

            pendingMarkIntent != null -> when (pendingMarkIntent!!.type) {
                PendingMarkIntentType.MARK -> VoiceState.AWAITING_MARK
                PendingMarkIntentType.CLEAR -> VoiceState.AWAITING_CLEAR
                PendingMarkIntentType.POSTPONE -> VoiceState.AWAITING_POSTPONE
            }

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

    val hasQueue: Boolean
        get() = queue.isNotEmpty()

    // ================================================================
    // Pin / queue
    // ================================================================

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

    fun unpin() {
        pinned = null
    }

    fun enqueue(scopes: List<PinnedScope>) {
        if (scopes.isEmpty()) {
            queue = emptyList()
            return
        }
        pinned = scopes.first()
        queue = scopes.drop(1)
    }

    fun nextInQueue(): PinnedScope? {
        if (queue.isEmpty()) return null
        val next = queue.first()
        queue = queue.drop(1)
        pinned = next
        return next
    }

    fun clearQueue() {
        queue = emptyList()
    }

    // ================================================================
    // FIX 5.8.11-e4-weight-queue: очередь веса
    // ================================================================

    val hasWeightQueue: Boolean
        get() = weightQueue.isNotEmpty()

    val currentWeightItem: WeightQueueItem?
        get() = weightQueue.getOrNull(weightQueueIndex)

    val weightQueueTotal: Int
        get() = weightQueue.size

    val weightQueuePosition: Int
        get() = weightQueueIndex + 1

    /**
     * Запустить очередь веса.
     * Сбрасывает счётчики, ставит таймер.
     */
    fun startWeightQueue(items: List<WeightQueueItem>) {
        weightQueue = items
        weightQueueIndex = 0
        weightQueueMarked = 0
        weightQueueSkipped = 0
        weightQueueStartedAt = System.currentTimeMillis()
    }

    /**
     * Перейти к следующей пробе. Возвращает true, если ещё есть.
     */
    fun advanceWeightQueue(): Boolean {
        val next = weightQueueIndex + 1
        if (next >= weightQueue.size) {
            return false
        }
        weightQueueIndex = next
        weightQueueStartedAt = System.currentTimeMillis()
        return true
    }

    /**
     * Обновить таймер текущей пробы (после предупреждения).
     */
    fun touchWeightQueueTimer() {
        weightQueueStartedAt = System.currentTimeMillis()
    }

    /**
     * Очистить очередь полностью.
     */
    fun clearWeightQueue() {
        weightQueue = emptyList()
        weightQueueIndex = 0
        weightQueueMarked = 0
        weightQueueSkipped = 0
        weightQueueStartedAt = 0L
    }

    // ================================================================
    // PendingMarkChoice
    // ================================================================

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
        if (pendingMarkChoice == null) return
        pendingMarkChoice = null
        isPaused = pausedBeforePending
        pausedBeforePending = false
    }

    // ================================================================
    // Маркеры намерения
    // ================================================================

    fun startMarkIntent(type: PendingMarkIntentType) {
        pendingMarkIntent = PendingMarkIntent(type = type)
    }

    fun clearMarkIntent() {
        pendingMarkIntent = null
    }

    // ================================================================
    // Подтверждение массовых
    // ================================================================

    fun startPendingConfirm(action: ConfirmedAction, count: Int) {
        pendingConfirm = PendingConfirm(action = action, count = count)
    }

    fun clearPendingConfirm() {
        pendingConfirm = null
    }

    // ================================================================

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
        pendingMarkIntent = null
        pendingConfirm = null
        pausedBeforePending = false
        pinned = null
        queue = emptyList()
        clearWeightQueue()
    }

    /**
     * FIX 5.8.11-sort-fix:
     * Раньше здесь был `mode = VoiceSessionMode.SEARCH`. Это ломало
     * SORT: пользователь говорил «далее» и незаметно возвращался
     * в SEARCH. Режим ортогонален состоянию — не трогаем.
     */
    fun advanceToNext() {
        currentQuery = null
        currentWellNumber = null
        currentSampleNumber = null
        currentSampleOrdinal = null
        lastMarkedRowId = null
        lastMarkedSampleNumber = null
        isAutoMode = false
        awaitingWeight = false
        awaitingContinue = false
        pendingMarkChoice = null
        pendingMarkIntent = null
        pendingConfirm = null
        pausedBeforePending = false
        isPaused = false
        pinned = null
        queue = emptyList()
        clearWeightQueue()
    }
}