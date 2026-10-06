package com.example.geosamplemanager.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Calendar

/**
 * FIX 5.10-stat-admin-v2-nav: таймлайн 00:00–24:00.
 * FIX 5.10-stat-admin-v2-time-filter: фильтр времени.
 *
 * FIX 5.10-stat-admin-v2-time-filter (доработка 3):
 *  - метки часов и жёлтая подсветка Point стоят на ОДНОЙ позиции
 *    (hourWidthDp * hour). Раньше метка была со сдвигом +4dp,
 *    а подсветка -1dp — рассинхрон 5dp был виден глазом.
 */
enum class TimelineScale(val hourWidthDp: Int, val title: String) {
    DAY(20, "Сутки"),
    EIGHT_HOURS(60, "8 ч"),
    FOUR_HOURS(120, "4 ч"),
    ONE_HOUR(480, "1 ч")
}

private const val HOURS_PER_DAY = 24
private const val WORK_START_HOUR = 8
private const val WORK_END_HOUR = 17
private const val SEGMENTS_ROW_HEIGHT_DP = 60
private const val LABELS_ROW_HEIGHT_DP = 24

@Composable
fun DayTimelineBar(
    date: String,
    segments: List<TimelineSegment>,
    scale: TimelineScale,
    timeFilterInput: String,
    timeFilterResult: TimeFilterParseResult,
    onScaleChange: (TimelineScale) -> Unit,
    onFilterInputChange: (String) -> Unit,
    onClearFilter: () -> Unit,
    onSessionClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Таймлайн дня",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                ScaleChips(
                    current = scale,
                    onSelect = {
                        onClearFilter()
                        onScaleChange(it)
                    }
                )
            }
            Spacer(Modifier.height(8.dp))

            FilterRow(
                input = timeFilterInput,
                result = timeFilterResult,
                onChange = onFilterInputChange,
                onClear = onClearFilter
            )

            Spacer(Modifier.height(8.dp))

            val (dayStart, dayEnd) = AdminPanelDateUtils.dayBounds(date)
            val totalMs = (dayEnd - dayStart).coerceAtLeast(1L)

            val scrollState = rememberScrollState()
            val density = LocalDensity.current

            BoxWithConstraints {
                val maxW = maxWidth

                val baseHourWidth: Dp =
                    if (scale == TimelineScale.DAY) maxW / HOURS_PER_DAY
                    else scale.hourWidthDp.dp

                val hourWidthDp: Dp = when (val r = timeFilterResult) {
                    is TimeFilterParseResult.Ok -> when (val f = r.filter) {
                        is TimeFilter.Range -> {
                            val hours = (f.toMin - f.fromMin) / 60f
                            maxW / hours.coerceAtLeast(0.5f)
                        }
                        is TimeFilter.Point -> baseHourWidth
                    }
                    else -> baseHourWidth
                }

                val totalWidth = hourWidthDp * HOURS_PER_DAY
                val hourWidthPx = with(density) { hourWidthDp.toPx() }
                val totalWidthPx = hourWidthPx * HOURS_PER_DAY

                LaunchedEffect(timeFilterResult, scale, scrollState.viewportSize) {
                    if (scrollState.viewportSize <= 0) return@LaunchedEffect
                    val viewport = scrollState.viewportSize.toFloat()
                    val targetPx: Float? = when (val r = timeFilterResult) {
                        is TimeFilterParseResult.Ok -> when (val f = r.filter) {
                            is TimeFilter.Point -> {
                                val pos = (f.minutes / 60f) * hourWidthPx
                                pos - viewport / 2f
                            }
                            is TimeFilter.Range -> {
                                (f.fromMin / 60f) * hourWidthPx
                            }
                        }
                        else -> null
                    }
                    if (targetPx != null) {
                        val maxScroll = (totalWidthPx - viewport).coerceAtLeast(0f)
                        scrollState.scrollTo(targetPx.coerceIn(0f, maxScroll).toInt())
                    }
                }

                Row(modifier = Modifier.horizontalScroll(scrollState)) {
                    Column {
                        // Полоса сессий
                        Box(
                            modifier = Modifier
                                .width(totalWidth)
                                .height(SEGMENTS_ROW_HEIGHT_DP.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                for (h in 0 until HOURS_PER_DAY) {
                                    val hourTs = dayStart + h * 3_600_000L
                                    val isWork = isWorkTime(hourTs)
                                    Box(
                                        modifier = Modifier
                                            .width(hourWidthDp)
                                            .fillMaxHeight()
                                            .background(
                                                if (isWork)
                                                    Color(0xFF2E7D32).copy(alpha = 0.12f)
                                                else
                                                    Color(0xFF616161).copy(alpha = 0.12f)
                                            )
                                    )
                                }
                            }
                            for (seg in segments) {
                                val leftFrac = (seg.fromTs - dayStart).toFloat() / totalMs
                                val rightFrac = (seg.toTs - dayStart).toFloat() / totalMs
                                val left = totalWidth * leftFrac
                                val w = (totalWidth * (rightFrac - leftFrac))
                                    .coerceAtLeast(2.dp)
                                Box(
                                    modifier = Modifier
                                        .offset(x = left)
                                        .width(w)
                                        .fillMaxHeight(0.7f)
                                        .align(Alignment.CenterStart)
                                        .background(
                                            if (seg.crashFlag) Color(0xFFE53935)
                                            else Color(0xFF3F51B5),
                                            RoundedCornerShape(3.dp)
                                        )
                                        .clickable { onSessionClick(seg.sessionId) }
                                )
                            }
                            // Подсветка позиции Point — центр ровно на позиции.
                            val pointMinutes = (timeFilterResult as? TimeFilterParseResult.Ok)
                                ?.filter?.let { it as? TimeFilter.Point }?.minutes
                            if (pointMinutes != null) {
                                val pos = hourWidthDp * (pointMinutes / 60f)
                                Box(
                                    modifier = Modifier
                                        .offset(x = pos - 1.dp)
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .align(Alignment.TopStart)
                                        .background(Color(0xFFFFC107))
                                )
                            }
                        }

                        // Метки часов
                        Box(
                            modifier = Modifier
                                .width(totalWidth)
                                .height(LABELS_ROW_HEIGHT_DP.dp)
                        ) {
                            val range = (timeFilterResult as? TimeFilterParseResult.Ok)
                                ?.filter as? TimeFilter.Range
                            if (range != null) {
                                Text(
                                    formatMinutes(range.fromMin),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(top = 4.dp)
                                )
                                Text(
                                    formatMinutes(range.toMin),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 4.dp)
                                )
                            } else {
                                for (h in 0 until HOURS_PER_DAY step 4) {
                                    // FIX: без +4dp — метка на той же позиции,
                                    // что и жёлтая подсветка.
                                    Text(
                                        "%02d:00".format(h),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .offset(x = hourWidthDp * h)
                                            .align(Alignment.TopStart)
                                            .padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScaleChips(
    current: TimelineScale,
    onSelect: (TimelineScale) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TimelineScale.values().forEach { s ->
            FilterChip(
                selected = s == current,
                onClick = { onSelect(s) },
                label = { Text(s.title, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun FilterRow(
    input: String,
    result: TimeFilterParseResult,
    onChange: (String) -> Unit,
    onClear: () -> Unit
) {
    val isError = result is TimeFilterParseResult.Invalid

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = input,
            onValueChange = onChange,
            label = { Text("Время") },
            placeholder = { Text("12, 12:30, 12-13, 12:00-13:30") },
            singleLine = true,
            isError = isError,
            trailingIcon = {
                if (input.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Close, contentDescription = "Очистить")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (isError) {
            Text(
                (result as TimeFilterParseResult.Invalid).message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
            )
        }
    }
}

private fun formatMinutes(min: Int): String {
    val h = (min / 60).coerceIn(0, 23)
    val m = (min % 60).coerceIn(0, 59)
    return "%02d:%02d".format(h, m)
}

private fun isWorkTime(ts: Long): Boolean {
    val cal = Calendar.getInstance()
    cal.timeInMillis = ts
    val dow = cal.get(Calendar.DAY_OF_WEEK)
    if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) return false
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    return hour in WORK_START_HOUR until WORK_END_HOUR
}