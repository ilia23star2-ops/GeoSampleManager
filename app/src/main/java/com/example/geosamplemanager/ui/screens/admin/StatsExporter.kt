package com.example.geosamplemanager.ui.screens.admin

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/**
 * FIX 5.10-stat-daily-file-1:
 * Экспорт дня в JSON-файл `filesDir/stats/exports/YYYY-MM-DD.json`.
 *
 * Формат — по спеке SHADOW_STATS §5. Сборка — чистая функция
 * (тестируется в JVM). Запись — suspend, в Dispatchers.IO.
 *
 * Решения:
 *  - У1=Б: приватная папка filesDir/stats/exports/ (не Downloads);
 *  - У3=А: перезапись файла при повторном экспорте;
 *  - У4=А: ended_at = null пишется как есть;
 *  - У7=А: русские названия дней недели вручную.
 *
 * FIX 5.10-stats-exporter-nulls:
 *  - добавлен `.serializeNulls()` — иначе Gson пропускает поля
 *    со значением null, и потребитель JSON не видит, что поле
 *    существует. Тесты `session_openEndedAtIsNull`,
 *    `event_withoutDetails_detailsIsNull`,
 *    `event_brokenDetailsJson_detailsIsNull` ожидают, что null
 *    будет записан явно.
 */
object StatsExporter {

    private const val FORMAT_VERSION = 1
    private const val EXPORTS_SUBDIR = "stats/exports"

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    /**
     * Сборка JSON-строки для экспорта. Чистая функция — не касается
     * файлов. Тестируется в JVM.
     */
    fun buildJson(dayView: DayView, problems: ProblemsView): String {
        val dto = DayDto(
            formatVersion = FORMAT_VERSION,
            date = dayView.date,
            weekday = weekdayRu(dayView.date),
            inWorkWindow = isWorkDay(dayView.date),
            totals = TotalsDto(
                activeSec = dayView.totals.activeSec,
                idleSec = dayView.totals.idleSec,
                runs = dayView.totals.runs,
                readyOrders = dayView.totals.readyOrders,
                errorsCount = dayView.totals.errorsCount,
                warnsCount = dayView.totals.warnsCount,
                byTab = dayView.tabUsage.associate { it.tab.code to it.totalSec }
            ),
            sessions = dayView.sessions.map { s ->
                SessionDto(
                    id = s.id,
                    startedAt = s.startedAt,
                    endedAt = s.endedAt,
                    activeSec = s.activeSec,
                    idleSec = s.idleSec,
                    crashFlag = s.crashFlag,
                    tabVisits = s.visits.map { v ->
                        TabVisitDto(
                            tab = v.tab.code,
                            fromTs = v.fromTs,
                            toTs = v.toTs,
                            durationSec = v.durationSec
                        )
                    },
                    orderWork = s.orderWorks.map { w ->
                        OrderWorkDto(
                            orderId = w.orderId,
                            areaTitle = w.areaTitle,
                            orderTitle = w.orderTitle,
                            startedAt = w.startedAt,
                            endedAt = w.endedAt,
                            searchSec = w.searchSec,
                            verifySec = w.verifySec,
                            status = w.status.code,
                            totalSamples = w.totalSamples,
                            foundSamples = w.foundSamples
                        )
                    }
                )
            },
            events = dayView.events.map { e ->
                EventDto(
                    atTs = e.atTs,
                    level = e.level.code,
                    category = e.category.code,
                    summary = e.summary,
                    details = parseDetails(e.detailsJson)
                )
            },
            dbProblems = DbProblemsDto(
                orphanOrders = problems.orphanOrders,
                orphanSamples = problems.orphanSamples,
                brokenPhotos = problems.brokenPhotos
            )
        )
        return gson.toJson(dto)
    }

    /**
     * Записать JSON дня в файл. Возвращает файл или null при ошибке.
     */
    suspend fun export(
        context: Context,
        dayView: DayView,
        problems: ProblemsView
    ): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, EXPORTS_SUBDIR)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "${dayView.date}.json")
            file.writeText(buildJson(dayView, problems), Charsets.UTF_8)
            file
        } catch (_: Exception) {
            null
        }
    }

    // ============================================================
    // Вспомогательные
    // ============================================================

    private fun parseDetails(json: String?): JsonElement? {
        if (json.isNullOrBlank()) return null
        return try {
            JsonParser.parseString(json)
        } catch (_: Exception) {
            null
        }
    }

    private fun weekdayRu(date: String): String {
        val parts = date.split("-")
        if (parts.size != 3) return "?"
        val y = parts[0].toIntOrNull() ?: return "?"
        val m = parts[1].toIntOrNull() ?: return "?"
        val d = parts[2].toIntOrNull() ?: return "?"
        val cal = Calendar.getInstance()
        cal.set(y, m - 1, d)
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Пн"
            Calendar.TUESDAY -> "Вт"
            Calendar.WEDNESDAY -> "Ср"
            Calendar.THURSDAY -> "Чт"
            Calendar.FRIDAY -> "Пт"
            Calendar.SATURDAY -> "Сб"
            Calendar.SUNDAY -> "Вс"
            else -> "?"
        }
    }

    private fun isWorkDay(date: String): Boolean {
        val parts = date.split("-")
        if (parts.size != 3) return false
        val y = parts[0].toIntOrNull() ?: return false
        val m = parts[1].toIntOrNull() ?: return false
        val d = parts[2].toIntOrNull() ?: return false
        val cal = Calendar.getInstance()
        cal.set(y, m - 1, d)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        return dow != Calendar.SATURDAY && dow != Calendar.SUNDAY
    }

    // ============================================================
    // DTO для Gson. Имена полей в JSON — snake_case по спеке.
    // ============================================================

    private data class DayDto(
        @SerializedName("format_version") val formatVersion: Int,
        val date: String,
        val weekday: String,
        @SerializedName("in_work_window") val inWorkWindow: Boolean,
        val totals: TotalsDto,
        val sessions: List<SessionDto>,
        val events: List<EventDto>,
        @SerializedName("db_problems") val dbProblems: DbProblemsDto
    )

    private data class TotalsDto(
        @SerializedName("active_sec") val activeSec: Int,
        @SerializedName("idle_sec") val idleSec: Int,
        val runs: Int,
        @SerializedName("ready_orders") val readyOrders: Int,
        @SerializedName("errors_count") val errorsCount: Int,
        @SerializedName("warns_count") val warnsCount: Int,
        @SerializedName("by_tab") val byTab: Map<String, Int>
    )

    private data class SessionDto(
        val id: Long,
        @SerializedName("started_at") val startedAt: Long,
        @SerializedName("ended_at") val endedAt: Long?,
        @SerializedName("active_sec") val activeSec: Int,
        @SerializedName("idle_sec") val idleSec: Int,
        @SerializedName("crash_flag") val crashFlag: Boolean,
        @SerializedName("tab_visits") val tabVisits: List<TabVisitDto>,
        @SerializedName("order_work") val orderWork: List<OrderWorkDto>
    )

    private data class TabVisitDto(
        val tab: String,
        @SerializedName("from_ts") val fromTs: Long,
        @SerializedName("to_ts") val toTs: Long?,
        @SerializedName("duration_sec") val durationSec: Int
    )

    private data class OrderWorkDto(
        @SerializedName("order_id") val orderId: Long,
        @SerializedName("area_title") val areaTitle: String,
        @SerializedName("order_title") val orderTitle: String,
        @SerializedName("started_at") val startedAt: Long,
        @SerializedName("ended_at") val endedAt: Long?,
        @SerializedName("search_sec") val searchSec: Int,
        @SerializedName("verify_sec") val verifySec: Int,
        val status: String,
        @SerializedName("total_samples") val totalSamples: Int,
        @SerializedName("found_samples") val foundSamples: Int
    )

    private data class EventDto(
        @SerializedName("at_ts") val atTs: Long,
        val level: String,
        val category: String,
        val summary: String,
        val details: JsonElement?
    )

    private data class DbProblemsDto(
        @SerializedName("orphan_orders") val orphanOrders: Int,
        @SerializedName("orphan_samples") val orphanSamples: Int,
        @SerializedName("broken_photos") val brokenPhotos: Int
    )
}