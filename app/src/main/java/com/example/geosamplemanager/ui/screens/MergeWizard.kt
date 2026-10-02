package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.merge.FieldOwner
import com.example.geosamplemanager.data.merge.MassStrategy
import com.example.geosamplemanager.data.merge.MergePreview
import com.example.geosamplemanager.data.merge.MergeStats
import com.example.geosamplemanager.data.merge.MergeWizardState
import com.example.geosamplemanager.data.merge.SampleConflict
import com.example.geosamplemanager.data.merge.SampleField

/**
 * FIX 5.9-db-merge-v2/7:
 *  - добавлен колбэк onGroupMass (массовые действия для групп).
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
    onGroupMass: (conflicts: List<SampleConflict>, strategy: MassStrategy) -> Unit,
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
        is MergeWizardState.ConflictStep -> MergeConflictsScreen(
            preview = state.preview,
            resolutions = state.resolutions,
            onSetField = onSetField,
            onSetSample = onSetSample,
            onMassAll = onMassAll,
            onMassFillEmpty = onMassFillEmpty,
            onMassByField = onMassByField,
            onGroupMass = onGroupMass,
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
                StatRow("Пробы (конфликты)", s.samplesConflicts, 0)
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        DetailsSection(
            title = "Участки",
            newCount = preview.areaPlan.toAdd.size,
            identicalCount = preview.areaPlan.existing.size,
            conflictCount = 0
        ) {
            if (preview.areaPlan.toAdd.isNotEmpty()) {
                Text(
                    "Новые:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                for (a in preview.areaPlan.toAdd) Text(
                    "· ${a.entity.areaName}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (preview.areaPlan.existing.isNotEmpty()) {
                Text(
                    "Совпали по имени: ${preview.areaPlan.existing.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (preview.areaPlan.duplicatesInMine.isNotEmpty()) {
                Text(
                    "⚠ У меня дубликаты: " +
                            preview.areaPlan.duplicatesInMine.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        DetailsSection(
            title = "Наряды",
            newCount = preview.orderPlan.toAdd.size,
            identicalCount = preview.orderPlan.existing.size,
            conflictCount = 0
        ) {
            if (preview.orderPlan.toAdd.isNotEmpty()) {
                Text(
                    "Новые:",
                    style = MaterialTheme.typography.labelSmall,
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
            if (preview.orderPlan.existing.isNotEmpty()) {
                Text(
                    "Совпали по номеру: ${preview.orderPlan.existing.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (preview.orderPlan.skippedOrphans > 0) {
                Text(
                    "⚠ Пропущено (участок не найден): " +
                            preview.orderPlan.skippedOrphans,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        DetailsSection(
            title = "Пробы",
            newCount = preview.samplePlan.toAdd.size,
            identicalCount = preview.samplePlan.identical.size,
            conflictCount = preview.samplePlan.conflicts.size
        ) {
            if (preview.samplePlan.conflicts.isNotEmpty()) {
                Text(
                    "Конфликты:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                for (c in preview.samplePlan.conflicts.take(50)) {
                    Text(
                        "· ${c.sampleNumber} " +
                                "(${c.fieldDiffs.joinToString { it.field.label }})",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (preview.samplePlan.conflicts.size > 50) Text(
                    "…и ещё ${preview.samplePlan.conflicts.size - 50}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        DetailsSection(
            title = "Скважины",
            newCount = preview.wellPlan.toAdd.size,
            identicalCount = 0,
            conflictCount = 0
        ) {}

        DetailsSection(
            title = "Заметки",
            newCount = preview.notePlan.toAdd.size,
            identicalCount = 0,
            conflictCount = preview.notePlan.conflicts.size
        ) {
            if (preview.notePlan.conflicts.isNotEmpty()) {
                Text(
                    "Различаются: ${preview.notePlan.conflicts.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DetailsSection(
            title = "Фото",
            newCount = preview.photoPlan.toAdd.size,
            identicalCount = 0,
            conflictCount = 0
        ) {}
    }
}

@Composable
private fun DetailsSection(
    title: String,
    newCount: Int,
    identicalCount: Int,
    conflictCount: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    val summary = buildString {
        append("новых: $newCount")
        if (identicalCount > 0) append(" · совпали: $identicalCount")
        if (conflictCount > 0) append(" · конфликты: $conflictCount")
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
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