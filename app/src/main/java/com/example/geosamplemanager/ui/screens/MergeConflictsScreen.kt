package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.merge.ConflictTreeNode
import com.example.geosamplemanager.data.merge.ConflictWellNode
import com.example.geosamplemanager.data.merge.FieldDiff
import com.example.geosamplemanager.data.merge.FieldOwner
import com.example.geosamplemanager.data.merge.FieldResolution
import com.example.geosamplemanager.data.merge.MassStrategy
import com.example.geosamplemanager.data.merge.MergeEngine
import com.example.geosamplemanager.data.merge.MergePreview
import com.example.geosamplemanager.data.merge.SampleConflict
import com.example.geosamplemanager.data.merge.SampleField

/**
 * FIX 5.9-db-merge-v2/7 (ui-fix-2):
 *  - если конфликтов <= AUTO_EXPAND_THRESHOLD (30) — дерево сразу
 *    развёрнуто на уровне наряд/скважина;
 *  - иначе — свёрнуто;
 *  - в шапке дерева — иконка «Развернуть всё / Свернуть всё».
 */
private const val AUTO_EXPAND_THRESHOLD = 30

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeConflictsScreen(
    preview: MergePreview,
    resolutions: Map<Long, FieldResolution>,
    onSetField: (Long, SampleField, FieldOwner) -> Unit,
    onSetSample: (Long, FieldOwner) -> Unit,
    onMassAll: (FieldOwner) -> Unit,
    onMassFillEmpty: () -> Unit,
    onMassByField: (SampleField, FieldOwner) -> Unit,
    onGroupMass: (List<SampleConflict>, MassStrategy) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val conflicts = preview.samplePlan.conflicts
    val totalResolved = conflicts.count { c ->
        resolutions[c.theirId]?.isFullyResolved(c.fieldDiffs) == true
    }
    val fieldCounts = remember(conflicts) { countFieldsByType(conflicts) }

    var fieldFilter by remember { mutableStateOf<SampleField?>(null) }
    val visibleConflicts = remember(conflicts, fieldFilter) {
        if (fieldFilter == null) conflicts
        else conflicts.filter { c -> c.fieldDiffs.any { it.field == fieldFilter } }
    }
    val tree = remember(visibleConflicts) {
        MergeEngine.buildConflictTree(visibleConflicts)
    }

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
                            Text("Конфликты")
                            Text(
                                "$totalResolved из ${conflicts.size} разрешено",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
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
                            ControlPanel(
                                fieldCounts = fieldCounts,
                                fieldFilter = fieldFilter,
                                onFilterChange = { fieldFilter = it },
                                onMassAll = onMassAll,
                                onMassFillEmpty = onMassFillEmpty,
                                onMassByField = onMassByField
                            )
                        }
                        ConflictsTree(
                            tree = tree,
                            resolutions = resolutions,
                            fieldFilter = fieldFilter,
                            onSetField = onSetField,
                            onSetSample = onSetSample,
                            onGroupMass = onGroupMass,
                            onClearFilter = { fieldFilter = null },
                            modifier = Modifier
                                .weight(0.7f)
                                .fillMaxHeight()
                        )
                    }
                } else {
                    NarrowContent(
                        fieldCounts = fieldCounts,
                        fieldFilter = fieldFilter,
                        onFilterChange = { fieldFilter = it },
                        onMassAll = onMassAll,
                        onMassFillEmpty = onMassFillEmpty,
                        onMassByField = onMassByField,
                        tree = tree,
                        resolutions = resolutions,
                        onSetField = onSetField,
                        onSetSample = onSetSample,
                        onGroupMass = onGroupMass,
                        onClearFilter = { fieldFilter = null },
                        modifier = Modifier.weight(1f)
                    )
                }

                BottomActionBar(
                    totalResolved = totalResolved,
                    totalCount = conflicts.size,
                    onCancel = onCancel,
                    onConfirm = onConfirm
                )
            }
        }
    }
}

// ================================================================
// Левая панель
// ================================================================

@Composable
private fun ControlPanel(
    fieldCounts: Map<SampleField, Int>,
    fieldFilter: SampleField?,
    onFilterChange: (SampleField?) -> Unit,
    onMassAll: (FieldOwner) -> Unit,
    onMassFillEmpty: () -> Unit,
    onMassByField: (SampleField, FieldOwner) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Массовые действия",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Button(
            onClick = { onMassAll(FieldOwner.MINE) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) { Text("Мои везде", maxLines = 1) }

        Button(
            onClick = { onMassAll(FieldOwner.THEIRS) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) { Text("Из архива везде", maxLines = 1) }

        OutlinedButton(
            onClick = onMassFillEmpty,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) { Text("Заполнить пустые", maxLines = 1) }

        if (fieldCounts.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                "Различия по полям",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Тап — фильтр списка справа",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            for ((field, count) in fieldCounts) {
                FieldGroupRow(
                    field = field,
                    count = count,
                    selected = fieldFilter == field,
                    onToggle = {
                        onFilterChange(if (fieldFilter == field) null else field)
                    },
                    onMassByField = onMassByField
                )
            }
        }
    }
}

@Composable
private fun FieldGroupRow(
    field: SampleField,
    count: Int,
    selected: Boolean,
    onToggle: () -> Unit,
    onMassByField: (SampleField, FieldOwner) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onToggle)
            Text(
                "${field.label}: $count",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.padding(start = 44.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TextButton(
                onClick = { onMassByField(field, FieldOwner.MINE) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) { Text("Мои", style = MaterialTheme.typography.labelSmall) }
            TextButton(
                onClick = { onMassByField(field, FieldOwner.THEIRS) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) { Text("Из архива", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

// ================================================================
// Правая область — дерево
// ================================================================

@Composable
private fun ConflictsTree(
    tree: List<ConflictTreeNode>,
    resolutions: Map<Long, FieldResolution>,
    fieldFilter: SampleField?,
    onSetField: (Long, SampleField, FieldOwner) -> Unit,
    onSetSample: (Long, FieldOwner) -> Unit,
    onGroupMass: (List<SampleConflict>, MassStrategy) -> Unit,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalConflicts = remember(tree) { tree.sumOf { it.allConflicts.size } }
    val autoExpand = totalConflicts in 1..AUTO_EXPAND_THRESHOLD

    // Ключи всех нарядов/скважин — для «Развернуть всё».
    val allOrderKeys = remember(tree) {
        tree.map { "${it.areaName}|${it.orderNumber}" }.toSet()
    }
    val allWellKeys = remember(tree) {
        tree.flatMap { n ->
            n.wells.map { w -> "${n.areaName}|${n.orderNumber}|${w.wellNumber}" }
        }.toSet()
    }

    var expandedOrders by remember(tree) {
        mutableStateOf(if (autoExpand) allOrderKeys else emptySet())
    }
    var expandedWells by remember(tree) {
        mutableStateOf(if (autoExpand) allWellKeys else emptySet())
    }
    var expandedSamples by remember { mutableStateOf(setOf<Long>()) }

    val allExpanded = expandedOrders.size == allOrderKeys.size &&
            expandedWells.size == allWellKeys.size

    Column(modifier = modifier.fillMaxSize()) {
        if (fieldFilter != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Фильтр: ${fieldFilter.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClearFilter) {
                        Text("Сбросить", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Шапка с иконкой «Развернуть всё / Свернуть всё».
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Наряды и скважины",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    if (allExpanded) {
                        expandedOrders = emptySet()
                        expandedWells = emptySet()
                    } else {
                        expandedOrders = allOrderKeys
                        expandedWells = allWellKeys
                    }
                }
            ) {
                Icon(
                    if (allExpanded) Icons.Default.UnfoldLess
                    else Icons.Default.UnfoldMore,
                    contentDescription = if (allExpanded) {
                        "Свернуть всё"
                    } else {
                        "Развернуть всё"
                    }
                )
            }
        }

        if (tree.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Нет конфликтов",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (order in tree) {
                    val orderKey = "${order.areaName}|${order.orderNumber}"
                    item(key = "order|$orderKey") {
                        OrderHeader(
                            node = order,
                            expanded = orderKey in expandedOrders,
                            onToggle = {
                                expandedOrders = toggle(expandedOrders, orderKey)
                            },
                            onGroupMass = onGroupMass
                        )
                    }
                    if (orderKey in expandedOrders) {
                        for (well in order.wells) {
                            val wellKey = "$orderKey|${well.wellNumber}"
                            item(key = "well|$wellKey") {
                                WellHeader(
                                    node = well,
                                    expanded = wellKey in expandedWells,
                                    onToggle = {
                                        expandedWells = toggle(expandedWells, wellKey)
                                    },
                                    onGroupMass = onGroupMass
                                )
                            }
                            if (wellKey in expandedWells) {
                                for (c in well.conflicts) {
                                    item(key = "sample|${c.theirId}") {
                                        ConflictRow(
                                            conflict = c,
                                            resolution = resolutions[c.theirId]
                                                ?: FieldResolution.Empty,
                                            expanded = c.theirId in expandedSamples,
                                            onToggle = {
                                                expandedSamples = toggle(
                                                    expandedSamples, c.theirId
                                                )
                                            },
                                            onSetField = onSetField,
                                            onSetSample = onSetSample
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
}

private fun <T> toggle(set: Set<T>, item: T): Set<T> =
    if (item in set) set - item else set + item

@Composable
private fun OrderHeader(
    node: ConflictTreeNode,
    expanded: Boolean,
    onToggle: () -> Unit,
    onGroupMass: (List<SampleConflict>, MassStrategy) -> Unit
) {
    val title = buildString {
        if (node.areaName.isNotBlank()) append("${node.areaName} · ")
        append("Наряд ")
        append(node.orderNumber.ifBlank { "—" })
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
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
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${node.allConflicts.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                GroupMassButtons(node.allConflicts, onGroupMass)
            }
        }
    }
}

@Composable
private fun WellHeader(
    node: ConflictWellNode,
    expanded: Boolean,
    onToggle: () -> Unit,
    onGroupMass: (List<SampleConflict>, MassStrategy) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
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
                    "Скв. ${node.wellNumber.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${node.conflicts.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Spacer(Modifier.height(4.dp))
                GroupMassButtons(node.conflicts, onGroupMass)
            }
        }
    }
}

@Composable
private fun GroupMassButtons(
    conflicts: List<SampleConflict>,
    onGroupMass: (List<SampleConflict>, MassStrategy) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TextButton(
            onClick = { onGroupMass(conflicts, MassStrategy.ALL_MINE) },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
        ) { Text("Мои", style = MaterialTheme.typography.labelSmall) }
        TextButton(
            onClick = { onGroupMass(conflicts, MassStrategy.ALL_THEIRS) },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
        ) { Text("Из архива", style = MaterialTheme.typography.labelSmall) }
        TextButton(
            onClick = { onGroupMass(conflicts, MassStrategy.FILL_EMPTY) },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
        ) { Text("Заполнить", style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun ConflictRow(
    conflict: SampleConflict,
    resolution: FieldResolution,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSetField: (Long, SampleField, FieldOwner) -> Unit,
    onSetSample: (Long, FieldOwner) -> Unit
) {
    val resolved = resolution.isFullyResolved(conflict.fieldDiffs)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    conflict.sampleNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (resolved) "✓" else "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (resolved) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    if (expanded) Icons.Default.ExpandLess
                    else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (expanded) {
                Spacer(Modifier.height(6.dp))
                for (d in conflict.fieldDiffs) {
                    FieldDiffRow(d, resolution.ownerOf(d.field)) { owner ->
                        onSetField(conflict.theirId, d.field, owner)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { onSetSample(conflict.theirId, FieldOwner.MINE) },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("Вся — моя", style = MaterialTheme.typography.labelSmall) }
                    TextButton(
                        onClick = { onSetSample(conflict.theirId, FieldOwner.THEIRS) },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("Вся — из архива", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

@Composable
private fun FieldDiffRow(
    diff: FieldDiff,
    currentOwner: FieldOwner?,
    onPick: (FieldOwner) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            diff.field.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Моя: ${diff.myDisplay}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Архив: ${diff.theirDisplay}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = currentOwner == FieldOwner.MINE,
                onClick = { onPick(FieldOwner.MINE) },
                label = { Text("Моя", style = MaterialTheme.typography.labelSmall) }
            )
            FilterChip(
                selected = currentOwner == FieldOwner.THEIRS,
                onClick = { onPick(FieldOwner.THEIRS) },
                label = { Text("Из архива", style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

// ================================================================
// Узкий экран: две вкладки
// ================================================================

@Composable
private fun NarrowContent(
    fieldCounts: Map<SampleField, Int>,
    fieldFilter: SampleField?,
    onFilterChange: (SampleField?) -> Unit,
    onMassAll: (FieldOwner) -> Unit,
    onMassFillEmpty: () -> Unit,
    onMassByField: (SampleField, FieldOwner) -> Unit,
    tree: List<ConflictTreeNode>,
    resolutions: Map<Long, FieldResolution>,
    onSetField: (Long, SampleField, FieldOwner) -> Unit,
    onSetSample: (Long, FieldOwner) -> Unit,
    onGroupMass: (List<SampleConflict>, MassStrategy) -> Unit,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableStateOf(0) }
    val totalConflicts = remember(tree) { tree.sumOf { it.allConflicts.size } }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("Массовые") }
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("Список ($totalConflicts)") }
            )
        }

        when (tab) {
            0 -> ControlPanel(
                fieldCounts = fieldCounts,
                fieldFilter = fieldFilter,
                onFilterChange = onFilterChange,
                onMassAll = onMassAll,
                onMassFillEmpty = onMassFillEmpty,
                onMassByField = onMassByField
            )
            else -> ConflictsTree(
                tree = tree,
                resolutions = resolutions,
                fieldFilter = fieldFilter,
                onSetField = onSetField,
                onSetSample = onSetSample,
                onGroupMass = onGroupMass,
                onClearFilter = onClearFilter,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ================================================================
// BottomBar
// ================================================================

@Composable
private fun BottomActionBar(
    totalResolved: Int,
    totalCount: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) { Text("Отмена") }

            Button(
                enabled = totalResolved > 0,
                onClick = onConfirm,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (totalResolved == totalCount) "Продолжить"
                    else "Продолжить ($totalResolved/$totalCount)"
                )
            }
        }
    }
}

// ================================================================
// Утилиты
// ================================================================

private fun countFieldsByType(
    conflicts: List<SampleConflict>
): Map<SampleField, Int> {
    val m = mutableMapOf<SampleField, Int>()
    for (c in conflicts) for (d in c.fieldDiffs) {
        m[d.field] = (m[d.field] ?: 0) + 1
    }
    return m
}