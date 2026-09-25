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
 */
data class PinnedScope(
    val orderId: Long,
    val orderTitle: String,
    val areaTitle: String,
    val wellNumber: String
)

/**
 * FIX 5.8.11-e4-markers:
 * Тип маркера намерения — какого действия ждём от пользователя.
 *
 * MARK     — «отметь» → ждём номер пробы для отметки.
 * CLEAR    — «снять» → ждём номер для снятия.
 * POSTPONE — «отложить» → ждём номер для отложения.
 */
enum class PendingMarkIntentType {
    MARK,
    CLEAR,
    POSTPONE
}

/**
 * FIX 5.8.11-e4-markers:
 * Контекст ожидания номера пробы после маркера намерения.
 *
 * @param type       какое действие выполним
 * @param startedAt  время старта ожидания (для тайм-аута)
 */
data class PendingMarkIntent(
    val type: PendingMarkIntentType,
    val startedAt: Long = System.currentTimeMillis()
)

/**
 * FIX 5.8.11-e4-markers:
 * Контекст ожидания подтверждения массового действия.
 *
 * @param action     какое действие подтверждаем
 * @param count      сколько проб затронет (для озвучки)
 * @param startedAt  время старта ожидания (для тайм-аута)
 */
data class PendingConfirm(
    val action: ConfirmedAction,
    val count: Int,
    val startedAt: Long = System.currentTimeMillis()
)

/**
 * FIX 5.8.11-e4-markers:
 * Виды массовых действий, требующих подтверждения.
 *
 * DELETE намеренно отсутствует: удаление голосом не делаем — слишком
 * опасно. Только UI с чек-боксом «Пересчитать №».
 */
enum class ConfirmedAction {
    MARK_ALL,
    CLEAR_ALL
}

/**
 * Контекст голосовой сессии.
 *
 * FIX 5.8.11-e4-pin-2:
 * - поле `queue` — очередь скважин мультизапроса.
 *
 * FIX 5.8.11-e4-markers:
 * - поле `pendingMarkIntent` — маркер намерения (MARK/CLEAR/POSTPONE);
 * - поле `pendingConfirm` — ожидание подтверждения массового действия.
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
     * FIX 5.8.11-e4-markers:
     * Ожидание номера пробы после маркера намерения.
     */
    var pendingMarkIntent: PendingMarkIntent? by mutableStateOf<PendingMarkIntent?>(null)

    /**
     * FIX 5.8.11-e4-markers:
     * Ожидание подтверждения массового действия.
     */
    var pendingConfirm: PendingConfirm? by mutableStateOf<PendingConfirm?>(null)

    /**
     * Текущее закрепление скважины.
     */
    var pinned: PinnedScope? by mutableStateOf<PinnedScope?>(null)

    /**
     * Очередь скважин мультизапроса.
     * Первая всегда pinned, остальные здесь.
     */
    var queue: List<PinnedScope> by mutableStateOf(emptyList())

    private var pausedBeforePending: Boolean = false

    /**
     * Вычисляемое состояние. Источник правды — флаги.
     *
     * Порядок важен (сверху вниз приоритет):
     *   1. pendingConfirm    → AWAITING_CONFIRM
     *   2. pendingMarkIntent → AWAITING_MARK/CLEAR/POSTPONE
     *   3. pendingMarkChoice → AWAITING_CHOICE
     *   4. isPaused          → PAUSED
     *   5. awaitingWeight    → AWAITING_WEIGHT
     *   6. awaitingContinue  → AWAITING_CONTINUE
     *   7. pinned != null    → FOUND_PINNED
     *   8. иначе             → LISTENING
     */
    val state: VoiceState
        get() = when {
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
    // PendingMarkChoice (старое)
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
    // FIX 5.8.11-e4-markers: маркеры намерения
    // ================================================================

    /**
     * Запустить маркер намерения. ГП переходит в AWAITING_MARK /
     * AWAITING_CLEAR / AWAITING_POSTPONE и ждёт номер пробы.
     */
    fun startMarkIntent(type: PendingMarkIntentType) {
        pendingMarkIntent = PendingMarkIntent(type = type)
    }

    /**
     * Сбросить маркер намерения (после выполнения или отмены).
     */
    fun clearMarkIntent() {
        pendingMarkIntent = null
    }

    // ================================================================
    // FIX 5.8.11-e4-markers: подтверждение массовых
    // ================================================================

    /**
     * Запустить ожидание подтверждения. ГП переходит в AWAITING_CONFIRM.
     */
    fun startPendingConfirm(action: ConfirmedAction, count: Int) {
        pendingConfirm = PendingConfirm(action = action, count = count)
    }

    /**
     * Сбросить ожидание подтверждения.
     */
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
        pendingMarkIntent = null
        pendingConfirm = null
        pausedBeforePending = false
        isPaused = false
        pinned = null
        queue = emptyList()
    }
}
