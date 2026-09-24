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

    /** Отметить несколько проб подряд одной фразой. */
    data class MarkByNumbers(val ordinals: List<Int>) : VoiceCommand()

    /** Отметить все пробы текущей скважины. */
    data object MarkAll : VoiceCommand()

    /** Отметить пробу, найденную последним поиском. */
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

    /**
     * FIX 5.8.11-e4-pin-2: переключиться к следующей скважине
     * в очереди мультизапроса. Только в состоянии FOUND_PINNED,
     * когда queue не пусто.
     */
    data object NextInQueue : VoiceCommand()

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

    /** Переключение режима голосом. */
    data class SetMode(val mode: VoiceSessionMode) : VoiceCommand()

    /** FIX 5.8.11-e4g3: явный поиск по глаголу «найди». */
    data class Find(val query: String?) : VoiceCommand()

    /** Выбор действия для уже отмеченной / отложенной пробы. */
    data object ChoiceRemove : VoiceCommand()

    /** Отложить текущую пробу из состояния выбора. */
    data object ChoicePostpone : VoiceCommand()

    /** Пропустить текущую пробу из состояния выбора. */
    data object ChoiceSkip : VoiceCommand()

    /** Распознать не удалось. */
    data object Unknown : VoiceCommand()
}