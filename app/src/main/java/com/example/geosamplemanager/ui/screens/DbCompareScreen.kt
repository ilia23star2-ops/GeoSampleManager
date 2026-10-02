package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.compare.CompareAreaNode
import com.example.geosamplemanager.data.compare.CompareFilterKey
import com.example.geosamplemanager.data.compare.CompareOrderNode
import com.example.geosamplemanager.data.compare.CompareResult
import com.example.geosamplemanager.data.compare.CompareTree

/**
 * FIX 5.9-db-compare (ui-fix-2):
 *  - правая часть — дерево Участок → Наряд → Проба/Скважина;
 *  - поиск по активной категории;
 *  - авторазворот при <= 30 элементов, иначе иконка
 *    «Развернуть всё / Свернуть всё».
 */
private const val AUTO_EXPAND_THRESHOLD = 30

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DbCompareScreen(
    result: CompareResult,
    onClose: () -> Unit
) {
    var filter by remember { mutableStateOf(CompareFilterKey.IN_ARCHIVE) }
    var query by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 600.dp

            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Column {
                            Text("Сравнение")
                            Text(
                                result.fileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть")
                        }
                    }
                )

                if (isWide) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(0.3f)
                                .fillMaxHeight(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 2.dp
                        ) {
                            SummaryPanel(
                                result = result,
                                filter = filter,
                                onFilterChange = {
                                    filter = it
                                    query = ""
                                }
                            )
                        }
                        DetailsPanel(
                            result = result,
                            filter = filter,
                            query = query,
                            onQueryChange = { query = it },
                            modifier = Modifier
                                .weight(0.7f)
                                .fillMaxHeight()
                        )
                    }
                } else {
                    NarrowContent(
                        result = result,
                        filter = filter,
                        onFilterChange = {
                            filter = it
                            query = ""
                        },
                        query = query,
                        onQueryChange = { query = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                BottomBar(onClose = onClose)
            }
        }
    }
}

// ================================================================
// Левая панель: сводки-фильтры
// ================================================================

@Composable
private fun SummaryPanel(
    result: CompareResult,
    filter: CompareFilterKey,
    onFilterChange: (CompareFilterKey) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Сводка",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        SummaryCard(
            title = "Только в архиве",
            stat = result.statFor(CompareFilterKey.IN_ARCHIVE),
            color = MaterialTheme.colorScheme.primaryContainer,
            active = filter == CompareFilterKey.IN_ARCHIVE,
            onClick = { onFilterChange(CompareFilterKey.IN_ARCHIVE) }
        )
        SummaryCard(
            title = "Совпадает",
            stat = result.statFor(CompareFilterKey.MATCHED),
            color = MaterialTheme.colorScheme.secondaryContainer,
            active = filter == CompareFilterKey.MATCHED,
            onClick = { onFilterChange(CompareFilterKey.MATCHED) }
        )
        SummaryCard(
            title = "Отличается",
            stat = result.statFor(CompareFilterKey.DIFFERENT),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            active = filter == CompareFilterKey.DIFFERENT,
            onClick = { onFilterChange(CompareFilterKey.DIFFERENT) }
        )
        SummaryCard(
            title = "Только в текущей БД",
            stat = result.statFor(CompareFilterKey.MY_ONLY),
            color = MaterialTheme.colorScheme.errorContainer,
            active = filter == CompareFilterKey.MY_ONLY,
            onClick = { onFilterChange(CompareFilterKey.MY_ONLY) }
        )
    }
}

@Composable
private fun SummaryCard(
    title: String,
    stat: com.example.geosamplemanager.data.compare.CompareStatLine,
    color: Color,
    active: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = color),
        border = if (active) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    buildStatSummary(stat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (active) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        )
                )
            }
        }
    }
}

private fun buildStatSummary(
    stat: com.example.geosamplemanager.data.compare.CompareStatLine
): String {
    val parts = mutableListOf<String>()
    if (stat.samples > 0) parts += "пробы ${stat.samples}"
    if (stat.orders > 0) parts += "наряды ${stat.orders}"
    if (stat.areas > 0) parts += "участки ${stat.areas}"
    if (stat.wells > 0) parts += "скважины ${stat.wells}"
    return if (parts.isEmpty()) "нет" else parts.joinToString(" · ")
}

// ================================================================
// Правая панель: поиск + дерево
// ================================================================

@Composable
private fun DetailsPanel(
    result: CompareResult,
    filter: CompareFilterKey,
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val rawTree = result.treeFor(filter)
    val tree = remember(rawTree, query) {
        if (query.isBlank()) rawTree
        else filterTree(rawTree, query)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Text(
                filterTitle(filter),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        SearchField(query = query, onQueryChange = onQueryChange)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            if (tree.isEmpty) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (query.isBlank()) filterEmptyText(filter)
                        else "Ничего не найдено",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                CompareTreeView(tree)
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
            Text("Поиск: участок, наряд, проба, скважина")
        },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Очистить")
                }
            }
        },
        singleLine = true
    )
}

@Composable
private fun CompareTreeView(tree: CompareTree) {
    val totalItems = tree.totalOrders + tree.totalSamples + tree.totalWells
    val autoExpand = totalItems in 1..AUTO_EXPAND_THRESHOLD

    val allAreaKeys = remember(tree) { tree.areas.map { it.areaName }.toSet() }
    val allOrderKeys = remember(tree) {
        tree.areas.flatMap { a ->
            a.orders.map { o -> "${a.areaName}|${o.orderNumber}" }
        }.toSet()
    }

    var expandedAreas by remember(tree) {
        mutableStateOf(if (autoExpand) allAreaKeys else emptySet())
    }
    var expandedOrders by remember(tree) {
        mutableStateOf(if (autoExpand) allOrderKeys else emptySet())
    }
    val allExpanded = expandedAreas.size == allAreaKeys.size &&
            expandedOrders.size == allOrderKeys.size

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Участки и наряды",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                if (allExpanded) {
                    expandedAreas = emptySet()
                    expandedOrders = emptySet()
                } else {
                    expandedAreas = allAreaKeys
                    expandedOrders = allOrderKeys
                }
            }) {
                Icon(
                    if (allExpanded) Icons.Default.UnfoldLess
                    else Icons.Default.UnfoldMore,
                    contentDescription = if (allExpanded)
                        "Свернуть всё" else "Развернуть всё"
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (area in tree.areas) {
                item(key = "area|${area.areaName}") {
                    AreaHeader(
                        node = area,
                        expanded = area.areaName in expandedAreas,
                        onToggle = {
                            expandedAreas = toggle(expandedAreas, area.areaName)
                        }
                    )
                }
                if (area.areaName in expandedAreas) {
                    for (order in area.orders) {
                        val orderKey = "${area.areaName}|${order.orderNumber}"
                        item(key = "order|$orderKey") {
                            OrderHeader(
                                node = order,
                                expanded = orderKey in expandedOrders,
                                onToggle = {
                                    expandedOrders = toggle(expandedOrders, orderKey)
                                }
                            )
                        }
                        if (orderKey in expandedOrders) {
                            for (s in order.samples) {
                                item(key = "sample|$orderKey|${s.sampleNumber}") {
                                    LeafRow(
                                        text = "Проба ${s.sampleNumber}",
                                        note = s.note
                                    )
                                }
                            }
                            for (w in order.wells) {
                                item(key = "well|$orderKey|${w.wellNumber}") {
                                    LeafRow(text = "Скважина ${w.wellNumber}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun <T> toggle(set: Set<T>, item: T): Set<T> =
    if (item in set) set - item else set + item

@Composable
private fun AreaHeader(
    node: CompareAreaNode,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (expanded) Icons.Default.ExpandLess
                else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Участок: ${node.areaName}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${node.orders.size} наряд(ов)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun OrderHeader(
    node: CompareOrderNode,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (expanded) Icons.Default.ExpandLess
                else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "Наряд ${node.orderNumber}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val parts = mutableListOf<String>()
            if (node.samples.isNotEmpty()) parts += "пробы ${node.samples.size}"
            if (node.wells.isNotEmpty()) parts += "скв. ${node.wells.size}"
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LeafRow(text: String, note: String? = null) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!note.isNullOrBlank()) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ================================================================
// Фильтрация дерева поиском
// ================================================================

private fun filterTree(tree: CompareTree, query: String): CompareTree {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return tree

    val result = mutableListOf<CompareAreaNode>()
    for (area in tree.areas) {
        val areaMatches = area.areaName.lowercase().contains(q)
        val newOrders = mutableListOf<CompareOrderNode>()
        for (order in area.orders) {
            val orderMatches = order.orderNumber.lowercase().contains(q)
            val newSamples = order.samples.filter {
                areaMatches || orderMatches ||
                        it.sampleNumber.lowercase().contains(q)
            }
            val newWells = order.wells.filter {
                areaMatches || orderMatches ||
                        it.wellNumber.lowercase().contains(q)
            }
            if (areaMatches || orderMatches ||
                newSamples.isNotEmpty() || newWells.isNotEmpty()
            ) {
                newOrders += order.copy(
                    samples = newSamples,
                    wells = newWells
                )
            }
        }
        if (areaMatches || newOrders.isNotEmpty()) {
            result += area.copy(orders = newOrders)
        }
    }
    return CompareTree(areas = result)
}

// ================================================================
// Узкий экран: две вкладки
// ================================================================

@Composable
private fun NarrowContent(
    result: CompareResult,
    filter: CompareFilterKey,
    onFilterChange: (CompareFilterKey) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("Сводка") }
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("Подробности") }
            )
        }

        when (tab) {
            0 -> SummaryPanel(
                result = result,
                filter = filter,
                onFilterChange = {
                    onFilterChange(it)
                    tab = 1
                }
            )
            else -> DetailsPanel(
                result = result,
                filter = filter,
                query = query,
                onQueryChange = onQueryChange,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ================================================================
// BottomBar
// ================================================================

@Composable
private fun BottomBar(onClose: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Закрыть") }
        }
    }
}

// ================================================================
// Утилиты
// ================================================================

private fun filterTitle(filter: CompareFilterKey): String = when (filter) {
    CompareFilterKey.IN_ARCHIVE -> "Только в архиве"
    CompareFilterKey.MATCHED -> "Совпадает"
    CompareFilterKey.DIFFERENT -> "Отличается"
    CompareFilterKey.MY_ONLY -> "Только в текущей БД"
}

private fun filterEmptyText(filter: CompareFilterKey): String = when (filter) {
    CompareFilterKey.IN_ARCHIVE -> "Ничего нового"
    CompareFilterKey.MATCHED -> "Ничего не совпало"
    CompareFilterKey.DIFFERENT -> "Различий нет"
    CompareFilterKey.MY_ONLY -> "Ничего своего, чего не было бы в архиве"
}