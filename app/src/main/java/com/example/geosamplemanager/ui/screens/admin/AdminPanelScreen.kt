package com.example.geosamplemanager.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * FIX 5.10-stat-admin-ui-2:
 * Полноэкранный экран админ-панели. Открывается долгим тапом
 * на версии в Настройках → «О приложении» (интеграция — в -3).
 *
 * Структура (SHADOW_STATS §7.2–7.6):
 *  - TopAppBar с «Закрыть» и «Обновить»;
 *  - сводка дня;
 *  - timeline по вкладкам;
 *  - список сессий (свёрнуты, тап — раскрывает);
 *  - лента событий;
 *  - блок «Требует внимания».
 *
 * Важно: используется TopAppBar в Column, без вложенного Scaffold.
 * Это грабли из CONTEXT_BRIEF — вложенный Scaffold Material3 ломает
 * Compose Runtime.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    onClose: () -> Unit,
    viewModel: AdminPanelViewModel = viewModel()
) {
    val dayView by viewModel.dayView.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadToday() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Теневая статистика") },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть")
                }
            },
            actions = {
                IconButton(onClick = { viewModel.loadToday() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                }
            }
        )

        val v = dayView
        if (v == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DayHeader(date = v.date)
            TotalsCard(totals = v.totals)
            if (v.tabUsage.isNotEmpty()) {
                TabTimelineBar(usage = v.tabUsage)
            }
            ProblemsBlock(
                problems = v.problems,
                onOpenDiagnostics = { /* заглушка — реально в -3 */ }
            )
            if (v.sessions.isNotEmpty()) {
                Text(
                    "Сессии",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                v.sessions.forEach { session ->
                    SessionCard(session = session)
                }
            } else {
                EmptyDayCard()
            }
            EventsList(events = v.events)
        }
    }
}

@Composable
private fun DayHeader(date: String) {
    Text(
        "Дата: $date",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun TotalsCard(totals: DayTotals) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Сводка",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(
                    label = "Активно",
                    value = "${totals.activePercent}% " +
                            "(${AdminPanelAggregator.formatDuration(totals.activeSec)})",
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = "Простой",
                    value = "${totals.idlePercent}% " +
                            "(${AdminPanelAggregator.formatDuration(totals.idleSec)})",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(
                    label = "Прогонов",
                    value = totals.runs.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = "Готовых нарядов",
                    value = totals.readyOrders.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
            if (totals.errorsCount > 0 || totals.warnsCount > 0) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (totals.errorsCount > 0) {
                        Badge(
                            text = "✕ ${totals.errorsCount} ошибок",
                            bg = MaterialTheme.colorScheme.errorContainer,
                            fg = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    if (totals.warnsCount > 0) {
                        Badge(
                            text = "⚠ ${totals.warnsCount} предупреждений",
                            bg = MaterialTheme.colorScheme.tertiaryContainer,
                            fg = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun Badge(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
private fun EmptyDayCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "За этот день данных нет.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}