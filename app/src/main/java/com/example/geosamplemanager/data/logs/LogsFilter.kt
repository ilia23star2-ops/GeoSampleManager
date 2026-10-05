package com.example.geosamplemanager.data.logs

/**
 * FIX 5.9-logs-5:
 * Фильтр журнала в UI. Каждое значение — либо «Все» (code = null),
 * либо отсылка к LogCategory по code. Метки — русские.
 */
enum class LogsFilter(val code: String?, val label: String) {
    ALL(null, "Все"),
    ERROR(LogCategory.ERROR.code, "Ошибки"),
    APP(LogCategory.APP.code, "Приложение"),
    NAV(LogCategory.NAV.code, "Навигация"),
    SEARCH(LogCategory.SEARCH.code, "Поиск"),
    VOICE(LogCategory.VOICE.code, "Голос"),
    MARK(LogCategory.MARK.code, "Отметки"),
    EDIT(LogCategory.EDIT.code, "Редактирование"),
    DB(LogCategory.DB.code, "База данных");
}