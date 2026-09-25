package com.example.geosamplemanager.data.voice

/**
 * Команда, распознанная из фразы.
 *
 * FIX 5.8.11-e4-markers-2:
 * Добавлена команда PostponeOrdinal — отложить пробу по номеру
 * одной фразой: «отложить вторую», «отложи 7».
 *
 * FIX 5.8.11-e4-weight-queue:
 * Добавлена команда SkipWeightItem — «пропустить» на вопросе веса
 * в очереди: оставить пробу неотмеченной, перейти к следующей.
 */
sealed class VoiceCommand {

    data class Search(val query: String) : VoiceCommand()
    data class MarkOrdinal(val ordinal: Int) : VoiceCommand()
    data class MarkByNumbers(val ordinals: List<Int>) : VoiceCommand()
    data object MarkAll : VoiceCommand()
    data object MarkCurrent : VoiceCommand()
    data class SetWeight(val value: Double) : VoiceCommand()
    data class ClearOrdinal(val ordinal: Int) : VoiceCommand()
    data object ClearLast : VoiceCommand()
    data object ClearAll : VoiceCommand()
    data class PostponeOrdinal(val ordinal: Int) : VoiceCommand()
    data object Unpostpone : VoiceCommand()
    data object Next : VoiceCommand()
    data object NextInQueue : VoiceCommand()
    data object Undo : VoiceCommand()
    data object Redo : VoiceCommand()
    data object Pause : VoiceCommand()
    data object Resume : VoiceCommand()
    data object Stop : VoiceCommand()
    data object HowManyLeft : VoiceCommand()
    data object ShowPostponed : VoiceCommand()
    data object ShowFound : VoiceCommand()
    data object Help : VoiceCommand()
    data class Sort(val queries: List<String>) : VoiceCommand()
    data class SetMode(val mode: VoiceSessionMode) : VoiceCommand()
    data class Find(val query: String?) : VoiceCommand()

    // ================================================================
    // Маркеры намерения + подтверждение
    // ================================================================

    data object MarkIntent : VoiceCommand()
    data object ClearIntent : VoiceCommand()
    data object PostponeIntent : VoiceCommand()
    data object Confirm : VoiceCommand()
    data object Decline : VoiceCommand()

    /**
     * FIX 5.8.11-e4-weight-queue:
     * «Пропустить» на вопросе веса в очереди — оставить пробу
     * неотмеченной, перейти к следующей.
     */
    data object SkipWeightItem : VoiceCommand()

    // ================================================================

    data object ChoiceRemove : VoiceCommand()
    data object ChoicePostpone : VoiceCommand()
    data object ChoiceSkip : VoiceCommand()

    data object Unknown : VoiceCommand()
}
