package com.example.geosamplemanager.data.logs

/**
 * FIX 5.9-logs-2:
 * Fluent-API для сборки записи журнала.
 *
 * Использование:
 *   Log.app("Приложение запущено").write()
 *   Log.db("Импортирован бэкап X").detail("samples", 30).write()
 *   Log.error("Не удалось прочитать бэкап", e).detail("file", n).write()
 *
 * Пока не вызван .write() — запись не отправляется в LogWriter.
 * Так простое логирование пишется одной строкой (без параметров),
 * а сложное — цепочкой.
 *
 * nowMs — для детерминизма в юнит-тестах. По умолчанию — системное
 * время. В тестах подменяем на фиксированную лямбду.
 */
class LogEntryBuilder internal constructor(
    private val category: LogCategory,
    private val level: LogLevel,
    private val summary: String,
    private val sessionId: String,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    private val detailsMap = LinkedHashMap<String, Any?>()

    fun detail(key: String, value: Any?): LogEntryBuilder {
        detailsMap[key] = value
        return this
    }

    fun details(map: Map<String, Any?>): LogEntryBuilder {
        detailsMap.putAll(map)
        return this
    }

    /**
     * Добавить данные исключения в details.
     *
     *  - error_type   — полное имя класса (FileNotFoundException)
     *  - error_message — t.message
     *  - error_stack  — stackTraceToString()
     *
     * Не бросает, если throwable == null — просто no-op.
     */
    fun detailThrowable(t: Throwable?): LogEntryBuilder {
        if (t == null) return this
        detailsMap["error_type"] = t::class.qualifiedName ?: t::class.simpleName
        detailsMap["error_message"] = t.message
        detailsMap["error_stack"] = t.stackTraceToString()
        return this
    }

    fun build(): LogEntry = LogEntry(
        createdAt = nowMs(),
        sessionId = sessionId,
        category = category.code,
        level = level.code,
        summary = summary,
        details = if (detailsMap.isEmpty()) null else DetailsJson.encode(detailsMap)
    )

    /**
     * Отправить запись в LogWriter (неблокирующая отправка
     * в канал). Возвращает саму запись — на случай, если тест
     * или инструмент хочет проверить, что было записано.
     */
    fun write(): LogEntry {
        val entry = build()
        LogWriter.send(entry)
        return entry
    }
}