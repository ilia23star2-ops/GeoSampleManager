package com.example.geosamplemanager.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Calendar

/**
 * FIX 5.10-stat-admin-v2-nav:
 * Глобальный таймлайн дня от 00:00 до 24:00.
 *
 *  - фон: серый вне рабочего окна (8:00–17:00 пн–пт), светлый внутри;
 *  - сессии — цветные полосы;
 *  - пресеты масштаба меняют ширину часа;
 *  - горизонтальный скролл при увеличенном масштабе.
 *
 * FIX 5.10-stat-admin-v2-nav (правка меток):
 *  - метки часов рисуются в общем Box и позиционируются через
 *    offset(x = hourWidth * h). Раньше они лежали внутри
 *    Box(width = hourWidth) и обрезались при пресете «Сутки».
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

@Composable
fun DayTimelineBar(
    date: String,
    segments: List<TimelineSegment>,
    scale: TimelineScale,
    onScaleChange: (TimelineScale) -> Unit,
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
                ScaleChips(current = scale, onSelect = onScaleChange)
            }
            Spacer(Modifier.height(8.dp))

            val (dayStart, dayEnd) = AdminPanelDateUtils.dayBounds(date)
            val totalMs = (dayEnd - dayStart).coerceAtLeast(1L)

            val hourWidth = scale.hourWidthDp.dp
            val totalWidth = hourWidth * HOURS_PER_DAY

            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    // Полоса сессий + фон
                    Box(
                        modifier = Modifier
                            .width(totalWidth)
                            .height(28.dp)
                    ) {
                        // Фон: по часу, светлый/серый
                        Row(modifier = Modifier.fillMaxSize()) {
                            for (h in 0 until HOURS_PER_DAY) {
                                val hourTs = dayStart + h * 3_600_000L
                                val isWork = isWorkTime(hourTs)
                                Box(
                                    modifier = Modifier
                                        .width(hourWidth)
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
                        // Сессии
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
                                    .height(20.dp)
                                    .align(Alignment.CenterStart)
                                    .background(
                                        if (seg.crashFlag) Color(0xFFE53935)
                                        else Color(0xFF3F51B5),
                                        RoundedCornerShape(3.dp)
                                    )
                                    .clickable { onSessionClick(seg.sessionId) }
                            )
                        }
                    }

                    // FIX: метки часов — абсолютным offset в общем Box.
                    Box(
                        modifier = Modifier
                            .width(totalWidth)
                            .height(20.dp)
                    ) {
                        for (h in 0 until HOURS_PER_DAY step 4) {
                            Text(
                                "%02d:00".format(h),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .offset(x = hourWidth * h + 4.dp)
                                    .align(Alignment.TopStart)
                            )
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

/**
 * Рабочее ли это время (пн–пт, 8:00–17:00). Выходные и вне
 * окна — серый фон.
 */
private fun isWorkTime(ts: Long): Boolean {
    val cal = Calendar.getInstance()
    cal.timeInMillis = ts
    val dow = cal.get(Calendar.DAY_OF_WEEK)
    if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) return false
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    return hour in WORK_START_HOUR until WORK_END_HOUR
}