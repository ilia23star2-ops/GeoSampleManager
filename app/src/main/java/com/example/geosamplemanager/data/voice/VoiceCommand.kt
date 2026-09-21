package com.example.geosamplemanager.data.voice

/**
 * Команда, распознанная из фразы.
 *
 * Разбор и выполнение разделены: парсер даёт VoiceCommand,
 * ViewModel решает, что с ним делать.
 */
sealed class VoiceCommand {

    /** Поиск: введён номер скважины или пробы. */
    data class Search(val query: String) : VoiceCommand()

    /** Отметить пробу по порядковому номеру (1..30). */
    data class MarkOrdinal(val ordinal: Int) : VoiceCommand()

    /**
     * FIX 5.8.9g-1: отметить несколько проб подряд одной фразой.
     */
    data class MarkByNumbers(val ordinals: List<Int>) : VoiceCommand()

    /**
     * FIX 5.8.9g-1: отметить все пробы текущей скважины.
     */
    data object MarkAll : VoiceCommand()

    /**
     * FIX 5.8.9d-2a: отметить пробу, найденную последним поиском.
     *
     * Используется, когда после поиска конкретной пробы «15 26 01»
     * пользователь говорит «отметь» / «отметь её» / «эту».
     *
     * В отличие от [MarkOrdinal], номер пробы не называется —
     * берётся из сессии (что нашли, то и отмечаем).
     */
    data object MarkCurrent : VoiceCommand()

    /** Установить вес. */
    data class SetWeight(val value: Double) : VoiceCommand()

    /** Снять отметку у пробы по порядковому номеру. */
    data class ClearOrdinal(val ordinal: Int) : VoiceCommand()

    /** Снять отметку у последней отмеченной. */
    data object ClearLast : VoiceCommand()

    /** Снять все отметки в текущей скважине. */
    data object ClearAll : VoiceCommand()

    /** Снять «отложена» с текущей пробы. */
    data object Unpostpone : VoiceCommand()

    /** Перейти к следующей скважине (очистить запрос). */
    data object Next : VoiceCommand()

    /** Отмена. */
    data object Undo : VoiceCommand()

    /** Возврат отменённого. */
    data object Redo : VoiceCommand()

    /** Пауза. */
    data object Pause : VoiceCommand()

    /** Продолжить после паузы. */
    data object Resume : VoiceCommand()

    /** Завершить сессию. */
    data object Stop : VoiceCommand()

    /** «Сколько осталось». */
    data object HowManyLeft : VoiceCommand()

    /** Показать отложенные. */
    data object ShowPostponed : VoiceCommand()

    /** Показать найденные. */
    data object ShowFound : VoiceCommand()

    /** Справка. */
    data object Help : VoiceCommand()

    /** Сортировка: два и более номера подряд. */
    data class Sort(val queries: List<String>) : VoiceCommand()

    /**
     * FIX 5.8.9f-1a-fix-1: переключение режима голосом.
     *
     * Распознаётся из фраз:
     * «сортировка» / «режим сортировка» / «режим сортировки» → SORT.
     * «поиск» / «режим поиск» → SEARCH.
     */
    data class SetMode(val mode: VoiceSessionMode) : VoiceCommand()

    /**
     * FIX 5.8.9d-3c2b1: выбор действия для уже отмеченной / отложенной пробы.
     *
     * Используется только когда VoiceSession.pendingMarkChoice != null.
     *
     * Примеры:
     * «снять»    → ChoiceRemove
     * «отложить» → ChoicePostpone
     * «пропустить» → ChoiceSkip
     */
    data object ChoiceRemove : VoiceCommand()

    /** FIX 5.8.9d-3c2b1: отложить текущую пробу из состояния выбора. */
    data object ChoicePostpone : VoiceCommand()

    /** FIX 5.8.9d-3c2b1: пропустить текущую пробу из состояния выбора. */
    data object ChoiceSkip : VoiceCommand()

    /** Распознать не удалось. */
    data object Unknown : VoiceCommand()
}
