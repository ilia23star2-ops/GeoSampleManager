package com.example.geosamplemanager.data.logs

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.geosamplemanager.data.backup.PublicBackupsMigrator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * FIX 5.9-logs-6:
 * Файловый архив журнала. Каждая запись дополнительно к logs.db
 * пишется в человекочитаемый .log-файл:
 *
 *   Downloads/GeoSampleManager/.logs/YYYY-MM-DD.log
 *
 * Папка с точкой в начале — скрыта от стандартных файловых
 * менеджеров Android. Рабочий её не видит. Доступ — только у
 * администратора: USB, adb, root, специальные файл-менеджеры.
 *
 * Один файл на день. Дописывается до полуночи. На следующий
 * день — новый файл, старые остаются архивом.
 *
 * FIX 5.9-logs-7:
 *  - папка .logs (скрытая) вместо logs;
 *  - Unicode-маркеры уровней: ● ⚠ ✕;
 *  - шапка дня с разделителем ━;
 *  - details раскрываются построчно через └ key: value;
 *  - время в записи — только HH:mm:ss (дата в шапке дня).
 */
object LogFileWriter {

    /** Скрытая папка в Загрузках. Рабочий её не видит. */
    const val LOGS_SUBDIR = ".logs"

    private const val DAY_PATTERN = "yyyy-MM-dd"
    private const val CATEGORY_WIDTH = 12
    private const val HEADER_LINE = "━"
    private const val HEADER_WIDTH = 48

    /**
     * Кэш текущего файла дня. Один процесс — один файл за раз.
     * Если день сменился (переход через полночь) — перезаписываем.
     */
    private var cachedDay: String? = null
    private var cachedUri: Uri? = null

    /**
     * Дописать батч записей в файл(ы) по дням. Если записи
     * охватывают несколько дней (пересечение полуночи) —
     * делятся на группы по дню.
     */
    fun appendBatch(context: Context, entries: List<LogEntry>) {
        if (entries.isEmpty()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val grouped = entries.groupBy { dayKey(it.createdAt) }
        for ((day, dayEntries) in grouped) {
            try {
                appendForDay(context, day, dayEntries)
            } catch (_: Exception) {
                // Не критично — запись всё равно осталась в logs.db.
            }
        }
    }

    /**
     * Одна запись журнала для файла. Чистая функция —
     * покрывается юнит-тестами.
     *
     * Формат:
     *   <маркер>  HH:mm:ss  Категория   summary
     *                                     └ key: value
     *                                     └ key: value
     *
     * Маркер: ● (инфо), ⚠ (предупреждение), ✕ (ошибка).
     */
    fun formatEntry(entry: LogEntry, tz: TimeZone = TimeZone.getDefault()): String {
        val time = LogFormatter.timeOnly(entry.createdAt, tz)
        val marker = levelMarker(entry.level)
        val category = categoryLabel(entry.category).padEnd(CATEGORY_WIDTH)
        val summary = escape(entry.summary)

        val sb = StringBuilder()
        sb.append(' ').append(marker).append("  ")
        sb.append(time).append("  ")
        sb.append(category).append("  ")
        sb.append(summary)

        val details = entry.details
        if (!details.isNullOrBlank()) {
            sb.append('\n')
            sb.append(formatDetails(details))
        }
        return sb.toString()
    }

    /**
     * Шапка дня. Пишется один раз при создании файла.
     *   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     *    05.10.2026
     *   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     */
    fun formatDayHeader(isoDay: String): String {
        val display = isoToDisplayDate(isoDay)
        val line = HEADER_LINE.repeat(HEADER_WIDTH)
        return "$line\n $display\n$line\n\n"
    }

    // ================================================================
    // Внутренняя часть
    // ================================================================

    private fun dayKey(ms: Long): String {
        val sdf = SimpleDateFormat(DAY_PATTERN, Locale.US).apply {
            isLenient = false
        }
        return sdf.format(Date(ms))
    }

    private fun appendForDay(
        context: Context,
        day: String,
        entries: List<LogEntry>
    ) {
        val fileName = "$day.log"
        val (uri, isNew) = findOrCreateFile(context, day, fileName)
        val resolver = context.contentResolver

        resolver.openOutputStream(uri, "wa")?.use { out ->
            if (isNew) {
                out.write(formatDayHeader(day).toByteArray(Charsets.UTF_8))
            }
            for (e in entries) {
                out.write(formatEntry(e).toByteArray(Charsets.UTF_8))
                out.write('\n'.code)
            }
        } ?: throw IllegalStateException("Не удалось открыть файл журнала")
    }

    private fun findOrCreateFile(
        context: Context,
        day: String,
        fileName: String
    ): Pair<Uri, Boolean> {
        // FIX 5.9-logs-6 (fix): кэш на текущий день.
        if (cachedDay == day) {
            cachedUri?.let { return it to false }
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        // Ищем только по DISPLAY_NAME: путь на разных прошивках
        // отличается регистром/слешами, query по RELATIVE_PATH
        // может не сработать и файл создастся заново.
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val args = arrayOf(fileName)

        try {
            resolver.query(collection, projection, selection, args, null)?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(0)
                    val uri = ContentUris.withAppendedId(collection, id)
                    cachedDay = day
                    cachedUri = uri
                    return uri to false
                }
            }
        } catch (_: Exception) {
            // Если query упал — пробуем создать.
        }

        // Не нашли — создаём.
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/" +
                        "${PublicBackupsMigrator.ROOT_DIR}/$LOGS_SUBDIR"
            )
        }
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Не удалось создать файл журнала")
        cachedDay = day
        cachedUri = uri
        return uri to true
    }

    private fun formatDetails(json: String): String {
        val map = DetailsJson.decode(json)
        if (map.isNullOrEmpty()) {
            return "     └ ${escape(json)}"
        }
        return map.entries.joinToString("\n") { (k, v) ->
            "     └ $k: ${escape(v?.toString() ?: "")}"
        }
    }

    private fun levelMarker(code: String): String = when (code) {
        LogLevel.ERROR.code -> "✕"
        LogLevel.WARN.code -> "⚠"
        LogLevel.INFO.code -> "●"
        else -> "·"
    }

    private fun categoryLabel(code: String): String =
        LogCategory.fromCode(code)?.label ?: code

    private fun isoToDisplayDate(iso: String): String {
        val parts = iso.split("-")
        val allNumeric = parts.size == 3 && parts.all { it.toIntOrNull() != null }
        return if (allNumeric) "${parts[2]}.${parts[1]}.${parts[0]}"
        else iso
    }

    /**
     * Экранируем переводы строк — каждая часть записи должна быть
     * одной строкой. `\n` превращаем в пробел, чтобы многострочный
     * stacktrace не сломал формат.
     */
    private fun escape(s: String): String = s
        .replace("\r\n", " ")
        .replace('\n', ' ')
        .replace('\r', ' ')
}