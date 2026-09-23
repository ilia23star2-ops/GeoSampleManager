package com.example.geosamplemanager.data.voice

/**
 * FIX 5.8.11-d2 (SEARCH_MODEL §7):
 * Единая модель ответа. Из неё строятся и UI, и TTS.
 *
 * Один объект — два представления. Это закрывает Б3 (разные ответы
 * UI и TTS): оба смотрят на один и тот же Response, а не строят
 * фразы каждый по-своему.
 *
 * Строится презентером из SearchResult или из результата команды.
 * Используется Presenter.render() для финального рендера.
 */
data class Response(
    /** Тип ответа. */
    val kind: ResponseKind,

    /** Главная строка. Коротко, по сути: «Скважина 15 24». */
    val primary: String,

    /** Детали. Фиксированный порядок. Может быть пустым. */
    val details: List<String> = emptyList(),

    /** Причина. Определяет цвет и звук. */
    val reason: AnswerReason,

    /** Какой звук играть. */
    val sound: SoundKind,

    /** Ждём ли ответа пользователя (для AWAITING_*). */
    val pause: Boolean = false,

    /** Структура ввода — для озвучки номера. */
    val groups: List<DigitGroup>? = null,

    /** Дополнительный контекст. */
    val context: ResponseContext? = null
)

/**
 * Тип ответа. Определяет, как рендерить.
 */
enum class ResponseKind {
    FOUND_ONE,
    FOUND_MANY,
    NOT_FOUND,
    MARKED,
    MARKED_MULTIPLE,
    MARKED_ALL,
    UNMARKED,
    WEIGHT_SET,
    MODE_CHANGED,
    NEXT,
    MESSAGE,
    ASKING_WEIGHT,
    ASKING_CHOICE,
    ASKING_CONTINUE,
    UNKNOWN,
    STOPPED
}

/**
 * Какой звук играть.
 */
enum class SoundKind {
    OK,
    ATTENTION,
    ERROR,
    NONE
}

/**
 * Дополнительный контекст ответа.
 */
data class ResponseContext(
    /** Для MARKED / MARKED_MULTIPLE / UNMARKED — номера проб. */
    val sampleNumbers: List<String> = emptyList(),

    /** Для MARKED — порядковый номер внутри скважины. */
    val ordinal: Int? = null,

    /** Для WEIGHT_SET — установленный вес. */
    val weight: Double? = null,

    /** Для MARKED — это весовой контроль / холостая. */
    val isWeightControl: Boolean = false,
    val isBlank: Boolean = false,

    /** Для FOUND_ONE — статистика по скважине. */
    val totalSamples: Int = 0,
    val foundSamples: Int = 0,
    val blanks: Int = 0,
    val weightControls: Int = 0,
    val postponed: Int = 0,

    /** Для FOUND_ONE — найденное — это проба (true) или скважина (false). */
    val isSample: Boolean = false,

    /** Для FOUND_* — внимание. */
    val otherAreaTitle: String? = null,
    val otherOrderNumber: String? = null
)

// ================================================================
// Маппинг SearchResult → Response
// ================================================================

/**
 * Единственное место, где SearchResult превращается в Response.
 * Всё остальное (UI, TTS, звук) работает с Response.
 */
object ResponseMapper {

    /**
     * Собрать Response из SearchResult (результат поиска).
     *
     * @param state  текущее состояние ГП (влияет на kind)
     * @param mode   режим (SEARCH / SORT) — влияет на формат details
     */
    fun fromSearch(
        result: SearchResult,
        state: VoiceState,
        mode: VoiceSessionMode
    ): Response {
        return when (result) {
            SearchResult.NotFound -> Response(
                kind = ResponseKind.NOT_FOUND,
                primary = "Не нашёл",
                details = emptyList(),
                reason = AnswerReason.NOT_FOUND,
                sound = SoundKind.ERROR,
                pause = false
            )

            is SearchResult.Failed -> Response(
                kind = ResponseKind.NOT_FOUND,
                primary = "Ошибка поиска",
                details = emptyList(),
                reason = AnswerReason.SEARCH_FAILED,
                sound = SoundKind.ERROR,
                pause = false
            )

            is SearchResult.Found -> fromFound(result, mode)
        }
    }

    private fun fromFound(
        result: SearchResult.Found,
        mode: VoiceSessionMode
    ): Response {
        val hits = result.hits
        val first = hits.first()
        val isSample = result.matchedKind == UnifiedMatchKind.SAMPLE

        // SORT — упрощённый ответ: только «запрос — наряд».
        if (mode == VoiceSessionMode.SORT) {
            val orderNumber = first.orderNumber
            return Response(
                kind = ResponseKind.FOUND_ONE,
                primary = "${result.matchedValue} — Наряд №$orderNumber",
                details = emptyList(),
                reason = AnswerReason.OK_SINGLE,
                sound = SoundKind.NONE,
                pause = false,
                groups = result.groups
            )
        }

        // SEARCH — полный ответ.
        val areaTitles = hits.map { it.areaTitle }.distinct()
        val orderFull = hits
            .map { "${it.areaTitle} / Наряд №${it.orderNumber}" }
            .distinct()

        val reason: AnswerReason = when {
            orderFull.size == 1 -> AnswerReason.OK_SINGLE
            areaTitles.size > 1 -> AnswerReason.FOUND_MULTIPLE_AREA
            else -> AnswerReason.FOUND_MULTIPLE
        }

        val (kind, sound, pause) = when (reason) {
            AnswerReason.OK_SINGLE -> Triple(ResponseKind.FOUND_ONE, SoundKind.NONE, false)
            else -> Triple(ResponseKind.FOUND_MANY, SoundKind.ATTENTION, true)
        }

        val primary = if (isSample) {
            "Проба ${result.matchedValue}"
        } else {
            "Скважина ${result.matchedValue}"
        }

        val details = mutableListOf<String>()
        if (orderFull.size == 1) {
            details.add("Наряд №${first.orderNumber}")
        } else {
            details.add("Наряды: ${orderFull.joinToString(", ")}")
        }
        details.add("Всего проб: ${hits.size}")

        return Response(
            kind = kind,
            primary = primary,
            details = details,
            reason = reason,
            sound = sound,
            pause = pause,
            groups = result.groups,
            context = ResponseContext(
                isSample = isSample,
                otherAreaTitle = if (areaTitles.size == 1) areaTitles.first() else null,
                otherOrderNumber = if (orderFull.size == 1) first.orderNumber else null
            )
        )
    }
}
