package com.example.geosamplemanager.data.report

import com.example.geosamplemanager.ui.screens.SampleRow
import com.example.geosamplemanager.ui.screens.SampleStatus

/**
 * FIX 5.9-report-html-multi / подзаход 5 (html-multi):
 * Мультинарядный HTML-отчёт. Один самодостаточный .html со всеми нарядами.
 *
 * Структура:
 *  1. Титульная секция (#toc):
 *     - заголовок, дата, общее число нарядов;
 *     - общая сводка по всем нарядам (сумма GroupStats);
 *     - список нарядов со ссылками на секции.
 *  2. N секций нарядов (#order-{idx}):
 *     - шапка (участок, №, дата);
 *     - сводка по наряду + прогресс-бар;
 *     - таблица проб с ссылками «Прил. N».
 *  3. Общий блок приложений (#appendices):
 *     - блоки всех нарядов по порядку;
 *     - сквозная нумерация «Приложение 1, 2, 3, ...»;
 *     - обратные ссылки «↩ К пробе X» в нужную секцию наряда.
 *
 * Якоря: sample-{orderIdx}-{rowId}, appendix-{orderIdx}-{rowId}.
 * Префикс orderIdx защищает от коллизий при совпадении rowId.
 */
object MultiHtmlReportGenerator {

    fun generate(orders: List<ReportData>, generatedAt: String): String {
        if (orders.isEmpty()) return emptyHtml(generatedAt)

        val numbers = buildAppendixNumbers(orders)

        val sb = StringBuilder(256 * 1024)
        sb.append("<!DOCTYPE html>\n")
        sb.append("<html lang=\"ru\"><head>")
        sb.append("<meta charset=\"UTF-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        sb.append("<title>Отчёты · ").append(orders.size).append(" наряд(ов)</title>")
        sb.append("<style>").append(CSS).append("</style>")
        sb.append("</head><body>")

        sb.append(buildToolbar())
        sb.append(buildToc(orders, generatedAt))
        orders.forEachIndexed { idx, data ->
            sb.append(buildOrderSection(data, idx, numbers))
        }
        sb.append(buildAppendices(orders, numbers))
        sb.append(buildFooter())

        sb.append("</body></html>")
        return sb.toString()
    }

    // ================================================================
    // ТИТУЛЬНАЯ СЕКЦИЯ
    // ================================================================

    private fun buildToc(orders: List<ReportData>, generatedAt: String): String {
        val sb = StringBuilder(4 * 1024)
        sb.append("<section class=\"toc\" id=\"toc\">")
        sb.append("<h1>Отчёты по нарядам</h1>")
        sb.append("<div class=\"toc-meta\">")
        sb.append("<div><b>Дата:</b> ").append(esc(generatedAt)).append("</div>")
        sb.append("<div><b>Нарядов:</b> ").append(orders.size).append("</div>")
        sb.append("</div>")

        val total = orders.sumOf { it.stats.total }
        val found = orders.sumOf { it.stats.found }
        val notFound = orders.sumOf { it.stats.notFound }
        val blanks = orders.sumOf { it.stats.blanks }
        val weightControls = orders.sumOf { it.stats.weightControls }
        val postponed = orders.sumOf { it.stats.postponed }
        val errors = orders.sumOf { it.stats.errors }
        val active = (total - errors).coerceAtLeast(0)
        val pct = if (active > 0) (found * 1000 / active) / 10.0 else 0.0

        sb.append("<div class=\"summary\">")
        sb.append(summaryChip("Всего", total, null))
        sb.append(summaryChip("Найдено", found, "#2E7D32"))
        sb.append(summaryChip("Не найдено", notFound, "#C62828"))
        sb.append(summaryChip("Холостые", blanks, null))
        sb.append(summaryChip("ВК", weightControls, null))
        sb.append(summaryChip("Отложено", postponed, "#1976D2"))
        if (errors > 0) sb.append(summaryChip("Ошибки", errors, "#C62828"))
        sb.append("</div>")

        sb.append("<div class=\"progress-line\">")
        sb.append("<div class=\"progress-bar\"><div class=\"progress-fill\" style=\"width:")
            .append(pct.coerceIn(0.0, 100.0)).append("%\"></div></div>")
        sb.append("<span class=\"progress-pct\">").append(pct).append("%</span>")
        sb.append("</div>")

        sb.append("<h2 class=\"toc-h2\">Наряды</h2>")
        sb.append("<ol class=\"toc-list\">")
        orders.forEachIndexed { idx, data ->
            sb.append("<li><a href=\"#order-").append(idx).append("\">")
                .append(esc(buildOrderTitle(data))).append("</a></li>")
        }
        sb.append("</ol>")
        sb.append("</section>")
        return sb.toString()
    }

    // ================================================================
    // СЕКЦИЯ НАРЯДА
    // ================================================================

    private fun buildOrderSection(
        data: ReportData,
        orderIdx: Int,
        numbers: Map<Pair<Int, String>, Int>
    ): String {
        val sb = StringBuilder(16 * 1024)
        sb.append("<section class=\"order-section\" id=\"order-").append(orderIdx).append("\">")
        sb.append("<div class=\"order-back\"><a href=\"#toc\">↑ К содержанию</a></div>")
        sb.append("<h2 class=\"order-title\">")
            .append(esc(buildOrderTitle(data))).append("</h2>")

        sb.append("<div class=\"order-header\">")
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
        sb.append("</div>")

        sb.append(buildOrderTable(data, orderIdx, numbers))
        sb.append("</section>")
        return sb.toString()
    }

    private fun buildOrderTable(
        data: ReportData,
        orderIdx: Int,
        numbers: Map<Pair<Int, String>, Int>
    ): String {
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

        data.samples.forEach { sample ->
            val row = sample.row
            val rowClass = rowCssClass(row)

            sb.append("<tr class=\"").append(rowClass).append("\">")
            sb.append("<td class=\"c-check\">")
                .append(if (row.found) "✓" else "").append("</td>")
            sb.append("<td class=\"c-num\">")
                .append(if (row.serialNumber > 0) row.serialNumber else "—")
                .append("</td>")
            sb.append("<td class=\"c-sample\" id=\"sample-")
                .append(orderIdx).append("-").append(safeId(row.id)).append("\">")
                .append(esc(row.sampleNumber)).append("</td>")
            sb.append("<td>").append(esc(row.wellNumber)).append("</td>")
            sb.append("<td class=\"c-int\">").append(esc(buildInterval(row))).append("</td>")
            sb.append("<td class=\"c-weight\">").append(esc(buildWeight(row))).append("</td>")
            sb.append("<td class=\"c-char\">").append(esc(row.characteristic)).append("</td>")
            sb.append("<td>").append(esc(displayType(row))).append("</td>")

            if (sample.hasAppendix) {
                val n = numbers[orderIdx to row.id] ?: 0
                sb.append("<td class=\"c-link\">")
                sb.append("<a href=\"#appendix-")
                    .append(orderIdx).append("-").append(safeId(row.id)).append("\">")
                    .append("Прил. ").append(n).append("</a>")
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
    // ОБЩИЙ БЛОК ПРИЛОЖЕНИЙ
    // ================================================================

    private fun buildAppendices(
        orders: List<ReportData>,
        numbers: Map<Pair<Int, String>, Int>
    ): String {
        val hasAny = orders.any { data -> data.samples.any { it.hasAppendix } }
        if (!hasAny) return ""

        val sb = StringBuilder(32 * 1024)
        sb.append("<section class=\"appendices\" id=\"appendices\">")
        sb.append("<h2>Приложения</h2>")

        orders.forEachIndexed { orderIdx, data ->
            val withAppendix = data.samples.filter { it.hasAppendix }
            if (withAppendix.isEmpty()) return@forEachIndexed

            sb.append("<h3 class=\"app-order-title\">")
                .append(esc(buildOrderTitle(data)))
                .append("</h3>")

            withAppendix.forEach { sample ->
                val row = sample.row
                val num = numbers[orderIdx to row.id] ?: 0

                sb.append("<div class=\"appendix\" id=\"appendix-")
                    .append(orderIdx).append("-").append(safeId(row.id)).append("\">")
                sb.append("<h4>Приложение ").append(num).append(" — ")
                    .append(esc(row.sampleNumber)).append("</h4>")

                sb.append("<div class=\"appendix-meta\">")
                sb.append("Скважина: <b>").append(esc(row.wellNumber)).append("</b>")
                sb.append(" · Тип: <b>").append(esc(displayType(row))).append("</b>")
                if (row.characteristic != "—") {
                    sb.append(" · Характеристика: <b>")
                        .append(esc(row.characteristic)).append("</b>")
                }
                sb.append("</div>")

                sample.note?.let { note ->
                    sb.append("<div class=\"note\">")
                    sb.append("<div class=\"note-title\">Заметка</div>")
                    sb.append("<div class=\"note-text\">")
                        .append(escMultiline(note.text)).append("</div>")
                    sb.append("</div>")
                }

                if (sample.photos.isNotEmpty()) {
                    sb.append("<div class=\"photos\">")
                    sample.photos.forEachIndexed { pIdx, photo ->
                        sb.append("<figure class=\"photo\">")
                        sb.append("<img src=\"").append(photo.dataUri)
                            .append("\" alt=\"Фото ").append(pIdx + 1).append("\">")
                        sb.append("<figcaption>Фото ").append(pIdx + 1)
                            .append(" из ").append(sample.photos.size).append("</figcaption>")
                        sb.append("</figure>")
                    }
                    sb.append("</div>")
                }

                sb.append("<div class=\"back\">")
                sb.append("<a href=\"#sample-")
                    .append(orderIdx).append("-").append(safeId(row.id)).append("\">")
                    .append("↩ К пробе ").append(esc(row.sampleNumber))
                    .append("</a>")
                sb.append("</div>")

                sb.append("</div>")
            }
        }

        sb.append("</section>")
        return sb.toString()
    }

    // ================================================================
    // НУМЕРАЦИЯ ПРИЛОЖЕНИЙ
    // ================================================================

    /** Карта: (orderIdx, sampleId) → сквозной номер приложения. */
    private fun buildAppendixNumbers(orders: List<ReportData>): Map<Pair<Int, String>, Int> {
        val result = mutableMapOf<Pair<Int, String>, Int>()
        var n = 1
        orders.forEachIndexed { orderIdx, data ->
            data.samples.forEach { sample ->
                if (sample.hasAppendix) {
                    result[orderIdx to sample.row.id] = n
                    n++
                }
            }
        }
        return result
    }

    // ================================================================
    // ОБЩИЕ БЛОКИ
    // ================================================================

    private fun buildToolbar(): String =
        "<div class=\"toolbar no-print\">" +
                "<button onclick=\"window.print()\">" +
                "🖨 Сохранить PDF / Печать</button>" +
                "</div>"

    private fun buildFooter(): String =
        "<footer class=\"footer no-print\">Сгенерировано GeoSample Manager</footer>"

    private fun emptyHtml(generatedAt: String): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html>\n")
        sb.append("<html lang=\"ru\"><head>")
        sb.append("<meta charset=\"UTF-8\">")
        sb.append("<title>Отчёты · 0 нарядов</title>")
        sb.append("<style>").append(CSS).append("</style>")
        sb.append("</head><body>")
        sb.append("<section class=\"toc\">")
        sb.append("<h1>Отчёты по нарядам</h1>")
        sb.append("<div class=\"toc-meta\">")
        sb.append("<div><b>Дата:</b> ").append(esc(generatedAt)).append("</div>")
        sb.append("<div><b>Нарядов:</b> 0</div>")
        sb.append("</div>")
        sb.append("<p>Нет нарядов для отчёта.</p>")
        sb.append("</section>")
        sb.append("</body></html>")
        return sb.toString()
    }

    // ================================================================
    // УТИЛИТЫ
    // ================================================================

    private fun buildOrderTitle(data: ReportData): String =
        if (data.areaName.isBlank()) {
            "Наряд №${data.orderNumber}"
        } else {
            "${data.areaName} — Наряд №${data.orderNumber}"
        }

    private fun summaryChip(label: String, value: Int, color: String?): String {
        val style = if (color != null) " style=\"color:$color\"" else ""
        return "<span class=\"chip\"><b$style>$value</b> $label</span>"
    }

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
        SampleStatus.BLANK -> "Холостая"
        SampleStatus.CONTROL -> "Весовой контроль"
        SampleStatus.NORMAL -> row.type.title
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

        .toc { background: #fff; padding: 24px; margin-bottom: 16px; }
        .toc h1 { margin: 0 0 12px 0; font-size: 24px; }
        .toc-meta { display: flex; flex-wrap: wrap; gap: 6px 24px;
                    margin-bottom: 12px; font-size: 14px; color: #444; }
        .toc-h2 { margin: 20px 0 8px 0; font-size: 18px;
                  border-bottom: 2px solid #ccc; padding-bottom: 6px; }
        .toc-list { margin: 0; padding-left: 22px; }
        .toc-list li { margin: 4px 0; font-size: 15px; }
        .toc-list a { color: #1976D2; text-decoration: none; }
        .toc-list a:hover { text-decoration: underline; }

        .order-section { background: #fff; padding: 20px 24px; margin-bottom: 16px; }
        .order-back { text-align: right; font-size: 12px; margin-bottom: 6px; }
        .order-back a { color: #1976D2; text-decoration: none; }
        .order-back a:hover { text-decoration: underline; }
        .order-title { margin: 0 0 12px 0; font-size: 20px;
                       border-bottom: 2px solid #ccc; padding-bottom: 6px; }
        .order-header { margin-bottom: 12px; }
        .meta { display: flex; flex-wrap: wrap; gap: 6px 24px; margin-bottom: 12px;
                font-size: 14px; color: #444; }
        .summary { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
        .chip { background: #eee; padding: 4px 10px; border-radius: 4px; font-size: 13px; }
        .chip b { margin-right: 4px; }
        .progress-line { display: flex; align-items: center; gap: 10px; }
        .progress-bar { flex: 1; height: 8px; background: #eee;
                        border-radius: 4px; overflow: hidden; }
        .progress-fill { height: 100%; background: #2E7D32; transition: width .3s; }
        .progress-pct { font-size: 13px; font-weight: bold; }

        .samples { padding: 0; }
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
        .app-order-title { margin: 20px 0 10px 0; font-size: 16px; color: #555;
                           background: #f0f0f0; padding: 6px 10px;
                           border-radius: 4px; }
        .appendix { margin-bottom: 24px; padding: 16px; border: 1px solid #ddd;
                    border-radius: 6px; background: #fafafa; }
        .appendix h4 { margin-top: 0; font-size: 16px; color: #1976D2; }
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
        .footer { text-align: center; font-size: 12px; color: #999; padding: 20px; }

        @media print {
            body { background: #fff; }
            .no-print { display: none !important; }
            .toc, .order-section, .appendices { margin: 0; padding: 12px;
                page-break-after: always; }
            .appendices { page-break-after: auto; }
            .appendix { page-break-inside: avoid; }
            .photo img { max-width: 240px; max-height: 240px; }
        }
    """.trimIndent()
}