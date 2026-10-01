package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * FIX 5.9-multi-report-ui/2 (01.10.2026):
 * Полноэкранный экран выбора нарядов для мульти-отчёта.
 */

enum class MultiReportFormat(val title: String) {
    EXCEL("Экспорт в Excel"),
    HTML("Экспорт в HTML")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiReportScreen(
    data: StatsData,
    initialAreaId: Long?,
    detectDuplicates: (List<Long>) -> List<DuplicateSheetGroup>,
    onExport: (orderIds: List<Long>, format: MultiReportFormat, skipDuplicates: Boolean) -> Unit,
    onClose: () -> Unit
) {
    var filterQuery by remember { mutableStateOf("") }
    var selectedOrderIds by remember {
        mutableStateOf<Set<Long>>(emptySet())
    }
    var expandedAreaIds by remember {
        val initial = if (initialAreaId != null) setOf(initialAreaId) else emptySet()
        mutableStateOf(initial)
    }

    LaunchedEffect(initialAreaId, data) {
        if (initialAreaId != null && selectedOrderIds.isEmpty()) {
            val area = data.areas.firstOrNull { it.areaId == initialAreaId }
            if (area != null) {
                selectedOrderIds = area.orders.map { it.orderId }.toSet()
            }
        }
    }

    var pendingDuplicates by remember {
        mutableStateOf<List<DuplicateSheetGroup>?>(null)
    }
    var pendingFormat by remember { mutableStateOf<MultiReportFormat?>(null) }
    var pendingOrderIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val normQuery = filterQuery.trim().lowercase()
    val visibleAreas: List<StatsAreaUi> = remember(data, normQuery) {
        if (normQuery.isEmpty()) data.areas
        else data.areas.filter { area ->
            area.areaName.lowercase().contains(normQuery) ||
                    area.orders.any { it.orderNumber.lowercase().contains(normQuery) }
        }
    }

    val totalSelected = selectedOrderIds.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Мультиотчёт")
                        Text(
                            "Выбрано: $totalSelected наряд(ов)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Отмена")
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = {
                        if (totalSelected == 0) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "Выберите хотя бы один наряд"
                                )
                            }
                        } else {
                            startExport(
                                MultiReportFormat.EXCEL,
                                selectedOrderIds.toList(),
                                detectDuplicates,
                                onExport,
                                onNeedsDialog = { dups, ids ->
                                    pendingFormat = MultiReportFormat.EXCEL
                                    pendingOrderIds = ids
                                    pendingDuplicates = dups
                                }
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Filled.TableView,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Excel", fontSize = 12.sp)
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = {
                        if (totalSelected == 0) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "Выберите хотя бы один наряд"
                                )
                            }
                        } else {
                            startExport(
                                MultiReportFormat.HTML,
                                selectedOrderIds.toList(),
                                detectDuplicates,
                                onExport,
                                onNeedsDialog = { dups, ids ->
                                    pendingFormat = MultiReportFormat.HTML
                                    pendingOrderIds = ids
                                    pendingDuplicates = dups
                                }
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Filled.Language,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("HTML", fontSize = 12.sp)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = {
                    Text(
                        "Поиск участка или наряда",
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
                    if (filterQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { filterQuery = "" },
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val allOrderIds = data.areas.flatMap { it.orders.map { o -> o.orderId } }
                TextButton(
                    onClick = { selectedOrderIds = allOrderIds.toSet() },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Выбрать все", fontSize = 12.sp)
                }
                TextButton(
                    onClick = { selectedOrderIds = emptySet() },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Снять все", fontSize = 12.sp)
                }
            }

            HorizontalDivider()

            if (visibleAreas.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (normQuery.isEmpty()) "Нет данных"
                        else "Ничего не найдено по запросу «$filterQuery»",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    visibleAreas.forEach { area ->
                        val expanded = area.areaId in expandedAreaIds
                        val areaOrderIds = area.orders.map { it.orderId }
                        val selectedInArea = areaOrderIds.count { it in selectedOrderIds }
                        val allSelected = areaOrderIds.isNotEmpty() &&
                                selectedInArea == areaOrderIds.size
                        val someSelected = selectedInArea in 1 until areaOrderIds.size

                        item(key = "area_${area.areaId}") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant
                                            .copy(alpha = 0.4f)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        expandedAreaIds = if (expanded)
                                            expandedAreaIds - area.areaId
                                        else expandedAreaIds + area.areaId
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        if (expanded) Icons.Filled.ExpandLess
                                        else Icons.Filled.ExpandMore,
                                        contentDescription = if (expanded) "Свернуть" else "Развернуть",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                TriStateCheckbox(
                                    state = when {
                                        allSelected -> ToggleableState.On
                                        someSelected -> ToggleableState.Indeterminate
                                        else -> ToggleableState.Off
                                    },
                                    onClick = {
                                        selectedOrderIds = if (allSelected) {
                                            selectedOrderIds - areaOrderIds.toSet()
                                        } else {
                                            selectedOrderIds + areaOrderIds.toSet()
                                        }
                                    }
                                )
                                Spacer(Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        area.areaName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${area.orders.size} наряд(ов) · " +
                                                "выбрано: $selectedInArea",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        if (expanded) {
                            items(
                                area.orders,
                                key = { "order_${it.orderId}" }
                            ) { order ->
                                val checked = order.orderId in selectedOrderIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 44.dp, end = 12.dp, top = 2.dp, bottom = 2.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            selectedOrderIds = if (checked)
                                                selectedOrderIds - order.orderId
                                            else selectedOrderIds + order.orderId
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { on ->
                                            selectedOrderIds = if (on)
                                                selectedOrderIds + order.orderId
                                            else selectedOrderIds - order.orderId
                                        }
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Наряд №${order.orderNumber}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            buildOrderShortSummary(order.stats),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                    }
                                    StatusDotSmall(order.stats)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val dup = pendingDuplicates
    if (dup != null && pendingFormat != null) {
        DuplicateNamesDialog(
            duplicates = dup,
            onCancel = {
                pendingDuplicates = null
                pendingFormat = null
                pendingOrderIds = emptyList()
            },
            onSkip = {
                val fmt = pendingFormat!!
                val ids = pendingOrderIds
                pendingDuplicates = null
                pendingFormat = null
                pendingOrderIds = emptyList()
                onExport(ids, fmt, true)
            },
            onMerge = {
                val fmt = pendingFormat!!
                val ids = pendingOrderIds
                pendingDuplicates = null
                pendingFormat = null
                pendingOrderIds = emptyList()
                onExport(ids, fmt, false)
            }
        )
    }
}

@Composable
private fun StatusDotSmall(stats: GroupStats) {
    val status = computeOrderStatus(stats)
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

private fun startExport(
    format: MultiReportFormat,
    orderIds: List<Long>,
    detectDuplicates: (List<Long>) -> List<DuplicateSheetGroup>,
    onExport: (List<Long>, MultiReportFormat, Boolean) -> Unit,
    onNeedsDialog: (List<DuplicateSheetGroup>, List<Long>) -> Unit
) {
    val dups = detectDuplicates(orderIds)
    if (dups.isEmpty()) {
        onExport(orderIds, format, false)
    } else {
        onNeedsDialog(dups, orderIds)
    }
}

@Composable
private fun DuplicateNamesDialog(
    duplicates: List<DuplicateSheetGroup>,
    onCancel: () -> Unit,
    onSkip: () -> Unit,
    onMerge: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Совпадение имён листов") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "У нескольких нарядов одинаковое имя листа " +
                            "(например, участок и номер совпадают). " +
                            "Excel не разрешает два листа с одним именем.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                duplicates.forEach { group ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "«${group.sheetName}»",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Наряды: ${group.orderTitles.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
                Text(
                    "«Объединить с суффиксом» — всем нарядам достанутся " +
                            "листы, второму добавится « (2)» и т.д. " +
                            "«Пропустить дубли» — останется только первый наряд " +
                            "из каждой группы.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onMerge) {
                Text("Объединить с суффиксом")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) { Text("Отмена") }
                TextButton(onClick = onSkip) { Text("Пропустить дубли") }
            }
        }
    )
}