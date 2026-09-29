package com.example.geosamplemanager.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.geosamplemanager.data.voice.AnswerState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FIX 5.9-stats-fixes:
 *  - Левая панель: скролл + FAB «Наверх» (порог >8).
 *  - Тап по строке участка — выбрать участок (подсветка).
 *    Тап по стрелке — раскрыть/свернуть.
 *  - Правая панель: если выбран наряд — сводка наряда (как раньше).
 *    Если выбран участок — сводка участка (общая статистика + наряды).
 */
@Composable
fun StatsScreen(viewModel: StatsViewModel = viewModel()) {
    val data by viewModel.data.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val hideReady by viewModel.hideReady.collectAsState()
    val expandedAreas by viewModel.expandedAreaIds.collectAsState()
    val expandedOrders by viewModel.expandedOrderIds.collectAsState()
    val selectedOrderId by viewModel.selectedOrderId.collectAsState()
    val selectedAreaId by viewModel.selectedAreaId.collectAsState()
    val drillStack by viewModel.drillStack.collectAsState()
    val wellFilter by viewModel.wellFilter.collectAsState()
    val message by viewModel.message.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingOrderIdForReport by remember { mutableStateOf<Long?>(null) }

    val htmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        val orderId = pendingOrderIdForReport
        pendingOrderIdForReport = null
        if (uri != null && orderId != null) {
            scope.launch {
                val ok = viewModel.generateHtmlReport(orderId, uri)
                snackbarHostState.showSnackbar(
                    if (ok) "Отчёт сохранён"
                    else "Не удалось сохранить отчёт"
                )
            }
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val current = data
    if (current == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp
        val treeWidth = maxWidth * 0.35f

        if (isWide) {
            Row(modifier = Modifier.fillMaxSize()) {
                LeftTreePanel(
                    data = current,
                    searchQuery = searchQuery,
                    hideReady = hideReady,
                    expandedAreaIds = expandedAreas,
                    expandedOrderIds = expandedOrders,
                    selectedOrderId = selectedOrderId,
                    selectedAreaId = selectedAreaId,
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    onHideReadyChange = { viewModel.setHideReady(it) },
                    onToggleArea = { viewModel.toggleArea(it) },
                    onToggleOrder = { viewModel.toggleOrder(it) },
                    onSelectOrder = { viewModel.selectOrder(it) },
                    onSelectArea = { viewModel.selectArea(it) },
                    onFilterChange = { viewModel.setFilter(it) },
                    onToggleAll = {
                        val anyExpanded = expandedAreas.isNotEmpty() || expandedOrders.isNotEmpty()
                        if (anyExpanded) viewModel.collapseAll() else viewModel.expandAll()
                    },
                    modifier = Modifier.fillMaxHeight().width(treeWidth)
                )
                VerticalDivider()
                RightDetailsPanel(
                    order = selectedOrderId?.let { viewModel.findOrder(it) },
                    area = if (selectedOrderId == null) {
                        selectedAreaId?.let { viewModel.findArea(it) }
                    } else null,
                    areaName = selectedOrderId?.let { viewModel.findAreaNameFor(it) },
                    drillStack = drillStack,
                    wellFilter = wellFilter,
                    showBackButton = false,
                    onBack = { viewModel.clearSelection() },
                    onPushDrill = { viewModel.pushDrill(it) },
                    onPopDrill = { viewModel.popDrill() },
                    onPopToIndex = { viewModel.popToIndex(it) },
                    onResetDrill = { viewModel.resetDrill() },
                    onWellFilterChange = { viewModel.setWellFilter(it) },
                    onReportHtml = { orderId ->
                        pendingOrderIdForReport = orderId
                        val dateStr = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
                            .format(Date())
                        htmlLauncher.launch("Отчёт_Наряд_${orderId}_$dateStr.html")
                    },
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier.fillMaxHeight().weight(1f)
                )
            }
        } else {
            val order = selectedOrderId?.let { viewModel.findOrder(it) }
            val area = if (order == null) {
                selectedAreaId?.let { viewModel.findArea(it) }
            } else null
            if (order == null && area == null) {
                LeftTreePanel(
                    data = current,
                    searchQuery = searchQuery,
                    hideReady = hideReady,
                    expandedAreaIds = expandedAreas,
                    expandedOrderIds = expandedOrders,
                    selectedOrderId = null,
                    selectedAreaId = null,
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    onHideReadyChange = { viewModel.setHideReady(it) },
                    onToggleArea = { viewModel.toggleArea(it) },
                    onToggleOrder = { viewModel.toggleOrder(it) },
                    onSelectOrder = { viewModel.selectOrder(it) },
                    onSelectArea = { viewModel.selectArea(it) },
                    onFilterChange = { viewModel.setFilter(it) },
                    onToggleAll = {
                        val anyExpanded = expandedAreas.isNotEmpty() || expandedOrders.isNotEmpty()
                        if (anyExpanded) viewModel.collapseAll() else viewModel.expandAll()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                RightDetailsPanel(
                    order = order,
                    area = area,
                    areaName = viewModel.findAreaNameFor(order?.orderId ?: -1L),
                    drillStack = drillStack,
                    wellFilter = wellFilter,
                    showBackButton = true,
                    onBack = { viewModel.clearSelection() },
                    onPushDrill = { viewModel.pushDrill(it) },
                    onPopDrill = { viewModel.popDrill() },
                    onPopToIndex = { viewModel.popToIndex(it) },
                    onResetDrill = { viewModel.resetDrill() },
                    onWellFilterChange = { viewModel.setWellFilter(it) },
                    onReportHtml = { orderId ->
                        pendingOrderIdForReport = orderId
                        val dateStr = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
                            .format(Date())
                        htmlLauncher.launch("Отчёт_Наряд_${orderId}_$dateStr.html")
                    },
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ====================================================================
// ЛЕВАЯ ПАНЕЛЬ
// ====================================================================

private const val LEFT_SCROLL_TOP_THRESHOLD = 8

@Composable
private fun LeftTreePanel(
    data: StatsData,
    searchQuery: String,
    hideReady: Boolean,
    expandedAreaIds: Set<Long>,
    expandedOrderIds: Set<Long>,
    selectedOrderId: Long?,
    selectedAreaId: Long?,
    onSearchChange: (String) -> Unit,
    onHideReadyChange: (Boolean) -> Unit,
    onToggleArea: (Long) -> Unit,
    onToggleOrder: (Long) -> Unit,
    onSelectOrder: (Long) -> Unit,
    onSelectArea: (Long) -> Unit,
    onFilterChange: (StatsFilter) -> Unit,
    onToggleAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > LEFT_SCROLL_TOP_THRESHOLD
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchField(query = searchQuery, onQueryChange = onSearchChange)
            TotalsHeader(data.totals)
            FiltersRow(
                filter = data.filter,
                hideReady = hideReady,
                onFilterChange = onFilterChange,
                onHideReadyChange = onHideReadyChange,
                allExpanded = expandedAreaIds.isNotEmpty() || expandedOrderIds.isNotEmpty(),
                onToggleAll = onToggleAll
            )
            HorizontalDivider()

            val items = remember(data, expandedAreaIds, expandedOrderIds) {
                buildStatsItems(data, expandedAreaIds, expandedOrderIds)
            }

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when {
                            searchQuery.isNotBlank() ->
                                "Ничего не найдено по запросу «$searchQuery»"
                            hideReady && data.filter == StatsFilter.ALL ->
                                "Нет активных нарядов"
                            data.filter == StatsFilter.ALL ->
                                "В базе пока нет данных"
                            else ->
                                "Нет проб, соответствующих фильтру"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(items, key = { it.key }) { item ->
                        when (item) {
                            is StatsItem.AreaHeader -> AreaHeaderCard(
                                area = item.area,
                                expanded = item.expanded,
                                selected = item.area.areaId == selectedAreaId,
                                onToggle = { onToggleArea(item.area.areaId) },
                                onSelect = { onSelectArea(item.area.areaId) }
                            )
                            is StatsItem.OrderHeader -> OrderHeaderCard(
                                order = item.order,
                                expanded = item.expanded,
                                selected = item.order.orderId == selectedOrderId,
                                onToggle = { onToggleOrder(item.order.orderId) },
                                onSelect = { onSelectOrder(item.order.orderId) }
                            )
                            is StatsItem.TableHeadItem -> Unit
                            is StatsItem.SampleRowItem -> Unit
                        }
                    }
                }
            }
        }

        if (showScrollTop) {
            SmallFloatingActionButton(
                onClick = {
                    scope.launch { listState.scrollToItem(0) }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Наверх")
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        placeholder = {
            Text(
                "Поиск наряда или участка",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Очистить",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun TotalsHeader(stats: GroupStats) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "Общая статистика",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SmallStatChip("Всего", stats.total, null)
                SmallStatChip("Найдено", stats.found, Color(0xFF2E7D32))
                SmallStatChip("Не найдено", stats.notFound, Color(0xFFC62828))
                SmallStatChip("Холостые", stats.blanks, null)
                SmallStatChip("ВК", stats.weightControls, null)
                SmallStatChip("Отложено", stats.postponed, Color(0xFF1976D2))
                if (stats.errors > 0) SmallStatChip("Ошибки", stats.errors, Color(0xFFC62828))
            }
        }
    }
}

@Composable
private fun SmallStatChip(label: String, value: Int, color: Color?) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color ?: MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun FiltersRow(
    filter: StatsFilter,
    hideReady: Boolean,
    onFilterChange: (StatsFilter) -> Unit,
    onHideReadyChange: (Boolean) -> Unit,
    allExpanded: Boolean,
    onToggleAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatsFilter.values().forEach { f ->
            FilterChip(
                selected = f == filter,
                onClick = { onFilterChange(f) },
                label = { Text(f.title, fontSize = 12.sp) }
            )
        }
        FilterChip(
            selected = hideReady,
            onClick = { onHideReadyChange(!hideReady) },
            label = { Text("Скрыть готовые", fontSize = 12.sp) },
            leadingIcon = if (hideReady) {
                { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }
            } else null
        )
        TextButton(onClick = onToggleAll, contentPadding = PaddingValues(horizontal = 6.dp)) {
            Icon(
                if (allExpanded) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun AreaHeaderCard(
    area: StatsAreaUi,
    expanded: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit
) {
    val baseColors = AnswerStateColors.of(AnswerState.OK)
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        baseColors.background
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clickable { onSelect() },
        color = containerColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp).height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(baseColors.accent)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    area.areaName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${area.orders.size} наряд(ов) · проб: ${area.stats.total} · " +
                            "найдено: ${area.stats.found}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Свернуть" else "Развернуть",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun OrderHeaderCard(
    order: StatsOrderUi,
    expanded: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit
) {
    val status = computeOrderStatus(order.stats)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, start = 12.dp)
            .clickable { onSelect() },
        color = if (selected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(status)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Наряд №${order.orderNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    buildOrderShortSummary(order.stats),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Свернуть" else "Развернуть",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusDot(status: OrderStatus) {
    val color = when (status) {
        OrderStatus.READY -> Color(0xFF2E7D32)
        OrderStatus.NEEDS_REVIEW -> Color(0xFF1976D2)
        OrderStatus.IN_PROGRESS -> Color(0xFFF9A825)
        OrderStatus.NOT_STARTED -> Color(0xFF9E9E9E)
        OrderStatus.EMPTY -> Color(0xFF424242)
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(50))
            .background(color)
    )
}

private fun buildOrderShortSummary(s: GroupStats): String {
    val active = s.total - s.errors
    val parts = mutableListOf<String>()
    parts.add("${s.found}/${active}")
    if (s.blanks > 0) parts.add("хол: ${s.blanks}")
    if (s.weightControls > 0) parts.add("ВК: ${s.weightControls}")
    if (s.postponed > 0) parts.add("отл: ${s.postponed}")
    if (s.errors > 0) parts.add("ош: ${s.errors}")
    return parts.joinToString(" · ")
}

// ====================================================================
// ПРАВАЯ ПАНЕЛЬ
// ====================================================================

private enum class ChartType(val title: String) {
    PIE("Круговая"),
    BARS("Столбцы"),
    WELLS("По скважинам")
}

private enum class ReportFormat(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    HTML("HTML", Icons.Filled.Language),
    EXCEL("Excel", Icons.Filled.TableView)
}

private const val SCROLL_TOP_THRESHOLD = 8

@Composable
private fun RightDetailsPanel(
    order: StatsOrderUi?,
    area: StatsAreaUi?,
    areaName: String?,
    drillStack: List<DrillLevel>,
    wellFilter: String,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onPushDrill: (DrillLevel) -> Unit,
    onPopDrill: () -> Unit,
    onPopToIndex: (Int) -> Unit,
    onResetDrill: () -> Unit,
    onWellFilterChange: (String) -> Unit,
    onReportHtml: (Long) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    when {
        order != null -> OrderDetailsPanel(
            order = order,
            areaName = areaName,
            drillStack = drillStack,
            wellFilter = wellFilter,
            showBackButton = showBackButton,
            onBack = onBack,
            onPushDrill = onPushDrill,
            onPopDrill = onPopDrill,
            onPopToIndex = onPopToIndex,
            onResetDrill = onResetDrill,
            onWellFilterChange = onWellFilterChange,
            onReportHtml = onReportHtml,
            snackbarHostState = snackbarHostState,
            modifier = modifier
        )
        area != null -> AreaDetailsPanel(
            area = area,
            showBackButton = showBackButton,
            onBack = onBack,
            snackbarHostState = snackbarHostState,
            modifier = modifier
        )
        else -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.TouchApp,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Выберите участок или наряд слева",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ====================================================================
// ПРАВАЯ ПАНЕЛЬ — УЧАСТОК
// ====================================================================

@Composable
private fun AreaDetailsPanel(
    area: StatsAreaUi,
    showBackButton: Boolean,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "area_header_${area.areaId}") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBackButton) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Участок",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        area.areaName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${area.orders.size} наряд(ов)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item(key = "area_progress_${area.areaId}") {
            ProgressBlockForStats(area.stats)
        }

        item(key = "area_summary_${area.areaId}") {
            SummaryBlockForStats(area.stats)
        }

        item(key = "area_div1_${area.areaId}") {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        item(key = "area_orders_label_${area.areaId}") {
            Text(
                "Наряды (${area.orders.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        items(area.orders, key = { "area_order_${it.orderId}" }) { o ->
            AreaOrderRow(o)
        }

        item(key = "area_actions_${area.areaId}") {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Отчёт по участку — в разработке")
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Filled.Description,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Отчёт по участку")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AreaOrderRow(o: StatsOrderUi) {
    val status = computeOrderStatus(o.stats)
    val active = (o.stats.total - o.stats.errors).coerceAtLeast(0)
    val progress = if (active > 0) o.stats.found.toFloat() / active.toFloat() else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(status)
            Spacer(Modifier.width(8.dp))
            Text(
                "Наряд №${o.orderNumber}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                buildOrderShortSummary(o.stats),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

@Composable
private fun ProgressBlockForStats(stats: GroupStats) {
    val active = (stats.total - stats.errors).coerceAtLeast(0)
    val progress = if (active > 0) stats.found.toFloat() / active.toFloat() else 0f
    val pct = (progress * 1000).toInt() / 10f

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$pct%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(10.dp))
            val extras = mutableListOf<String>()
            if (stats.errors > 0) extras.add("⚠ ${stats.errors} ошибок")
            if (stats.postponed > 0) extras.add("⏸ ${stats.postponed} отложено")
            if (extras.isNotEmpty()) {
                Text(
                    extras.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun SummaryBlockForStats(stats: GroupStats) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryInline("Всего", stats.total, null)
            SummaryInline("Найдено", stats.found, Color(0xFF2E7D32))
            SummaryInline("Не найдено", stats.notFound, Color(0xFFC62828))
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryInline("Холостые", stats.blanks, null)
            SummaryInline("ВК", stats.weightControls, null)
            SummaryInline("Отложено", stats.postponed, Color(0xFF1976D2))
            if (stats.errors > 0) SummaryInline("Ошибки", stats.errors, Color(0xFFC62828))
        }
    }
}

// ====================================================================
// ПРАВАЯ ПАНЕЛЬ — НАРЯД
// ====================================================================

@Composable
private fun OrderDetailsPanel(
    order: StatsOrderUi,
    areaName: String?,
    drillStack: List<DrillLevel>,
    wellFilter: String,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onPushDrill: (DrillLevel) -> Unit,
    onPopDrill: () -> Unit,
    onPopToIndex: (Int) -> Unit,
    onResetDrill: () -> Unit,
    onWellFilterChange: (String) -> Unit,
    onReportHtml: (Long) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    var chartType by remember { mutableStateOf(ChartType.PIE) }
    var samplesExpanded by rememberSaveable { mutableStateOf(true) }
    var showReportDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val showScrollTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > SCROLL_TOP_THRESHOLD }
    }

    val isDeepLevel = drillStack.isNotEmpty()
    val last = drillStack.lastOrNull()
    val isWellLevel = last is DrillLevel.Well

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "header_${order.orderId}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showBackButton && !isDeepLevel) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Наряд №${order.orderNumber}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (areaName != null) {
                            Text(
                                areaName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { showReportDialog = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Отчёт", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch { snackbarHostState.showSnackbar("Сравнение — в разработке") }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Filled.CompareArrows,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Сравнить", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            item(key = "progress_${order.orderId}") {
                ProgressBarBlock(order.stats)
            }
            item(key = "summary_${order.orderId}") {
                OrderSummaryRow(order.stats)
            }
            item(key = "div1_${order.orderId}") {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            if (isDeepLevel) {
                item(key = "crumbs_${order.orderId}") {
                    BreadcrumbsRow(
                        orderNumber = order.orderNumber,
                        drillStack = drillStack,
                        onRootClick = { onResetDrill() },
                        onIndexClick = { onPopToIndex(it) },
                        onBack = { onPopDrill() }
                    )
                }
            }

            if (!isWellLevel) {
                item(key = "chartdrop_${order.orderId}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Тип диаграммы:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        ChartTypeDropdown(
                            current = chartType,
                            onSelect = { chartType = it }
                        )
                    }
                }
            }

            item(key = "chart_${order.orderId}") {
                Spacer(Modifier.height(8.dp))
                ChartsArea(
                    allRows = order.group.rows,
                    drillStack = drillStack,
                    chartType = chartType,
                    wellFilter = wellFilter,
                    onWellFilterChange = onWellFilterChange,
                    onPushDrill = onPushDrill
                )
                Spacer(Modifier.height(8.dp))
            }

            item(key = "div2_${order.orderId}") {
                HorizontalDivider()
            }

            if (!isDeepLevel) {
                item(key = "toggle_${order.orderId}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { samplesExpanded = !samplesExpanded }) {
                            Icon(
                                if (samplesExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (samplesExpanded) "Свернуть список проб"
                                else "Развернуть список проб (${order.group.rows.size})"
                            )
                        }
                    }
                }

                if (samplesExpanded) {
                    item(key = "thead_${order.orderId}") {
                        TableHeadRow()
                    }
                    items(order.group.rows, key = { "row_${it.id}" }) { row ->
                        StatsSampleRow(row)
                    }
                }
            }

            item(key = "tailspace_${order.orderId}") {
                Spacer(Modifier.height(24.dp))
            }
        }

        if (showScrollTop) {
            SmallFloatingActionButton(
                onClick = {
                    scope.launch { listState.scrollToItem(0) }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Наверх")
            }
        }
    }

    if (showReportDialog) {
        ReportFormatDialog(
            onDismiss = { showReportDialog = false },
            onSelect = { format ->
                showReportDialog = false
                when (format) {
                    ReportFormat.HTML -> onReportHtml(order.orderId)
                    ReportFormat.EXCEL -> scope.launch {
                        snackbarHostState.showSnackbar("Excel-отчёт — в разработке")
                    }
                }
            }
        )
    }
}

// ====================================================================
// ХЛЕБНЫЕ КРОШКИ
// ====================================================================

@Composable
private fun BreadcrumbsRow(
    orderNumber: String,
    drillStack: List<DrillLevel>,
    onRootClick: () -> Unit,
    onIndexClick: (Int) -> Unit,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "Назад",
                modifier = Modifier.size(20.dp)
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Наряд №$orderNumber",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onRootClick() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            drillStack.forEachIndexed { idx, level ->
                Text(
                    "  ›  ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val label = levelLabel(level)
                val isLast = idx == drillStack.lastIndex
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                    color = if (isLast)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.primary,
                    modifier = if (isLast) {
                        Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    } else {
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onIndexClick(idx) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    }
                )
            }
        }
    }
}

private fun levelLabel(level: DrillLevel): String = when (level) {
    is DrillLevel.Root -> "Наряд"
    is DrillLevel.Category -> categoryLabel(level.kind)
    is DrillLevel.SubCategory -> subLabel(level.sub)
    is DrillLevel.Well -> level.well
}

// ====================================================================
// ОБЛАСТЬ ДИАГРАММЫ ПО УРОВНЮ
// ====================================================================

@Composable
private fun ChartsArea(
    allRows: List<SampleRow>,
    drillStack: List<DrillLevel>,
    chartType: ChartType,
    wellFilter: String,
    onWellFilterChange: (String) -> Unit,
    onPushDrill: (DrillLevel) -> Unit
) {
    val filteredRows = remember(allRows, drillStack) {
        filterRowsByDrillStack(allRows, drillStack)
    }
    val last = drillStack.lastOrNull()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            when {
                last is DrillLevel.Well -> {
                    Text(
                        "Пробы скважины ${last.well}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    if (filteredRows.isEmpty()) {
                        Text(
                            "Нет проб",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    } else {
                        TableHeadRow()
                        filteredRows.forEach { row -> StatsSampleRow(row) }
                    }
                }

                last is DrillLevel.Category &&
                        (last.kind == CategoryKey.POSTPONED ||
                                last.kind == CategoryKey.BLANK ||
                                last.kind == CategoryKey.CONTROL) -> {
                    WellFilterRow(wellFilter, onWellFilterChange)
                    Spacer(Modifier.height(8.dp))
                    WellProgressChart(
                        items = computeWellStats(filteredRows),
                        filter = wellFilter,
                        onWellClick = { well -> onPushDrill(DrillLevel.Well(well)) }
                    )
                }

                last is DrillLevel.SubCategory -> {
                    WellFilterRow(wellFilter, onWellFilterChange)
                    Spacer(Modifier.height(8.dp))
                    WellProgressChart(
                        items = computeWellStats(filteredRows),
                        filter = wellFilter,
                        onWellClick = { well -> onPushDrill(DrillLevel.Well(well)) }
                    )
                }

                last is DrillLevel.Category -> {
                    val data = computeSubCategoryData(filteredRows)
                    when (chartType) {
                        ChartType.PIE -> PieChart(
                            data = data,
                            onSliceClick = { payload ->
                                val sub = runCatching { SubKey.valueOf(payload) }.getOrNull()
                                if (sub != null) onPushDrill(DrillLevel.SubCategory(sub))
                            }
                        )
                        ChartType.BARS -> BarChart(
                            data = data,
                            onBarClick = { payload ->
                                val sub = runCatching { SubKey.valueOf(payload) }.getOrNull()
                                if (sub != null) onPushDrill(DrillLevel.SubCategory(sub))
                            }
                        )
                        ChartType.WELLS -> {
                            WellFilterRow(wellFilter, onWellFilterChange)
                            Spacer(Modifier.height(8.dp))
                            WellProgressChart(
                                items = computeWellStats(filteredRows),
                                filter = wellFilter,
                                onWellClick = { well -> onPushDrill(DrillLevel.Well(well)) }
                            )
                        }
                    }
                }

                else -> {
                    val data = computeCategoryData(filteredRows)
                    when (chartType) {
                        ChartType.PIE -> PieChart(
                            data = data,
                            onSliceClick = { payload ->
                                val key = runCatching { CategoryKey.valueOf(payload) }.getOrNull()
                                if (key != null) onPushDrill(DrillLevel.Category(key))
                            }
                        )
                        ChartType.BARS -> BarChart(
                            data = data,
                            onBarClick = { payload ->
                                val key = runCatching { CategoryKey.valueOf(payload) }.getOrNull()
                                if (key != null) onPushDrill(DrillLevel.Category(key))
                            }
                        )
                        ChartType.WELLS -> {
                            WellFilterRow(wellFilter, onWellFilterChange)
                            Spacer(Modifier.height(8.dp))
                            WellProgressChart(
                                items = computeWellStats(filteredRows),
                                filter = wellFilter,
                                onWellClick = { well -> onPushDrill(DrillLevel.Well(well)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WellFilterRow(
    value: String,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        placeholder = {
            Text(
                "Фильтр по скважине",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = { onChange("") },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Очистить",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall
    )
}

// ====================================================================
// ДИАЛОГ ОТЧЁТА
// ====================================================================

@Composable
private fun ReportFormatDialog(
    onDismiss: () -> Unit,
    onSelect: (ReportFormat) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Формат отчёта") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Из HTML-отчёта можно сохранить PDF: открыть в браузере → «Печать» → «Сохранить как PDF».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                ReportFormat.values().forEach { fmt ->
                    OutlinedButton(
                        onClick = { onSelect(fmt) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(fmt.icon, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(fmt.title)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

// ====================================================================
// ПРОГРЕСС + СВОДКА + БЕЙДЖ (для наряда)
// ====================================================================

@Composable
private fun ProgressBarBlock(stats: GroupStats) {
    val active = (stats.total - stats.errors).coerceAtLeast(0)
    val progress = if (active > 0) stats.found.toFloat() / active.toFloat() else 0f
    val pct = (progress * 1000).toInt() / 10f

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(stats)
            Spacer(Modifier.width(8.dp))
            Text(
                "$pct%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            val extras = mutableListOf<String>()
            if (stats.errors > 0) extras.add("⚠ ${stats.errors} ошибок")
            if (stats.postponed > 0) extras.add("⏸ ${stats.postponed} отложено")
            if (extras.isNotEmpty()) {
                Text(
                    extras.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
        )
    }
}

@Composable
private fun StatusBadge(stats: GroupStats) {
    val status = computeOrderStatus(stats)
    val bg = when (status) {
        OrderStatus.READY -> Color(0xFF2E7D32)
        OrderStatus.NEEDS_REVIEW -> Color(0xFF1976D2)
        OrderStatus.IN_PROGRESS -> Color(0xFFF9A825)
        OrderStatus.NOT_STARTED -> MaterialTheme.colorScheme.surfaceVariant
        OrderStatus.EMPTY -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when (status) {
        OrderStatus.NOT_STARTED, OrderStatus.EMPTY -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> Color.White
    }
    Surface(color = bg, shape = RoundedCornerShape(4.dp)) {
        Text(
            status.title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun OrderSummaryRow(stats: GroupStats) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryInline("Всего", stats.total, null)
            SummaryInline("Найдено", stats.found, Color(0xFF2E7D32))
            SummaryInline("Не найдено", stats.notFound, Color(0xFFC62828))
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryInline("Холостые", stats.blanks, null)
            SummaryInline("ВК", stats.weightControls, null)
            SummaryInline("Отложено", stats.postponed, Color(0xFF1976D2))
            if (stats.errors > 0) SummaryInline("Ошибки", stats.errors, Color(0xFFC62828))
        }
    }
}

@Composable
private fun SummaryInline(label: String, value: Int, color: Color?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label + ": ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
        Text(
            value.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color ?: MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartTypeDropdown(
    current: ChartType,
    onSelect: (ChartType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = current.title,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .width(180.dp)
                .height(48.dp),
            textStyle = MaterialTheme.typography.bodySmall
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ChartType.values().forEach { t ->
                DropdownMenuItem(
                    text = { Text(t.title) },
                    onClick = {
                        onSelect(t)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ====================================================================
// ТАБЛИЦА ПРОБ
// ====================================================================

@Composable
private fun TableHeadRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeadCell("п/п", 36.dp)
        HeadCell("№ пробы", 110.dp)
        HeadCell("Скважина", 90.dp)
        HeadCell("Интервал", 90.dp)
        HeadCell("Вес", 80.dp)
        HeadCell("Характеристика", 120.dp)
        HeadCell("Тип", 100.dp)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(28.dp))
    }
}

@Composable
private fun HeadCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(width)
    )
}

@Composable
private fun StatsSampleRow(row: SampleRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackgroundColor(row))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (row.serialNumber > 0) row.serialNumber.toString() else "—",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp)
        )
        Text(
            row.sampleNumber,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(110.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            row.wellNumber,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(90.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            buildIntervalText(row),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(90.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            buildWeightText(row),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(80.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            row.characteristic,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(120.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            displayType(row.type, row.status),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(100.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))

        if (row.weightControl) {
            Icon(
                Icons.Filled.Scale,
                contentDescription = "ВК",
                tint = Color(0xFF7B1FA2),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        if (row.postponed) {
            Icon(
                Icons.Filled.PauseCircle,
                contentDescription = "Отложена",
                tint = Color(0xFF1976D2),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun buildIntervalText(row: SampleRow): String {
    val from = row.intervalFrom
    val to = row.intervalTo
    return if (from == "—" && to == "—") "—" else "$from–$to"
}

private fun buildWeightText(row: SampleRow): String {
    val w = row.weight
    val cw = row.controlWeight
    return when {
        w != null && cw != null -> "$w ($cw)"
        w != null -> w.toString()
        cw != null -> "($cw)"
        else -> "—"
    }
}

@Composable
private fun rowBackgroundColor(row: SampleRow): Color {
    if (row.found) return Color(0xFFA5D6A7).copy(alpha = 0.35f)
    return when {
        row.hasImportError -> Color(0xFFEF9A9A).copy(alpha = 0.35f)
        row.postponed -> Color(0xFF90CAF9).copy(alpha = 0.35f)
        row.isBlank -> Color(0xFFFFF59D).copy(alpha = 0.35f)
        row.weightControl -> Color(0xFFCE93D8).copy(alpha = 0.30f)
        else -> Color.Transparent
    }
}
