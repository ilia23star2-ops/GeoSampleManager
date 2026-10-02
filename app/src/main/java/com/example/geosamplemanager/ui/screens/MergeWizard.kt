package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.merge.FieldDiff
import com.example.geosamplemanager.data.merge.FieldOwner
import com.example.geosamplemanager.data.merge.FieldResolution
import com.example.geosamplemanager.data.merge.MergePreview
import com.example.geosamplemanager.data.merge.MergeStats
import com.example.geosamplemanager.data.merge.MergeWizardState
import com.example.geosamplemanager.data.merge.SampleConflict
import com.example.geosamplemanager.data.merge.SampleField

/**
 * FIX 5.9-db-merge-v2/5:
 * Wizard слияния БД с умными конфликтами.
 *
 * Конфликты разрешаются по полям. Массовые кнопки + раскрывающиеся
 * карточки. Всё на русском.
 */
@Composable
fun MergeWizard(
    state: MergeWizardState,
    onContinue: () -> Unit,
    onSetField: (sampleId: Long, field: SampleField, owner: FieldOwner) -> Unit,
    onSetSample: (sampleId: Long, owner: FieldOwner) -> Unit,
    onMassAll: (owner: FieldOwner) -> Unit,
    onMassFillEmpty: () -> Unit,
    onMassByField: (field: SampleField, owner: FieldOwner) -> Unit,
    onConfirmConflicts: () -> Unit,
    onCancel: () -> Unit,
    onCloseDone: () -> Unit
) {
    when (state) {
        MergeWizardState.Idle -> Unit
        MergeWizardState.Loading -> LoadingDialog()
        is MergeWizardState.Preview -> PreviewDialog(
            state.preview, onContinue, onCancel
        )
        is MergeWizardState.ConflictStep -> ConflictsDialog(
            preview = state.preview,
            resolutions = state.resolutions,
            onSetField = onSetField,
            onSetSample = onSetSample,
            onMassAll = onMassAll,
            onMassFillEmpty = onMassFillEmpty,
            onMassByField = onMassByField,
            onConfirm = onConfirmConflicts,
            onCancel = onCancel
        )
        is MergeWizardState.Running -> ProgressDialog(state.message)
        is MergeWizardState.Done -> DoneDialog(state.stats, onCloseDone)
        is MergeWizardState.Error -> ErrorDialog(state.message, onCancel)
    }
}

@Composable
private fun LoadingDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Читаем архив…") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("Подготовка…")
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun PreviewDialog(
    preview: MergePreview,
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val s = preview.stats

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Слияние: ${preview.fileName}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Будет добавлено / совпадёт:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                StatRow("Участки", s.areasAdded, s.areasMatched)
                StatRow("Наряды", s.ordersAdded, s.ordersMatched)
                StatRow("Пробы (новые)", s.samplesAdded, 0)
                StatRow("Пробы (идентичные)", s.samplesIdentical, 0)
                StatRow("Скважины", s.wellsAdded, 0)
                StatRow("Заметки", s.notesAdded, s.notesConflicts)
                StatRow("Фото", s.photosAdded, 0)

                if (s.samplesConflicts > 0) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "Конфликтов по пробам: ${s.samplesConflicts}. " +
                                    "Разрешим по полям.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (expanded) "Свернуть подробности" else "Показать подробнее")
                }

                if (expanded) DetailsBlock(preview)
            }
        },
        confirmButton = {
            TextButton(onClick = onContinue) { Text("Продолжить") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Отмена") }
        }
    )
}

@Composable
private fun DetailsBlock(preview: MergePreview) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (preview.areaPlan.toAdd.isNotEmpty()) {
            Text(
                "Новые участки:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            for (a in preview.areaPlan.toAdd) Text(
                "· ${a.entity.areaName}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (preview.orderPlan.toAdd.isNotEmpty()) {
            Text(
                "Новые наряды:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            for (o in preview.orderPlan.toAdd.take(50)) Text(
                "· ${o.entity.orderNumber}",
                style = MaterialTheme.typography.bodySmall
            )
            if (preview.orderPlan.toAdd.size > 50) Text(
                "…и ещё ${preview.orderPlan.toAdd.size - 50}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun StatRow(label: String, added: Int, matched: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            "+$added",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
        if (matched > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                "= $matched",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ================================================================
// /5: конфликты
// ================================================================

@Composable
private fun ConflictsDialog(
    preview: MergePreview,
    resolutions: Map<Long, FieldResolution>,
    onSetField: (Long, SampleField, FieldOwner) -> Unit,
    onSetSample: (Long, FieldOwner) -> Unit,
    onMassAll: (FieldOwner) -> Unit,
    onMassFillEmpty: () -> Unit,
    onMassByField: (SampleField, FieldOwner) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val conflicts = preview.samplePlan.conflicts
    val totalResolved = conflicts.count { c ->
        resolutions[c.theirId]?.isFullyResolved(c.fieldDiffs) == true
    }
    val fieldCounts = remember(conflicts) { countFieldsByType(conflicts) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Конфликты (${totalResolved} из ${conflicts.size})") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Массовые кнопки.
                Text(
                    "Массово применить:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = { onMassAll(FieldOwner.MINE) },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("Мои везде", maxLines = 1) }
                    TextButton(
                        onClick = { onMassAll(FieldOwner.THEIRS) },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("Из архива везде", maxLines = 1) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = { onMassFillEmpty() },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("Заполнить пустые", maxLines = 1) }
                }

                // Сводка по типам расхождений.
                if (fieldCounts.isNotEmpty()) {
                    Text(
                        "Различия по полям:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    for ((field, count) in fieldCounts) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${field.label}: $count",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { onMassByField(field, FieldOwner.MINE) },
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) { Text("Мои", style = MaterialTheme.typography.labelSmall) }
                            TextButton(
                                onClick = { onMassByField(field, FieldOwner.THEIRS) },
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) { Text("Архив", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(conflicts, key = { it.theirId }) { c ->
                        ConflictRow(
                            conflict = c,
                            resolution = resolutions[c.theirId] ?: FieldResolution.Empty,
                            onSetField = onSetField,
                            onSetSample = onSetSample
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = totalResolved > 0,
                onClick = onConfirm
            ) {
                Text(
                    if (totalResolved == conflicts.size) "Продолжить"
                    else "Продолжить (остальные — Моя)"
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Отмена") }
        }
    )
}

@Composable
private fun ConflictRow(
    conflict: SampleConflict,
    resolution: FieldResolution,
    onSetField: (Long, SampleField, FieldOwner) -> Unit,
    onSetSample: (Long, FieldOwner) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val resolved = resolution.isFullyResolved(conflict.fieldDiffs)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
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
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
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

private fun countFieldsByType(
    conflicts: List<SampleConflict>
): Map<SampleField, Int> {
    val m = mutableMapOf<SampleField, Int>()
    for (c in conflicts) for (d in c.fieldDiffs) {
        m[d.field] = (m[d.field] ?: 0) + 1
    }
    return m
}

@Composable
private fun ProgressDialog(message: String) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Слияние…") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(message)
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun DoneDialog(stats: MergeStats, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Слияние завершено") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Добавлено:")
                Text("· участков: ${stats.areasAdded}")
                Text("· нарядов: ${stats.ordersAdded}")
                Text("· проб: ${stats.samplesAdded}")
                Text("· скважин: ${stats.wellsAdded}")
                Text("· заметок: ${stats.notesAdded}")
                Text("· фото: ${stats.photosAdded}")
                if (stats.samplesConflicts > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text("Разрешено конфликтов: ${stats.samplesConflicts}")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("OK") }
        }
    )
}

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ошибка слияния") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}