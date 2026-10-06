package com.example.geosamplemanager.data.stats

/**
 * FIX 5.10-stat-model:
 * Перечисления для теневой статистики.
 *
 * Хранится в БД — код (`code`). Показывается в UI — метка (`label`
 * или `title`). Разделение обязательно: в БД хранить только код,
 * чтобы случайная смена формулировки не сломала старые записи.
 *
 * fromCode — безопасный разбор: неизвестная строка даёт дефолт
 * (INFO / IN_PROGRESS) или null (для необязательных).
 */

enum class EventLevel(val code: String, val label: String) {
    INFO("info", "Инфо"),
    WARN("warn", "Внимание"),
    ERROR("error", "Ошибка");

    companion object {
        fun fromCode(code: String?): EventLevel =
            values().firstOrNull { it.code == code } ?: INFO
    }
}

enum class EventCategory(val code: String, val label: String) {
    APP("app", "Приложение"),
    NAV("nav", "Навигация"),
    SEARCH("search", "Поиск"),
    VOICE("voice", "Голос"),
    MARK("mark", "Отметки"),
    EDIT("edit", "Редактирование"),
    DB("db", "База данных"),
    ERROR("error", "Ошибки");

    companion object {
        fun fromCode(code: String?): EventCategory? =
            values().firstOrNull { it.code == code }
    }
}

enum class TabKind(val code: String, val title: String) {
    MAIN("main", "Главная"),
    ADD("add", "Добавить"),
    SEARCH("search", "Сверка и поиск"),
    STATS("stats", "Статистика"),
    EDIT("edit", "Редактирование"),
    DB("db", "База данных"),
    SETTINGS("settings", "Настройки");

    companion object {
        fun fromCode(code: String?): TabKind? =
            values().firstOrNull { it.code == code }
    }
}

enum class OrderWorkStatus(val code: String, val label: String) {
    IN_PROGRESS("in_progress", "В работе"),
    HALF_DONE("half_done", "Наполовину"),
    DONE("done", "Готов");

    companion object {
        fun fromCode(code: String?): OrderWorkStatus =
            values().firstOrNull { it.code == code } ?: IN_PROGRESS
    }
}

/**
 * FIX 5.10-stat-activity-b: фаза наряда.
 * В БД не хранится — это живое состояние OrderWorkTracker в памяти.
 */
enum class OrderWorkPhase(val code: String, val label: String) {
    SEARCH("search", "Поиск"),
    VERIFY("verify", "Сверка")
}