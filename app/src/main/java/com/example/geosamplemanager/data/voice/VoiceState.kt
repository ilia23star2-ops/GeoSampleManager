package com.example.geosamplemanager.data.voice

/**
 * Состояние голосового помощника — что он принимает прямо сейчас.
 *
 * Ортогонально [VoiceSessionMode] (SEARCH / SORT).
 *
 * FIX 5.8.11-e4-pin-1:
 * Добавлено FOUND_PINNED — скважина закреплена.
 *
 * FIX 5.8.11-e4-markers:
 * Добавлены маркеры намерения — ГП ждёт номер пробы для конкретного
 * действия, и подтверждение массовых действий.
 *
 *   AWAITING_MARK     — «отметь» → ждём «четвёртую» / «пять шесть».
 *   AWAITING_CLEAR    — «снять» → ждём номер.
 *   AWAITING_POSTPONE — «отложить» → ждём номер.
 *   AWAITING_CONFIRM  — «отметь все» → ждём «подтверждаю» / «отменяю».
 *
 * Все четыре имеют общий тайм-аут 30 сек с предупреждением на 20-й.
 *
 * Источник правды — флаги в VoiceSession:
 *   pendingConfirm != null     → AWAITING_CONFIRM
 *   pendingMarkIntent != null  → AWAITING_MARK / CLEAR / POSTPONE
 *   pendingMarkChoice != null  → AWAITING_CHOICE
 *   isPaused                   → PAUSED
 *   awaitingWeight             → AWAITING_WEIGHT
 *   awaitingContinue           → AWAITING_CONTINUE
 *   pinned != null             → FOUND_PINNED
 *   иначе                      → LISTENING
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
     * Тайм-аут с предупреждением (30 сек, предупреждение на 20-й)
     * работает только там, где ГП задал **вопрос** и ждёт ответа.
     *
     * НЕ требует:
     *   - AWAITING_CONTINUE — это констатация, юзер работает с экраном;
     *   - FOUND_PINNED — обычное слушание;
     *   - LISTENING — обычное слушание;
     *   - PAUSED — пауза явная.
     */
    val hasWaitTimeout: Boolean
        get() = this == AWAITING_WEIGHT
                || this == AWAITING_CHOICE
                || this == AWAITING_MARK
                || this == AWAITING_CLEAR
                || this == AWAITING_POSTPONE
                || this == AWAITING_CONFIRM
}
