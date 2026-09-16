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
     * Пример: «пятая шестая седьмая» → [5, 6, 7].
     * Порядок сохраняется — отмечаем как сказано.
     */
    data class MarkByNumbers(val ordinals: List<Int>) : VoiceCommand()

    /**
     * FIX 5.8.9g-1: отметить все пробы текущей скважины.
     * Пример: «отметь все», «отметить все», «все».
     */
    data object MarkAll : VoiceCommand()

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

    /** Распознать не удалось. */
    data object Unknown : VoiceCommand()
}