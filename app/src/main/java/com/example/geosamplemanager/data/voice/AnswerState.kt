package com.example.geosamplemanager.data.voice

/**
 * Состояние ответа на запрос. Определяет цвет индикатора и звук.
 *
 * Четыре состояния:
 *   OK        — единственный ответ, всё совпало (🟢).
 *   ATTENTION — есть нюанс, нужно внимание (🟡).
 *   ERROR     — ответа нет (🔴).
 *   IDLE      — жду запроса (⚪).
 */
enum class AnswerState {
    OK,
    ATTENTION,
    ERROR,
    IDLE
}

/**
 * Причина состояния. Определяет текст индикатора и фразу ГП.
 *
 * Причина → состояние однозначно, через [state].
 * Единственное место, где определяется цвет индикатора.
 */
enum class AnswerReason {
    /** Пустой запрос. Ждём. */
    IDLE_WAITING,

    /** Единственный ответ, всё совпало. */
    OK_SINGLE,

    /** Ответ в другом наряде (имеет смысл, только если выбран наряд). */
    FOUND_OTHER_ORDER,

    /** Ответ в другом участке. */
    FOUND_OTHER_AREA,

    /** Несколько нарядов с этим номером. */
    FOUND_MULTIPLE,

    /** Несколько участков. */
    FOUND_MULTIPLE_AREA,

    /** Номера нет в БД. */
    NOT_FOUND,

    /** Ошибка парсера или поиска. */
    SEARCH_FAILED;

    /** Состояние, соответствующее причине. Единственная точка маппинга. */
    val state: AnswerState
        get() = when (this) {
            IDLE_WAITING -> AnswerState.IDLE
            OK_SINGLE -> AnswerState.OK
            FOUND_OTHER_ORDER,
            FOUND_OTHER_AREA,
            FOUND_MULTIPLE,
            FOUND_MULTIPLE_AREA -> AnswerState.ATTENTION
            NOT_FOUND,
            SEARCH_FAILED -> AnswerState.ERROR
        }

    /** Краткий статус для индикатора (строка 2). */
    val shortLabel: String
        get() = when (state) {
            AnswerState.IDLE -> "Жду"
            AnswerState.OK -> "Найдено"
            AnswerState.ATTENTION -> "Внимание"
            AnswerState.ERROR -> when (this) {
                NOT_FOUND -> "Не найдено"
                else -> "Ошибка"
            }
        }

    /** Причина для индикатора (строка 3). Пустая — не показывать. */
    val detailLabel: String
        get() = when (this) {
            IDLE_WAITING -> ""
            OK_SINGLE -> ""
            FOUND_OTHER_ORDER -> "Другой наряд"
            FOUND_OTHER_AREA -> "Другой участок"
            FOUND_MULTIPLE -> "Несколько нарядов"
            FOUND_MULTIPLE_AREA -> "Несколько участков"
            NOT_FOUND -> ""
            SEARCH_FAILED -> ""
        }
}
