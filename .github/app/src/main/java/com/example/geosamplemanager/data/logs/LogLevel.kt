package com.example.geosamplemanager.data.logs

/**
 * FIX 5.9-logs-1:
 * Уровень записи. В UI: info — обычный текст,
 * warn — жёлтый, error — красный.
 */
enum class LogLevel(val code: String, val label: String) {
    INFO("info", "Обычное"),
    WARN("warn", "Внимание"),
    ERROR("error", "Ошибка");

    companion object {
        fun fromCode(code: String): LogLevel? =
            values().firstOrNull { it.code == code }
    }
}