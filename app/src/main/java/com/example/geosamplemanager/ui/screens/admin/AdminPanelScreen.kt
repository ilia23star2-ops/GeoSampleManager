package com.example.geosamplemanager.ui.screens.admin

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * FIX 5.10-stat-admin-ui-2: базовый экран админ-панели.
 * FIX 5.10-stat-admin-ui-3: без return@Column, через when.
 * FIX 5.10-stat-admin-ui-4: выбор дня.
 * FIX 5.10-stat-admin-ui-5a: блок «Незавершённые».
 * FIX 5.10-stat-admin-v2-nav: табы, таймлайн, Back.
 * FIX 5.10-stat-admin-v2-time-filter: фильтр времени.
 * FIX 5.10-stat-admin-v2-orders-b: таб «Наряды».
 * FIX 5.10-stat-admin-v2-details-a: провалы.
 * FIX 5.10-stat-admin-v2-details-c: таб «Ошибки» + EventDetail.
 *
 * FIX 5.10-stat-daily-file-1:
 *  - кнопка «Экспорт дня» (Icons.Default.Share) в TopAppBar;
 *  - Toast по завершении экспорта.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    onClose: () -> Unit,
    viewModel: AdminPanelViewModel = viewModel()
) {
    val context = LocalContext.current

    val dayView by viewModel.dayView.collectAsState()
    val availableDates by viewModel.availableDates.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val timelineSegments by viewModel.timelineSegments.collectAsState()
    val route by viewModel.route.collectAsState()
    val timeFilterInput by viewModel.timeFilterInput.collectAsState()
    val timeFilterResult by viewModel.timeFilterResult.collectAsState()
    val allOrders by viewModel.allOrders.collectAsState()
    val ordersFilterInput by viewModel.ordersFilterInput.collectAsState()
    val eventsFilterCategory by viewModel.eventsFilterCategory.collectAsState()
    val exportMessage by viewModel.exportMessage.collectAsState()

    var timelineScale by remember { mutableStateOf(TimelineScale.DAY) }

    LaunchedEffect(Unit) { viewModel.loadToday() }

    LaunchedEffect(exportMessage) {
        exportMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearExportMessage()
        }
    }

    BackHandler(enabled = viewModel.canGoBack()) {
        viewModel.goBack()
    }
    BackHandler(enabled = !viewModel.canGoBack()) {
        onClose()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Теневая статистика") },
            navigationIcon = {
                IconButton(onClick = {
                    if (viewModel.canGoBack()) viewModel.goBack() else onClose()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть")
                }
            },
            actions = {
                IconButton(onClick = { viewModel.exportDay() }) {
                    Icon(Icons.Default.Share, contentDescription = "Экспорт дня")
                }
                IconButton(onClick = { viewModel.loadToday() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                }
            }
        )

        AdminTabs(
            current = (route as? AdminPanelRoute.Tab)?.tab ?: AdminPanelTab.DAY,
            onSelect = { viewModel.selectTab(it) }
        )

        when (val r = route) {
            is AdminPanelRoute.Tab -> when (r.tab) {
                AdminPanelTab.DAY -> DayTab(
                    dayView = dayView,
                    selectedDate = selectedDate,
                    availableDates = availableDates,
                    timelineSegments = timelineSegments,
                    timelineScale = timelineScale,
                    timeFilterInput = timeFilterInput,
                    timeFilterResult = timeFilterResult,
                    onScaleChange = { timelineScale = it },
                    onFilterInputChange = { viewModel.setTimeFilterInput(it) },
                    onClearFilter = { viewModel.clearTimeFilter() },
                    onSelectDate = { viewModel.selectDate(it) },
                    onOpenSession = { viewModel.openSession(it) },
                    onOpenErrors = { viewModel.selectTab(AdminPanelTab.ERRORS) }
                )
                AdminPanelTab.ORDERS -> OrdersTab(
                    allOrders = allOrders,
                    filterInput = ordersFilterInput,
                    onFilterChange = { viewModel.setOrdersFilter(it) }
                )
                AdminPanelTab.ERRORS -> ErrorsTab(
                    allEvents = dayView?.events.orEmpty(),
                    filterCategory = eventsFilterCategory,
                    onFilterChange = { viewModel.setEventsFilterCategory(it) },
                    onOpenEvent = { sid, atTs -> viewModel.openEvent(sid, atTs) }
                )
            }

            is AdminPanelRoute.SessionDetail -> {
                val session = dayView?.sessions?.firstOrNull { it.id == r.sessionId }
                if (session != null) {
                    SessionDetailScreen(
                        session = session,
                        onOpenVisit = { sid, fromTs -> viewModel.openVisit(sid, fromTs) }
                    )
                } else {
                    StubTab(
                        title = "Сессия #${r.sessionId}",
                        hint = "Данные сессии не загружены."
                    )
                }
            }

            is AdminPanelRoute.VisitDetail -> {
                val visit = dayView?.sessions
                    ?.firstOrNull { it.id == r.sessionId }
                    ?.visits
                    ?.firstOrNull { it.fromTs == r.fromTs }
                if (visit != null) {
                    VisitDetailScreen(
                        visit = visit,
                        allEvents = dayView?.events.orEmpty()
                    )
                } else {
                    StubTab(
                        title = "Визит",
                        hint = "Данные визита не загружены."
                    )
                }
            }

            is AdminPanelRoute.EventDetail -> {
                val dv = dayView
                if (dv != null) {
                    EventDetailScreen(
                        dayView = dv,
                        sessionId = r.sessionId,
                        atTs = r.atTs
                    )
                } else {
                    StubTab(
                        title = "Событие",
                        hint = "Данные дня не загружены."
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminTabs(
    current: AdminPanelTab,
    onSelect: (AdminPanelTab) -> Unit
) {
    TabRow(selectedTabIndex = current.ordinal) {
        AdminPanelTab.values().forEach { tab ->
            Tab(
                selected = tab == current,
                onClick = { onSelect(tab) },
                text = { Text(tab.title) }
            )
        }
    }
}

@Composable
private fun DayTab(
    dayView: DayView?,
    selectedDate: String?,
    availableDates: List<String>,
    timelineSegments: List<TimelineSegment>,
    timelineScale: TimelineScale,
    timeFilterInput: String,
    timeFilterResult: TimeFilterParseResult,
    onScaleChange: (TimelineScale) -> Unit,
    onFilterInputChange: (String) -> Unit,
    onClearFilter: () -> Unit,
    onSelectDate: (String) -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenErrors: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        DaySelectorRow(
            dates = availableDates,
            selected = selectedDate,
            onSelect = onSelectDate
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
                TotalsCard(
                    totals = v.totals,
                    onOpenErrors = onOpenErrors
                )
                DayTimelineBar(
                    date = v.date,
                    segments = timelineSegments,
                    scale = timelineScale,
                    timeFilterInput = timeFilterInput,
                    timeFilterResult = timeFilterResult,
                    onScaleChange = onScaleChange,
                    onFilterInputChange = onFilterInputChange,
                    onClearFilter = onClearFilter,
                    onSessionClick = onOpenSession
                )
                if (v.tabUsage.isNotEmpty()) {
                    TabTimelineBar(usage = v.tabUsage)
                }
                UnfinishedOrdersBlock(orders = v.unfinishedOrders)
                ProblemsBlock(
                    problems = v.problems,
                    onOpenDiagnostics = { /* заглушка — отдельная пачка */ }
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

@Composable
private fun StubTab(title: String, hint: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

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
            onValueChange = { },
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
private fun TotalsCard(
    totals: DayTotals,
    onOpenErrors: () -> Unit
) {
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
                            fg = MaterialTheme.colorScheme.onErrorContainer,
                            onClick = onOpenErrors
                        )
                    }
                    if (totals.warnsCount > 0) {
                        Badge(
                            text = "⚠ ${totals.warnsCount} предупреждений",
                            bg = MaterialTheme.colorScheme.tertiaryContainer,
                            fg = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = onOpenErrors
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
private fun Badge(
    text: String,
    bg: Color,
    fg: Color,
    onClick: (() -> Unit)? = null
) {
    val base = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier = base
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