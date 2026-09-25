package com.example.geosamplemanager.data.voice

/**
 * FIX 5.8.11-a (SEARCH_MODEL §3):
 * Состояние голосового помощника — что он принимает прямо сейчас.
 *
 * Ортогонально [VoiceSessionMode] (SEARCH / SORT): режим может быть
 * любым при любом состоянии.
 *
 * FIX 5.8.11-e4-pin-1:
 * Добавлено FOUND_PINNED — скважина закреплена после поиска.
 * В этом состоянии голое число 1..99 идёт в MarkOrdinal, не в Search.
 *
 * FIX 5.8.11-e4-markers:
 * Добавлены маркеры намерения (AWAITING_MARK, AWAITING_CLEAR,
 * AWAITING_POSTPONE) и подтверждение массовых (AWAITING_CONFIRM).
 *
 * FIX 5.8.11-e4-weight-queue:
 * Добавлено AWAITING_WEIGHT_QUEUE — ГП после «отметь все» отметил
 * обычные пробы и по очереди запрашивает вес для холостых/ВК.
 *
 * Источник правды — флаги в VoiceSession:
 *   pendingWeightQueue != null  → AWAITING_WEIGHT_QUEUE
 *   pendingConfirm != null      → AWAITING_CONFIRM
 *   pendingMarkIntent != null   → AWAITING_MARK / CLEAR / POSTPONE
 *   pendingMarkChoice != null   → AWAITING_CHOICE
 *   isPaused                    → PAUSED
 *   awaitingWeight              → AWAITING_WEIGHT
 *   awaitingContinue            → AWAITING_CONTINUE
 *   pinned != null              → FOUND_PINNED
 *   иначе                       → LISTENING
 */
enum class VoiceState {

    /** ГП не запущен. */
    IDLE,

    /** Слушает. Полный словарь. */
    LISTENING,

    /**
     * Скважина найдена и закреплена.
     * Голое число → отметка в этой скважине.
     */
    FOUND_PINNED,

    /** Ждёт вес. */
    AWAITING_WEIGHT,

    /**
     * FIX 5.8.11-e4-weight-queue:
     * Очередь веса после массовой отметки.
     * ГП спросил «Холостая, 5-я. Вес?» и ждёт число.
     */
    AWAITING_WEIGHT_QUEUE,

    /** «Найден в нескольких нарядах», ждёт продолжения. */
    AWAITING_CONTINUE,

    /** Ждёт выбор: снять / отложить / пропустить. */
    AWAITING_CHOICE,

    /** Ждёт номер пробы для отметки. */
    AWAITING_MARK,

    /** Ждёт номер пробы для снятия. */
    AWAITING_CLEAR,

    /** Ждёт номер пробы для отложения. */
    AWAITING_POSTPONE,

    /** Ждёт подтверждения массового действия. */
    AWAITING_CONFIRM,

    /** Пауза. */
    PAUSED;

    val isAwaiting: Boolean
        get() = this == AWAITING_WEIGHT
                || this == AWAITING_WEIGHT_QUEUE
                || this == AWAITING_CONTINUE
                || this == AWAITING_CHOICE
                || this == AWAITING_MARK
                || this == AWAITING_CLEAR
                || this == AWAITING_POSTPONE
                || this == AWAITING_CONFIRM

    val isActive: Boolean
        get() = this != IDLE

    val showsPanel: Boolean
        get() = this != IDLE

    /**
     * FIX 5.8.11-e4-markers:
     * Требует ли это состояние тайм-аута ожидания.
     *
     * FIX 5.8.11-e4-weight-queue:
     * AWAITING_WEIGHT_QUEUE — тоже ждёт ответа, тайм-аут работает.
     */
    val hasWaitTimeout: Boolean
        get() = this == AWAITING_WEIGHT
                || this == AWAITING_WEIGHT_QUEUE
                || this == AWAITING_CHOICE
                || this == AWAITING_MARK
                || this == AWAITING_CLEAR
                || this == AWAITING_POSTPONE
                || this == AWAITING_CONFIRM
}
