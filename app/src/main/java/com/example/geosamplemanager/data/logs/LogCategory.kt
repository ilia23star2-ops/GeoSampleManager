package com.example.geosamplemanager.data.logs

/**
 * FIX 5.9-logs-1:
 * Категория записи в журнале. Код — для БД, метка — для UI
 * (фильтр, цвет, группировка).
 *
 * Метки — русские, без английских букв. Правило из §2.4:
 * summary — обычным языком, коды живут только в details.
 */
enum class LogCategory(val code: String, val label: String) {
    APP("app", "Приложение"),
    NAV("nav", "Навигация"),
    SEARCH("search", "Поиск"),
    VOICE("voice", "Голос"),
    MARK("mark", "Отметки"),
    EDIT("edit", "Редактирование"),
    DB("db", "База данных"),
    ERROR("error", "Ошибки");

    companion object {
        fun fromCode(code: String): LogCategory? =
            values().firstOrNull { it.code == code }
    }
}