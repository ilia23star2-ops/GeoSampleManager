package com.example.geosamplemanager.data.voice

/**
 * FIX 5.8.11-a (SEARCH_MODEL §3):
 * Состояние голосового помощника — что он принимает прямо сейчас.
 *
 * Ортогонально [VoiceSessionMode] (SEARCH / SORT): режим может быть
 * любым при любом состоянии.
 *
 * Источник правды — существующие флаги в VoiceSession:
 *   isPaused           → PAUSED
 *   pendingMarkChoice  → AWAITING_CHOICE
 *   awaitingWeight     → AWAITING_WEIGHT
 *   awaitingContinue   → AWAITING_CONTINUE
 *   иначе              → LISTENING
 *
 * На этом шаге state — вычисляемый (см. VoiceSession.state).
 * Явные переходы через enum — отдельным заходом, если потребуется.
 */
enum class VoiceState {

    /** ГП не запущен. Ничего не слушает. */
    IDLE,

    /** Слушает. Принимает команды и поиск. */
    LISTENING,

    /** Спросил «Вес?» и ждёт число. */
    AWAITING_WEIGHT,

    /** Спросил «Выберите на экране» и ждёт. */
    AWAITING_CONTINUE,

    /** Спросил «Снять, отложить или пропустить?». */
    AWAITING_CHOICE,

    /** Пауза. Активны только «продолжить» и «стоп». */
    PAUSED;

    /** Ждём ответа пользователя — панель показывает вопрос. */
    val isAwaiting: Boolean
        get() = this == AWAITING_WEIGHT
                || this == AWAITING_CONTINUE
                || this == AWAITING_CHOICE

    /** Активная сессия — ГП слушает или ждёт ответа. */
    val isActive: Boolean
        get() = this != IDLE

    /** Показывает ли панель ГП. */
    val showsPanel: Boolean
        get() = this != IDLE
}
