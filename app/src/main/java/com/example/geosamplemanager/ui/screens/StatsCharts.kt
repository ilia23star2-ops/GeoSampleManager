package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * FIX 5.9-stats-charts-3 (финал):
 *  - 5 категорий с приоритетом found > postponed > control > blank > not_found.
 *  - Круг: цифры на секторах (≥25°), без числа в центре.
 *  - Столбцы: цифра внутри полоски.
 *  - По скважинам: сегментированный бар + легенда X/Y.
 *  - DrillLevel — уровни drill-down.
 *  - filterRowsByDrillStack — применение стека.
 */

// ====================================================================
// МОДЕЛИ
// ====================================================================

enum class CategoryKey { FOUND, NOT_FOUND, POSTPONED, BLANK, CONTROL }
enum class SubKey { NORMAL, BLANK, CONTROL, POSTPONED }

sealed class DrillLevel {
    data object Root : DrillLevel()
    data class Category(val kind: CategoryKey) : DrillLevel()
    data class SubCategory(val sub: SubKey) : DrillLevel()
    data class Well(val well: String) : DrillLevel()
}

/** Порядок отображения в круге и столбцах. */
val CATEGORY_ORDER = listOf(
    CategoryKey.FOUND,
    CategoryKey.NOT_FOUND,
    CategoryKey.POSTPONED,
    CategoryKey.BLANK,
    CategoryKey.CONTROL
)

fun categoryLabel(key: CategoryKey): String = when (key) {
    CategoryKey.FOUND -> "Найдено"
    CategoryKey.NOT_FOUND -> "Не найдено"
    CategoryKey.POSTPONED -> "Отложено"
    CategoryKey.BLANK -> "Холостые"
    CategoryKey.CONTROL -> "ВК"
}

fun categoryColor(key: CategoryKey): Color = when (key) {
    CategoryKey.FOUND -> Color(0xFF2E7D32)
    CategoryKey.NOT_FOUND -> Color(0xFFC62828)
    CategoryKey.POSTPONED -> Color(0xFF1976D2)
    CategoryKey.BLANK -> Color(0xFFF9A825)
    CategoryKey.CONTROL -> Color(0xFF7B1FA2)
}

fun subLabel(sub: SubKey): String = when (sub) {
    SubKey.POSTPONED -> "Отложено"
    SubKey.CONTROL -> "ВК"
    SubKey.BLANK -> "Холостые"
    SubKey.NORMAL -> "Обычные"
}

fun subColor(sub: SubKey): Color = when (sub) {
    SubKey.NORMAL -> Color(0xFF546E7A)
    SubKey.BLANK -> Color(0xFFF9A825)
    SubKey.CONTROL -> Color(0xFF7B1FA2)
    SubKey.POSTPONED -> Color(0xFF1976D2)
}

fun subOrder(): List<SubKey> = listOf(
    SubKey.NORMAL, SubKey.BLANK, SubKey.CONTROL, SubKey.POSTPONED
)

/**
 * Приоритет отнесения пробы к категории.
 * Первое совпадение выигрывает. Проба попадает ровно в одну категорию.
 */
fun categoryOf(row: SampleRow): CategoryKey = when {
    row.found -> CategoryKey.FOUND
    row.postponed -> CategoryKey.POSTPONED
    row.weightControl -> CategoryKey.CONTROL
    row.isBlank -> CategoryKey.BLANK
    else -> CategoryKey.NOT_FOUND
}

/**
 * Приоритет подкатегории внутри уже отфильтрованного набора.
 */
fun subOf(row: SampleRow): SubKey = when {
    row.postponed -> SubKey.POSTPONED
    row.weightControl -> SubKey.CONTROL
    row.isBlank -> SubKey.BLANK
    else -> SubKey.NORMAL
}

// ====================================================================
// ПРИМЕНЕНИЕ СТЕКА DRILL-DOWN
// ====================================================================

fun filterRowsByDrillStack(
    rows: List<SampleRow>,
    stack: List<DrillLevel>
): List<SampleRow> {
    var result = rows
    for (level in stack) {
        result = when (level) {
            is DrillLevel.Root -> result
            is DrillLevel.Category -> result.filter { categoryOf(it) == level.kind }
            is DrillLevel.SubCategory -> result.filter { subOf(it) == level.sub }
            is DrillLevel.Well -> result.filter { it.wellNumber == level.well }
        }
    }
    return result
}

// ====================================================================
// ДАННЫЕ ДЛЯ КРУГА / СТОЛБЦОВ
// ====================================================================

data class CategoryDatum(
    val key: CategoryKey,
    val label: String,
    val value: Int,
    val color: Color,
    val payload: String
)

fun computeCategoryData(rows: List<SampleRow>): List<CategoryDatum> {
    val counts = IntArray(CategoryKey.values().size)
    rows.forEach { r -> counts[categoryOf(r).ordinal]++ }

    return CATEGORY_ORDER.mapNotNull { key ->
        val v = counts[key.ordinal]
        if (v <= 0) null
        else CategoryDatum(
            key = key,
            label = categoryLabel(key),
            value = v,
            color = categoryColor(key),
            payload = key.name
        )
    }
}

fun computeSubCategoryData(rows: List<SampleRow>): List<CategoryDatum> {
    val counts = IntArray(SubKey.values().size)
    rows.forEach { r -> counts[subOf(r).ordinal]++ }

    return subOrder().mapNotNull { sub ->
        val v = counts[sub.ordinal]
        if (v <= 0) null
        else CategoryDatum(
            key = when (sub) {
                SubKey.POSTPONED -> CategoryKey.POSTPONED
                SubKey.CONTROL -> CategoryKey.CONTROL
                SubKey.BLANK -> CategoryKey.BLANK
                SubKey.NORMAL -> CategoryKey.NOT_FOUND
            },
            label = subLabel(sub),
            value = v,
            color = subColor(sub),
            payload = sub.name
        )
    }
}

// ====================================================================
// ДАННЫЕ ДЛЯ СКВАЖИН
// ====================================================================

data class WellBarSegment(val key: CategoryKey, val color: Color, val value: Int)

data class WellStats(
    val wellNumber: String,
    val total: Int,
    val found: Int,
    val notFound: Int,
    val postponed: Int,
    val postponedFound: Int,
    val blank: Int,
    val blankFound: Int,
    val control: Int,
    val controlFound: Int,
    val segments: List<WellBarSegment>
)

fun computeWellStats(rows: List<SampleRow>): List<WellStats> {
    return rows
        .groupBy { it.wellNumber }
        .map { (well, list) -> statsFor(well, list) }
        .sortedBy { it.wellNumber }
}

private fun statsFor(well: String, list: List<SampleRow>): WellStats {
    val total = list.size
    val found = list.count { it.found }
    val postponedRows = list.filter { it.postponed }
    val blankRows = list.filter { it.isBlank }
    val controlRows = list.filter { it.weightControl }

    val segCounts = IntArray(CategoryKey.values().size)
    list.forEach { r -> segCounts[categoryOf(r).ordinal]++ }
    val segments = CATEGORY_ORDER.mapNotNull { key ->
        val v = segCounts[key.ordinal]
        if (v <= 0) null
        else WellBarSegment(key, categoryColor(key), v)
    }

    return WellStats(
        wellNumber = well,
        total = total,
        found = found,
        notFound = total - found,
        postponed = postponedRows.size,
        postponedFound = postponedRows.count { it.found },
        blank = blankRows.size,
        blankFound = blankRows.count { it.found },
        control = controlRows.size,
        controlFound = controlRows.count { it.found },
        segments = segments
    )
}

// ====================================================================
// КРУГ
// ====================================================================

@Composable
fun PieChart(
    data: List<CategoryDatum>,
    onSliceClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val total = data.sumOf { it.value }
    if (total == 0) { EmptyChart("Нет данных", modifier); return }

    val textMeasurer = rememberTextMeasurer()

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(240.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(210.dp)
                    .pointerInput(data) {
                        detectTapGestures { tap ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = tap.x - cx
                            val dy = tap.y - cy
                            val radius = size.width / 2f
                            if (sqrt(dx * dx + dy * dy) > radius) return@detectTapGestures
                            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                            angle = (angle + 90 + 360) % 360
                            var acc = 0f
                            data.forEach { slice ->
                                val sweep = 360f * slice.value / total
                                if (angle >= acc && angle < acc + sweep) {
                                    onSliceClick(slice.payload)
                                    return@detectTapGestures
                                }
                                acc += sweep
                            }
                        }
                    }
            ) {
                val diameter = size.minDimension
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                val cx = size.width / 2f
                val cy = size.height / 2f

                var startAngle = -90f
                data.forEach { slice ->
                    val sweep = 360f * slice.value / total
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = topLeft,
                        size = arcSize
                    )

                    if (sweep >= 25f) {
                        val mid = Math.toRadians((startAngle + sweep / 2).toDouble())
                        val r = (diameter / 2f) * 0.62f
                        val tx = cx + r * cos(mid).toFloat()
                        val ty = cy + r * sin(mid).toFloat()
                        val layout = textMeasurer.measure(
                            text = slice.value.toString(),
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(
                                tx - layout.size.width / 2f,
                                ty - layout.size.height / 2f
                            )
                        )
                    }
                    startAngle += sweep
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ChartLegend(data, total, onSliceClick)
    }
}

@Composable
private fun ChartLegend(
    data: List<CategoryDatum>,
    total: Int,
    onClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        data.forEach { datum ->
            val pct = if (total > 0) datum.value * 1000 / total / 10.0 else 0.0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onClick(datum.payload) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(datum.color)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    datum.label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    datum.value.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "$pct%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(58.dp)
                )
            }
        }
    }
}

// ====================================================================
// СТОЛБЦЫ
// ====================================================================

@Composable
fun BarChart(
    data: List<CategoryDatum>,
    onBarClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val total = data.sumOf { it.value }
    val maxValue = data.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    if (total == 0) { EmptyChart("Нет данных", modifier); return }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        data.forEach { datum ->
            val pct = if (total > 0) datum.value * 1000 / total / 10.0 else 0.0
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onBarClick(datum.payload) }
                    .padding(vertical = 6.dp, horizontal = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        datum.label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${datum.value} · $pct%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    if (datum.value > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(datum.value.toFloat() / maxValue)
                                .background(datum.color),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                datum.value.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================
// ПО СКВАЖИНАМ
// ====================================================================

@Composable
fun WellProgressChart(
    items: List<WellStats>,
    filter: String,
    onWellClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val filtered = remember(items, filter) {
        val q = filter.trim().lowercase()
        if (q.isEmpty()) items
        else items.filter { it.wellNumber.lowercase().contains(q) }
    }

    if (items.isEmpty()) { EmptyChart("Нет скважин", modifier); return }

    Column(modifier = modifier.fillMaxWidth()) {
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Ничего не найдено по «$filter»",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                filtered.forEach { wp -> WellRow(wp, onWellClick) }
            }
        }
    }
}

@Composable
private fun WellRow(wp: WellStats, onClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick(wp.wellNumber) }
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                wp.wellNumber,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${wp.found} / ${wp.total}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            wp.segments.forEach { seg ->
                if (seg.value > 0) {
                    Box(
                        modifier = Modifier
                            .weight(seg.value.toFloat())
                            .fillMaxHeight()
                            .background(seg.color)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = buildWellLegend(wp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}

private fun buildWellLegend(wp: WellStats): String {
    val parts = mutableListOf<String>()
    parts.add("Найдено ${wp.found}/${wp.total}")
    parts.add("Не найдено ${wp.notFound}/${wp.total}")
    if (wp.control > 0) parts.add("ВК ${wp.controlFound}/${wp.control}")
    if (wp.blank > 0) parts.add("Холостые ${wp.blankFound}/${wp.blank}")
    if (wp.postponed > 0) parts.add("Отложено ${wp.postponedFound}/${wp.postponed}")
    return parts.joinToString(" · ")
}

// ====================================================================
// УТИЛИТЫ
// ====================================================================

@Composable
private fun EmptyChart(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
