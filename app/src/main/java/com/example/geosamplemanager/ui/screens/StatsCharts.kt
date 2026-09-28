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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * FIX 5.9-stats-charts-2:
 * Единый срез для круга и столбцов. Drill-down идёт через payload.
 * Скважины — отдельный срез с фильтром и двухцветной полоской.
 */

enum class SliceKind { FOUND, NOT_FOUND, POSTPONED, ERRORS }
enum class SubKind { NORMAL, BLANK, CONTROL }

sealed class DrillLevel {
    data object Root : DrillLevel()
    data class Category(val kind: SliceKind) : DrillLevel()
    data class SubCategory(val cat: SliceKind, val sub: SubKind) : DrillLevel()
    data class Well(val cat: SliceKind, val sub: SubKind?, val well: String) : DrillLevel()
}

data class ChartDatum(
    val label: String,
    val value: Int,
    val color: Color,
    val payload: String
)

data class WellProgress(
    val wellNumber: String,
    val found: Int,
    val total: Int
)

val COLOR_FOUND = Color(0xFF2E7D32)
val COLOR_NOT_FOUND = Color(0xFFC62828)
val COLOR_POSTPONED = Color(0xFF1976D2)
val COLOR_ERRORS = Color(0xFFB71C1C)
val COLOR_NORMAL = Color(0xFF1976D2)
val COLOR_BLANK = Color(0xFFF9A825)
val COLOR_CONTROL = Color(0xFF7B1FA2)

// ====================================================================
// ВЫЧИСЛЕНИЕ ДАННЫХ
// ====================================================================

fun computeCategoryData(rows: List<SampleRow>): List<ChartDatum> {
    var errors = 0; var postponed = 0; var found = 0; var notFound = 0
    rows.forEach { r ->
        when {
            r.hasImportError -> errors++
            r.postponed -> postponed++
            r.found -> found++
            else -> notFound++
        }
    }
    return buildList {
        if (found > 0) add(ChartDatum("Найдено", found, COLOR_FOUND, "FOUND"))
        if (notFound > 0) add(ChartDatum("Не найдено", notFound, COLOR_NOT_FOUND, "NOT_FOUND"))
        if (postponed > 0) add(ChartDatum("Отложено", postponed, COLOR_POSTPONED, "POSTPONED"))
        if (errors > 0) add(ChartDatum("Ошибки", errors, COLOR_ERRORS, "ERRORS"))
    }
}

fun computeSubCategoryData(rows: List<SampleRow>): List<ChartDatum> {
    var normal = 0; var blank = 0; var control = 0
    rows.forEach { r ->
        when (r.status) {
            SampleStatus.NORMAL -> normal++
            SampleStatus.BLANK -> blank++
            SampleStatus.CONTROL -> control++
        }
    }
    return buildList {
        if (normal > 0) add(ChartDatum("Обычные", normal, COLOR_NORMAL, "NORMAL"))
        if (blank > 0) add(ChartDatum("Холостые", blank, COLOR_BLANK, "BLANK"))
        if (control > 0) add(ChartDatum("ВК", control, COLOR_CONTROL, "CONTROL"))
    }
}

fun computeWellProgress(rows: List<SampleRow>): List<WellProgress> {
    return rows
        .groupBy { it.wellNumber }
        .map { (well, list) ->
            WellProgress(well, list.count { it.found }, list.size)
        }
        .sortedBy { it.wellNumber }
}

// ====================================================================
// КРУГОВАЯ
// ====================================================================

@Composable
fun PieChart(
    data: List<ChartDatum>,
    centerLabel: String,
    onSliceClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val total = data.sumOf { it.value }
    if (total == 0) { EmptyChart("Нет данных", modifier); return }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(190.dp)
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
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    total.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    centerLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        ChartLegend(data, total, onSliceClick)
    }
}

@Composable
private fun ChartLegend(
    data: List<ChartDatum>,
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
                    modifier = Modifier.width(52.dp)
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
    data: List<ChartDatum>,
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
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    if (datum.value > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(datum.value.toFloat() / maxValue)
                                .background(datum.color)
                        )
                    }
                }
            }
        }
    }
}

// ====================================================================
// СКВАЖИНЫ
// ====================================================================

@Composable
fun WellProgressChart(
    items: List<WellProgress>,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendItem(COLOR_FOUND, "Найдено")
            LegendItem(COLOR_NOT_FOUND, "Не найдено")
        }
        Spacer(Modifier.height(8.dp))
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
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filtered.forEach { wp -> WellRow(wp, onWellClick) }
            }
        }
    }
}

@Composable
private fun WellRow(wp: WellProgress, onClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick(wp.wellNumber) }
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                wp.wellNumber,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${wp.found}/${wp.total}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            if (wp.total > 0 && wp.found > 0) {
                Box(
                    modifier = Modifier
                        .weight(wp.found.toFloat())
                        .fillMaxHeight()
                        .background(COLOR_FOUND)
                )
            }
            val notFound = wp.total - wp.found
            if (notFound > 0) {
                Box(
                    modifier = Modifier
                        .weight(notFound.toFloat())
                        .fillMaxHeight()
                        .background(COLOR_NOT_FOUND)
                )
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun sliceKindLabel(kind: SliceKind): String = when (kind) {
    SliceKind.FOUND -> "Найдено"
    SliceKind.NOT_FOUND -> "Не найдено"
    SliceKind.POSTPONED -> "Отложено"
    SliceKind.ERRORS -> "Ошибки"
}

fun subKindLabel(sub: SubKind): String = when (sub) {
    SubKind.NORMAL -> "Обычные"
    SubKind.BLANK -> "Холостые"
    SubKind.CONTROL -> "ВК"
}
