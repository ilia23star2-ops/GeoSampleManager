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
 * Полноэкранный экран админ-панели.
 *
 * FIX 5.10-stat-admin-ui-3 (уточнение):
 * убран `return@Column` при пустом дне. Compose Runtime падал
 * с `IndexOutOfBoundsException` в `Stack.pop`. Заменено на
 * `when (dayView)` с двумя полными ветками.
 *
 * FIX 5.10-stat-admin-ui-4:
 * выбор дня через ExposedDropdownMenuBox под TopAppBar.
 *
 * FIX 5.10-stat-admin-ui-5a:
 * блок «Незавершённые» (§7.6) между timeline и ProblemsBlock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    onClose: () -> Unit,
    viewModel: AdminPanelViewModel = viewModel()
) {
    val dayView by viewModel.dayView.collectAsState()
    val availableDates by viewModel.availableDates.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()

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

        DaySelectorRow(
            dates = availableDates,
            selected = selectedDate,
            onSelect = { viewModel.selectDate(it) }
        )

        when (val v = dayView) {
            null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TotalsCard(totals = v.totals)
                if (v.tabUsage.isNotEmpty()) {
                    TabTimelineBar(usage = v.tabUsage)
                }
                UnfinishedOrdersBlock(orders = v.unfinishedOrders)
                ProblemsBlock(
                    problems = v.problems,
                    onOpenDiagnostics = { /* заглушка — реально в -5b */ }
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
}

/**
 * FIX 5.10-stat-admin-ui-4:
 * Строка выбора дня.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySelectorRow(
    dates: List<String>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    if (dates.isEmpty() || selected == null) return

    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = AdminPanelDateUtils.label(selected),
            onValueChange = { /* read-only */ },
            readOnly = true,
            singleLine = true,
            label = { Text("Дата") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            dates.forEach { d ->
                DropdownMenuItem(
                    text = { Text(AdminPanelDateUtils.label(d)) },
                    onClick = {
                        expanded = false
                        onSelect(d)
                    }
                )
            }
        }
    }
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