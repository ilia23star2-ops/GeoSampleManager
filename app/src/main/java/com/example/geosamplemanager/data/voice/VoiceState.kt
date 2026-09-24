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
 * Источник правды — флаги в VoiceSession:
 *   pendingMarkChoice != null → AWAITING_CHOICE
 *   isPaused                  → PAUSED
 *   awaitingWeight            → AWAITING_WEIGHT
 *   awaitingContinue          → AWAITING_CONTINUE
 *   pinnedWellNumber != null  → FOUND_PINNED
 *   иначе                     → LISTENING
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

    /** Пауза. */
    PAUSED;

    val isAwaiting: Boolean
        get() = this == AWAITING_WEIGHT
                || this == AWAITING_CONTINUE
                || this == AWAITING_CHOICE

    val isActive: Boolean
        get() = this != IDLE

    val showsPanel: Boolean
        get() = this != IDLE
}