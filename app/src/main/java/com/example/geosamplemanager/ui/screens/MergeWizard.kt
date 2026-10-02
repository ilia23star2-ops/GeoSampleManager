package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.merge.ConflictResolution
import com.example.geosamplemanager.data.merge.MergePreview
import com.example.geosamplemanager.data.merge.MergeStats
import com.example.geosamplemanager.data.merge.MergeWizardState
import com.example.geosamplemanager.data.merge.SampleConflict

/**
 * FIX 5.9-db-merge-v2/4:
 * Wizard слияния БД.
 *
 * Шаги:
 *  1. Preview — сводка, кнопка «Продолжить».
 *  2. Conflicts — по одному, с общим режимом.
 *  3. Running — overlay с прогрессом.
 *  4. Done — финальный диалог со сводкой.
 */
@Composable
fun MergeWizard(
    state: MergeWizardState,
    onContinue: () -> Unit,
    onSetConflict: (sampleId: Long, resolution: ConflictResolution) -> Unit,
    onSetAllConflicts: (resolution: ConflictResolution) -> Unit,
    onCancel: () -> Unit,
    onCloseDone: () -> Unit
) {
    when (state) {
        MergeWizardState.Idle -> Unit
        MergeWizardState.Loading -> LoadingDialog()
        is MergeWizardState.Preview -> PreviewDialog(
            preview = state.preview,
            onContinue = onContinue,
            onCancel = onCancel
        )
        is MergeWizardState.ConflictStep -> ConflictDialog(
            preview = state.preview,
            currentIndex = state.currentIndex,
            onSetConflict = onSetConflict,
            onSetAllConflicts = onSetAllConflicts,
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
                StatRow("Пробы", s.samplesAdded, s.samplesMatched)
                StatRow("Скважины", s.wellsAdded, 0)
                StatRow("Заметки", s.notesAdded, s.notesConflicts)
                StatRow("Фото", s.photosAdded, 0)

                if (s.samplesMatched > 0) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "Конфликтов по пробам: ${s.samplesMatched}. " +
                                    "Разрешим пошагово.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (expanded) "Свернуть подробности" else "Показать подробнее")
                }

                if (expanded) {
                    DetailsBlock(preview)
                }
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
    val p = preview
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (p.areaPlan.toAdd.isNotEmpty()) {
            Text(
                "Новые участки:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            for (a in p.areaPlan.toAdd) {
                Text(
                    "· ${a.entity.areaName}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (p.orderPlan.toAdd.isNotEmpty()) {
            Text(
                "Новые наряды:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            for (o in p.orderPlan.toAdd.take(50)) {
                Text(
                    "· ${o.entity.orderNumber}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (p.orderPlan.toAdd.size > 50) {
                Text(
                    "…и ещё ${p.orderPlan.toAdd.size - 50}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (p.samplePlan.conflicts.isNotEmpty()) {
            Text(
                "Конфликты проб (${p.samplePlan.conflicts.size}):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            for (c in p.samplePlan.conflicts.take(50)) {
                Text(
                    "· ${c.sampleNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (p.samplePlan.conflicts.size > 50) {
                Text(
                    "…и ещё ${p.samplePlan.conflicts.size - 50}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, added: Int, matched: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
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

@Composable
private fun ConflictDialog(
    preview: MergePreview,
    currentIndex: Int,
    onSetConflict: (Long, ConflictResolution) -> Unit,
    onSetAllConflicts: (ConflictResolution) -> Unit,
    onCancel: () -> Unit
) {
    val conflicts = preview.samplePlan.conflicts
    if (currentIndex >= conflicts.size) {
        // Переходное состояние — сейчас запустится слияние.
        return
    }
    val c = conflicts[currentIndex]

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text("Конфликт ${currentIndex + 1} из ${conflicts.size}")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Проба: ${c.sampleNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            "Моя",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Вес: ${c.myEntity.weight ?: "—"}, " +
                                    "статус: ${c.myEntity.status}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            "Из архива",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Вес: ${c.theirEntity.weight ?: "—"}, " +
                                    "статус: ${c.theirEntity.status}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column {
                TextButton(
                    onClick = { onSetConflict(c.theirId, ConflictResolution.TAKE_THEIRS) }
                ) { Text("Из архива") }
                TextButton(
                    onClick = { onSetAllConflicts(ConflictResolution.TAKE_THEIRS) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text("Из архива для всех") }
            }
        },
        dismissButton = {
            Column {
                TextButton(
                    onClick = { onSetConflict(c.theirId, ConflictResolution.KEEP_MINE) }
                ) { Text("Моя") }
                TextButton(
                    onClick = { onSetAllConflicts(ConflictResolution.KEEP_MINE) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text("Моя для всех") }
            }
        }
    )
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
                if (stats.samplesMatched > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text("Разрешено конфликтов: ${stats.samplesMatched}")
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
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}