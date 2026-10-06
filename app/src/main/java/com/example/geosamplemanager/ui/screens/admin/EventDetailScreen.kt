package com.example.geosamplemanager.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * FIX 5.10-stat-admin-v2-details-c:
 * Экран «Событие» — контекст ±2 минуты вокруг выбранного события.
 *
 * У1=Б: маленький таймлайн сессии по абсолютному времени,
 *       с меткой позиции события.
 * У2=А: центр подсвечен фоном (eventRowExpandable highlighted).
 *
 * Окно ±2 мин вырезается из dayView.events через
 * AdminPanelAggregator.computeEventsAround.
 */
private const val WINDOW_MS = 2L * 60L * 1000L

@Composable
fun EventDetailScreen(
    dayView: DayView,
    sessionId: Long,
    atTs: Long,
    modifier: Modifier = Modifier
) {
    val around = AdminPanelAggregator.computeEventsAround(
        events = dayView.events,
        centerAtTs = atTs,
        centerSessionId = sessionId,
        windowMs = WINDOW_MS
    )
    val session = dayView.sessions.firstOrNull { it.id == sessionId }
    val centerEvent = around.events.getOrNull(around.centerIndex)
        ?: dayView.events.firstOrNull { it.sessionId == sessionId && it.atTs == atTs }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (centerEvent != null) {
            EventHeader(event = centerEvent)
        }
        if (session != null) {
            SessionMiniTimeline(
                session = session,
                eventAtTs = atTs
            )
        }
        Text(
            "Контекст ±2 мин",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (around.events.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "События в окне не найдены.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    around.events.forEachIndexed { index, ev ->
                        EventRowExpandable(
                            event = ev,
                            highlighted = index == around.centerIndex
                        )
                        if (index < around.events.lastIndex) {
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventHeader(event: EventView) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(eventLevelColor(event.level), RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    event.level.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${formatEventTime(event.atTs)} · ${event.category.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                event.summary,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun SessionMiniTimeline(session: SessionView, eventAtTs: Long) {
    val startTs = session.startedAt
    val endTs = session.endedAt ?: System.currentTimeMillis()
    val totalMs = (endTs - startTs).coerceAtLeast(1L)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Сессия #${session.id}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            ) {
                val maxW = maxWidth
                // Фон
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            RoundedCornerShape(4.dp)
                        )
                )
                // Визиты — абсолютные позиции
                session.visits.forEach { v ->
                    val vFrom = (v.fromTs - startTs).coerceIn(0L, totalMs)
                    val vTo = ((v.toTs ?: endTs) - startTs).coerceIn(0L, totalMs)
                    val fromFrac = vFrom.toFloat() / totalMs
                    val toFrac = vTo.toFloat() / totalMs
                    val widthFrac = (toFrac - fromFrac).coerceAtLeast(0.005f)
                    Box(
                        modifier = Modifier
                            .offset(x = maxW * fromFrac)
                            .width(maxW * widthFrac)
                            .fillMaxHeight()
                            .background(
                                tabColor(v.tab).copy(alpha = 0.6f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
                // Метка события
                val eventFrac =
                    ((eventAtTs - startTs).coerceIn(0L, totalMs)).toFloat() / totalMs
                Box(
                    modifier = Modifier
                        .offset(x = maxW * eventFrac - 1.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Color(0xFFE53935))
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatEventTime(startTs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    session.endedAt?.let { formatEventTime(it) } ?: "в процессе",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}