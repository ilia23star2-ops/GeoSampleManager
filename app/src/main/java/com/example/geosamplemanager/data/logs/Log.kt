package com.example.geosamplemanager.data.logs

/**
 * FIX 5.9-logs-2:
 * Точка входа для логирования. Один статический метод на
 * категорию — чтобы код-вызов читался коротко и однозначно.
 *
 * Примеры:
 *   Log.app("Приложение запущено").write()
 *   Log.nav("Открыта вкладка «Сверка»").detail("route", "search").write()
 *   Log.db("Импортирован бэкап X").detail("samples", 30).write()
 *   Log.error("Не удалось прочитать бэкап", e).detail("file", n).write()
 *
 * Ничего не пишет в summary кроме переданной фразы. Всё
 * техническое — только через .detail(...) → details.
 */
object Log {

    fun app(summary: String): LogEntryBuilder =
        LogWriter.info(LogCategory.APP, summary)

    fun nav(summary: String): LogEntryBuilder =
        LogWriter.info(LogCategory.NAV, summary)

    fun search(summary: String): LogEntryBuilder =
        LogWriter.info(LogCategory.SEARCH, summary)

    fun voice(summary: String): LogEntryBuilder =
        LogWriter.info(LogCategory.VOICE, summary)

    fun mark(summary: String): LogEntryBuilder =
        LogWriter.info(LogCategory.MARK, summary)

    fun edit(summary: String): LogEntryBuilder =
        LogWriter.info(LogCategory.EDIT, summary)

    fun db(summary: String): LogEntryBuilder =
        LogWriter.info(LogCategory.DB, summary)

    /** Уровень WARN для неожиданных, но не критичных ситуаций. */
    fun warn(category: LogCategory, summary: String): LogEntryBuilder =
        LogWriter.warn(category, summary)

    /** Уровень ERROR. throwable — необязательно, но рекомендуется. */
    fun error(summary: String, t: Throwable? = null): LogEntryBuilder =
        LogWriter.error(LogCategory.ERROR, summary, t)
}