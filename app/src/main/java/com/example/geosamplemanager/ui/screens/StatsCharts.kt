package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * FIX 5.9-stats-charts:
 * Canvas-диаграммы для экрана «Статистика».
 *
 * Три типа:
 *  1. Круговая — найдено / не найдено / отложено / ошибки.
 *  2. Столбцы — по статусам: обычные / холостые / ВК.
 *  3. По скважинам — прогресс по каждой скважине.
 */

// ====================================================================
// МОДЕЛИ
// ====================================================================

data class PieSlice(
    val label: String,
    val value: Int,
    val color: Color
)

data class BarDatum(
    val label: String,
    val value: Int,
    val color: Color
)

data class WellProgress(
    val wellNumber: String,
    val found: Int,
    val total: Int
)

// ====================================================================
// ВЫЧИСЛЕНИЕ ДАННЫХ
// ====================================================================

fun computePieSlices(rows: List<SampleRow>): List<PieSlice> {
    var errors = 0
    var postponed = 0
    var found = 0
    var notFound = 0

    rows.forEach { r ->
        when {
            r.hasImportError -> errors++
            r.postponed -> postponed++
            r.found -> found++
            else -> notFound++
        }
    }

    return buildList {
        if (found > 0) add(PieSlice("Найдено", found, Color(0xFF2E7D32)))
        if (notFound > 0) add(PieSlice("Не найдено", notFound, Color(0xFFC62828)))
        if (postponed > 0) add(PieSlice("Отложено", postponed, Color(0xFF1976D2)))
        if (errors > 0) add(PieSlice("Ошибки", errors, Color(0xFFB71C1C)))
    }
}

fun computeBarData(rows: List<SampleRow>): List<BarDatum> {
    var normal = 0
    var blank = 0
    var control = 0

    rows.forEach { r ->
        when (r.status) {
            SampleStatus.NORMAL -> normal++
            SampleStatus.BLANK -> blank++
            SampleStatus.CONTROL -> control++
        }
    }

    return listOf(
        BarDatum("Обычные", normal, Color(0xFF1976D2)),
        BarDatum("Холостые", blank, Color(0xFFF9A825)),
        BarDatum("ВК", control, Color(0xFF7B1FA2))
    )
}

fun computeWellProgress(rows: List<SampleRow>): List<WellProgress> {
    return rows
        .groupBy { it.wellNumber }
        .map { (well, list) ->
            WellProgress(
                wellNumber = well,
                found = list.count { it.found },
                total = list.size
            )
        }
        .sortedBy { it.wellNumber }
}

// ====================================================================
// UI — КРУГОВАЯ
// ====================================================================

private val PIE_SIZE = 180.dp
private val PIE_DIAMETER = 150.dp

@Composable
fun PieChart(
    slices: List<PieSlice>,
    modifier: Modifier = Modifier
) {
    val total = slices.sumOf { it.value }

    if (total == 0) {
        EmptyChart("Нет данных для диаграммы", modifier)
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PIE_SIZE),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(PIE_DIAMETER)) {
                val diameter = size.minDimension
                val topLeft = Offset(
                    (size.width - diameter) / 2f,
                    (size.height - diameter) / 2f
                )
                val arcSize = Size(diameter, diameter)

                var startAngle = -90f
                slices.forEach { slice ->
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

            Text(
                total.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(8.dp))
        Legend(slices.map { it.label to it.color })
    }
}

// ====================================================================
// UI — СТОЛБЦЫ
// ====================================================================

@Composable
fun BarChart(
    bars: List<BarDatum>,
    modifier: Modifier = Modifier
) {
    val maxValue = bars.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1

    if (bars.all { it.value == 0 }) {
        EmptyChart("Нет данных для диаграммы", modifier)
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        bars.forEach { bar ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(80.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    if (bar.value > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(bar.value.toFloat() / maxValue)
                                .background(bar.color)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    bar.value.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(36.dp)
                )
            }
        }
    }
}

// ====================================================================
// UI — ПО СКВАЖИНАМ
// ====================================================================

@Composable
fun WellProgressChart(
    items: List<WellProgress>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        EmptyChart("Нет скважин в наряде", modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { wp ->
            val progress = if (wp.total > 0) wp.found.toFloat() / wp.total else 0f
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    wp.wellNumber,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(110.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${wp.found}/${wp.total}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(56.dp)
                )
            }
        }
    }
}

// ====================================================================
// ЛЕГЕНДА + ЗАГЛУШКА
// ====================================================================

@Composable
private fun Legend(items: List<Pair<String, Color>>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

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
