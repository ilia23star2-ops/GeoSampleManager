package com.example.geosamplemanager.data.report

import com.example.geosamplemanager.ui.screens.GroupStats
import com.example.geosamplemanager.ui.screens.SampleRow

/**
 * FIX 5.9-report-html:
 * Генератор HTML-отчёта по наряду.
 *
 * Структура:
 *  1. Шапка — участок, номер наряда, дата, сводка.
 *  2. Таблица проб — с цветами как в приложении. Если у пробы
 *     есть заметка или фото — ссылка «Прил. N».
 *  3. Приложения — по блоку на пробу с заметкой/фото. Внутри:
 *     заголовок, текст заметки, фото (base64), ссылка «↩ К пробе».
 *
 * Особенности:
 *  - Один самодостаточный .html — фото встроены через data-uri,
 *    интернет не нужен, открывается в любом браузере.
 *  - Кнопка «Сохранить PDF / Печать» в шапке — вызывает
 *    window.print() → в браузере «Сохранить как PDF».
 *  - Экранирование текстов — esc().
 *  - Цвета строк — как в приложении (found/error/postponed/blank/control).
 */

data class ReportPhoto(
    /** data:image/jpeg;base64,... — готово для <img src>. */
    val dataUri: String
)

data class ReportNote(
    val text: String
)

data class ReportSample(
    val row: SampleRow,
    val note: ReportNote?,
    val photos: List<ReportPhoto>
) {
    val hasAppendix: Boolean get() = note != null || photos.isNotEmpty()
}

data class ReportData(
    val areaName: String,
    val orderNumber: String,
    val generatedAt: String,
    val stats: GroupStats,
    val samples: List<ReportSample>
)

object ReportHtmlGenerator {

    fun generate(data: ReportData): String {
        val sb = StringBuilder(64 * 1024)
        sb.append("<!DOCTYPE html>\n")
        sb.append("<html lang=\"ru\"><head>")
        sb.append("<meta charset=\"UTF-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        sb.append("<title>Отчёт · Наряд №").append(esc(data.orderNumber)).append("</title>")
        sb.append("<style>").append(CSS).append("</style>")
        sb.append("</head><body>")

        sb.append(buildToolbar())
        sb.append(buildHeader(data))
        sb.append(buildTable(data))
        sb.append(buildAppendices(data))
        sb.append(buildFooter())

        sb.append("</body></html>")
        return sb.toString()
    }

    // ================================================================
    // ШАПКА / ПОДВАЛ
    // ================================================================

    private fun buildToolbar(): String =
        "<div class=\"toolbar no-print\">" +
                "<button onclick=\"window.print()\">🖨 Сохранить PDF / Печать</button>" +
                "</div>"

    private fun buildHeader(data: ReportData): String {
        val sb = StringBuilder()
        sb.append("<header class=\"header\">")
        sb.append("<h1>Отчёт по наряду</h1>")
        sb.append("<div class=\"meta\">")
        sb.append("<div><b>Участок:</b> ").append(esc(data.areaName)).append("</div>")
        sb.append("<div><b>Наряд:</b> №").append(esc(data.orderNumber)).append("</div>")
        sb.append("<div><b>Дата:</b> ").append(esc(data.generatedAt)).append("</div>")
        sb.append("</div>")

        val s = data.stats
        val active = (s.total - s.errors).coerceAtLeast(0)
        val pct = if (active > 0) (s.found * 1000 / active) / 10.0 else 0.0

        sb.append("<div class=\"summary\">")
        sb.append(summaryChip("Всего", s.total, null))
        sb.append(summaryChip("Найдено", s.found, "#2E7D32"))
        sb.append(summaryChip("Не найдено", s.notFound, "#C62828"))
        sb.append(summaryChip("Холостые", s.blanks, null))
        sb.append(summaryChip("ВК", s.weightControls, null))
        sb.append(summaryChip("Отложено", s.postponed, "#1976D2"))
        if (s.errors > 0) sb.append(summaryChip("Ошибки", s.errors, "#C62828"))
        sb.append("</div>")

        sb.append("<div class=\"progress-line\">")
        sb.append("<div class=\"progress-bar\"><div class=\"progress-fill\" style=\"width:")
            .append(pct.coerceIn(0.0, 100.0)).append("%\"></div></div>")
        sb.append("<span class=\"progress-pct\">").append(pct).append("%</span>")
        sb.append("</div>")

        sb.append("</header>")
        return sb.toString()
    }

    private fun summaryChip(label: String, value: Int, color: String?): String {
        val style = if (color != null) " style=\"color:$color\"" else ""
        return "<span class=\"chip\"><b$style>$value</b> $label</span>"
    }

    private fun buildFooter(): String =
        "<footer class=\"footer no-print\">Сгенерировано GeoSample Manager</footer>"

    // ================================================================
    // ТАБЛИЦА ПРОБ
    // ================================================================

    private fun buildTable(data: ReportData): String {
        val sb = StringBuilder(8 * 1024)
        sb.append("<section class=\"samples\">")
        sb.append("<table class=\"samples-table\">")
        sb.append("<thead><tr>")
        sb.append("<th>✓</th>")
        sb.append("<th>п/п</th>")
        sb.append("<th>№ пробы</th>")
        sb.append("<th>Скважина</th>")
        sb.append("<th>Интервал</th>")
        sb.append("<th>Вес</th>")
        sb.append("<th>Характеристика</th>")
        sb.append("<th>Тип</th>")
        sb.append("<th>Прил.</th>")
        sb.append("</tr></thead><tbody>")

        var appendixIndex = 0
        data.samples.forEach { sample ->
            val row = sample.row
            val appendixNum = if (sample.hasAppendix) ++appendixIndex else 0
            val rowClass = rowCssClass(row)

            sb.append("<tr class=\"").append(rowClass).append("\">")
            sb.append("<td class=\"c-check\">").append(if (row.found) "✓" else "").append("</td>")
            sb.append("<td class=\"c-num\">")
                .append(if (row.serialNumber > 0) row.serialNumber else "—")
                .append("</td>")
            sb.append("<td class=\"c-sample\" id=\"sample-").append(safeId(row.id)).append("\">")
                .append(esc(row.sampleNumber)).append("</td>")
            sb.append("<td>").append(esc(row.wellNumber)).append("</td>")
            sb.append("<td class=\"c-int\">").append(esc(buildInterval(row))).append("</td>")
            sb.append("<td class=\"c-weight\">").append(esc(buildWeight(row))).append("</td>")
            sb.append("<td class=\"c-char\">").append(esc(row.characteristic)).append("</td>")
            sb.append("<td>").append(esc(displayType(row))).append("</td>")

            if (sample.hasAppendix) {
                sb.append("<td class=\"c-link\">")
                sb.append("<a href=\"#appendix-").append(safeId(row.id)).append("\">Прил. ")
                    .append(appendixNum).append("</a>")
                sb.append("</td>")
            } else {
                sb.append("<td class=\"c-link muted\">—</td>")
            }
            sb.append("</tr>")
        }

        sb.append("</tbody></table>")
        sb.append("</section>")
        return sb.toString()
    }

    // ================================================================
    // ПРИЛОЖЕНИЯ
    // ================================================================

    private fun buildAppendices(data: ReportData): String {
        val withAppendix = data.samples.filter { it.hasAppendix }
        if (withAppendix.isEmpty()) return ""

        val sb = StringBuilder(32 * 1024)
        sb.append("<section class=\"appendices\" id=\"appendices\">")
        sb.append("<h2>Приложения</h2>")

        withAppendix.forEachIndexed { idx, sample ->
            val row = sample.row
            val num = idx + 1

            sb.append("<div class=\"appendix\" id=\"appendix-").append(safeId(row.id)).append("\">")
            sb.append("<h3>Приложение ").append(num).append(" — ")
                .append(esc(row.sampleNumber)).append("</h3>")

            sb.append("<div class=\"appendix-meta\">")
            sb.append("Скважина: <b>").append(esc(row.wellNumber)).append("</b>")
            sb.append(" · Тип: <b>").append(esc(displayType(row))).append("</b>")
            if (row.characteristic != "—") {
                sb.append(" · Характеристика: <b>").append(esc(row.characteristic)).append("</b>")
            }
            sb.append("</div>")

            sample.note?.let { note ->
                sb.append("<div class=\"note\">")
                sb.append("<div class=\"note-title\">Заметка</div>")
                sb.append("<div class=\"note-text\">").append(escMultiline(note.text)).append("</div>")
                sb.append("</div>")
            }

            if (sample.photos.isNotEmpty()) {
                sb.append("<div class=\"photos\">")
                sample.photos.forEachIndexed { pIdx, photo ->
                    sb.append("<figure class=\"photo\">")
                    sb.append("<img src=\"").append(photo.dataUri).append("\" alt=\"Фото ")
                        .append(pIdx + 1).append("\">")
                    sb.append("<figcaption>Фото ").append(pIdx + 1)
                        .append(" из ").append(sample.photos.size).append("</figcaption>")
                    sb.append("</figure>")
                }
                sb.append("</div>")
            }

            sb.append("<div class=\"back\">")
            sb.append("<a href=\"#sample-").append(safeId(row.id)).append("\">")
                .append("↩ К пробе ").append(esc(row.sampleNumber))
                .append("</a>")
            sb.append("</div>")

            sb.append("</div>")
        }

        sb.append("</section>")
        return sb.toString()
    }

    // ================================================================
    // УТИЛИТЫ
    // ================================================================

    private fun rowCssClass(row: SampleRow): String = when {
        row.found -> "row-found"
        row.hasImportError -> "row-error"
        row.postponed -> "row-postponed"
        row.isBlank -> "row-blank"
        row.weightControl -> "row-control"
        else -> ""
    }

    private fun buildInterval(row: SampleRow): String {
        val from = row.intervalFrom
        val to = row.intervalTo
        return if (from == "—" && to == "—") "—" else "$from–$to"
    }

    private fun buildWeight(row: SampleRow): String {
        val w = row.weight
        val cw = row.controlWeight
        return when {
            w != null && cw != null -> "$w ($cw)"
            w != null -> w.toString()
            cw != null -> "($cw)"
            else -> "—"
        }
    }

    private fun displayType(row: SampleRow): String = when (row.status) {
        com.example.geosamplemanager.ui.screens.SampleStatus.BLANK -> "Холостая"
        com.example.geosamplemanager.ui.screens.SampleStatus.CONTROL -> "Весовой контроль"
        com.example.geosamplemanager.ui.screens.SampleStatus.NORMAL -> row.type.title
    }

    private fun esc(s: String): String {
        if (s.isEmpty()) return s
        val sb = StringBuilder(s.length + 8)
        s.forEach { c ->
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** Экранирование с сохранением переносов строк (для заметок). */
    private fun escMultiline(s: String): String =
        esc(s).replace("\n", "<br>")

    private fun safeId(id: String): String =
        id.filter { it.isLetterOrDigit() || it == '_' || it == '-' }

    // ================================================================
    // CSS
    // ================================================================

    private val CSS: String = """
        * { box-sizing: border-box; }
        body { font-family: -apple-system, "Segoe UI", Roboto, Arial, sans-serif;
               margin: 0; padding: 0; background: #f5f5f5; color: #222; }
        .toolbar { position: sticky; top: 0; background: #1976D2; padding: 10px 16px;
                   text-align: right; z-index: 100; }
        .toolbar button { background: #fff; color: #1976D2; border: none;
                          padding: 8px 16px; font-size: 14px; cursor: pointer;
                          border-radius: 4px; font-weight: bold; }
        .toolbar button:hover { background: #e3f2fd; }
        .header { background: #fff; padding: 20px 24px; margin-bottom: 16px; }
        .header h1 { margin: 0 0 12px 0; font-size: 22px; }
        .meta { display: flex; flex-wrap: wrap; gap: 6px 24px; margin-bottom: 12px;
                font-size: 14px; color: #444; }
        .summary { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
        .chip { background: #eee; padding: 4px 10px; border-radius: 4px;
                font-size: 13px; }
        .chip b { margin-right: 4px; }
        .progress-line { display: flex; align-items: center; gap: 10px; }
        .progress-bar { flex: 1; height: 8px; background: #eee;
                        border-radius: 4px; overflow: hidden; }
        .progress-fill { height: 100%; background: #2E7D32; transition: width .3s; }
        .progress-pct { font-size: 13px; font-weight: bold; }
        .samples { background: #fff; padding: 0 0 16px 0; margin-bottom: 16px; }
        .samples-table { width: 100%; border-collapse: collapse; font-size: 13px; }
        .samples-table th { background: #f0f0f0; padding: 8px 6px; text-align: left;
                            font-weight: bold; border-bottom: 2px solid #ccc; }
        .samples-table td { padding: 6px; border-bottom: 1px solid #e0e0e0;
                            vertical-align: top; }
        .samples-table tr.row-found { background: #A5D6A7; }
        .samples-table tr.row-blank { background: #FFF59D; }
        .samples-table tr.row-control { background: #CE93D8; }
        .samples-table tr.row-postponed { background: #90CAF9; }
        .samples-table tr.row-error { background: #EF9A9A; }
        .c-check, .c-num, .c-int, .c-weight { width: 1%; white-space: nowrap; }
        .c-num { text-align: right; }
        .c-sample { font-weight: bold; }
        .c-link { white-space: nowrap; }
        .c-link.muted { color: #999; }
        .c-char { max-width: 200px; }
        .appendices { background: #fff; padding: 20px 24px; }
        .appendices h2 { margin-top: 0; font-size: 20px;
                         border-bottom: 2px solid #ccc; padding-bottom: 6px; }
        .appendix { margin-bottom: 32px; padding: 16px; border: 1px solid #ddd;
                    border-radius: 6px; background: #fafafa; }
        .appendix h3 { margin-top: 0; font-size: 16px; color: #1976D2; }
        .appendix-meta { font-size: 13px; color: #555; margin-bottom: 12px; }
        .note { background: #fffbe6; border-left: 4px solid #F9A825;
                padding: 10px 14px; margin-bottom: 12px; border-radius: 4px; }
        .note-title { font-weight: bold; font-size: 13px; color: #7c5e00;
                      margin-bottom: 4px; }
        .note-text { font-size: 14px; white-space: pre-wrap; }
        .photos { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 12px; }
        .photo { margin: 0; }
        .photo img { max-width: 320px; max-height: 320px; display: block;
                     border-radius: 4px; border: 1px solid #ccc; }
        .photo figcaption { font-size: 11px; color: #777; margin-top: 4px;
                            text-align: center; }
        .back { text-align: right; font-size: 13px; }
        .back a { color: #1976D2; text-decoration: none; }
        .back a:hover { text-decoration: underline; }
        .footer { text-align: center; font-size: 12px; color: #999;
                  padding: 20px; }
        @media print {
            body { background: #fff; }
            .no-print { display: none !important; }
            .header, .samples, .appendices { margin: 0; padding: 12px; }
            .appendix { page-break-inside: avoid; }
            .photo img { max-width: 240px; max-height: 240px; }
        }
    """.trimIndent()
}
