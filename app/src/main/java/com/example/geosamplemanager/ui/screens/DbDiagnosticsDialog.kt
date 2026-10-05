package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.diagnostics.DbDiagnosticsState
import com.example.geosamplemanager.data.diagnostics.DbIssue

/**
 * FIX 5.9-db-diagnostics:
 * Единый список проблем БД. Группировка по типу (заголовок +
 * карточки), чек-боксы, кнопка «Исправить выбранное».
 *
 * Перед исправлением автоматически создаётся авто-бэкап
 * pre_diagnostics_* (см. DbViewModel.applyDiagnosticsFixes).
 */
@Composable
fun DbDiagnosticsDialog(
    state: DbDiagnosticsState,
    onToggle: (String) -> Unit,
    onToggleAll: () -> Unit,
    onApply: () -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        is DbDiagnosticsState.Idle,
        is DbDiagnosticsState.Loading -> LoadingDialog(onDismiss)

        is DbDiagnosticsState.Ready -> ReadyDialog(
            state = state,
            onToggle = onToggle,
            onToggleAll = onToggleAll,
            onApply = onApply,
            onReload = onReload,
            onDismiss = onDismiss
        )

        is DbDiagnosticsState.Applying -> ApplyingDialog(state.message)

        is DbDiagnosticsState.Done -> DoneDialog(
            fixedCount = state.fixedCount,
            onReload = onReload,
            onDismiss = onDismiss
        )

        is DbDiagnosticsState.Error -> ErrorDialog(
            message = state.message,
            onDismiss = onDismiss
        )
    }
}

// ============================================================
// Loading
// ============================================================

@Composable
private fun LoadingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Диагностика БД") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(12.dp))
                Text("Проверяем данные…")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

// ============================================================
// Ready — основной экран
// ============================================================

@Composable
private fun ReadyDialog(
    state: DbDiagnosticsState.Ready,
    onToggle: (String) -> Unit,
    onToggleAll: () -> Unit,
    onApply: () -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Диагностика БД")
                if (state.issues.isNotEmpty()) {
                    Text(
                        "Проблем: ${state.issues.size} · " +
                                "выбрано: ${state.selectedIds.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (state.issues.isEmpty()) {
                    EmptyContent()
                } else {
                    SelectAllRow(
                        allSelected = state.allSelected,
                        total = state.issues.size,
                        onClick = onToggleAll
                    )
                    HorizontalDivider()
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        groupedItems(
                            issues = state.issues,
                            selectedIds = state.selectedIds,
                            onToggle = onToggle
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onApply,
                enabled = state.canApply
            ) {
                Icon(Icons.Default.Build, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Исправить")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReload) { Text("Обновить") }
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        }
    )
}

@Composable
private fun EmptyContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Проблем не найдено.",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Данные консистентны: наряды и пробы на месте, " +
                    "фото соответствуют флагам.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectAllRow(
    allSelected: Boolean,
    total: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = allSelected, onCheckedChange = { onClick() })
        Spacer(Modifier.width(4.dp))
        Text(
            if (allSelected) "Снять выбор со всех ($total)"
            else "Выбрать все ($total)",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// ============================================================
// Группировка + рендер списка
// ============================================================

private fun androidx.compose.foundation.lazy.LazyListScope.groupedItems(
    issues: List<DbIssue>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit
) {
    val groups = listOf(
        "Наряды без участка" to issues.filter { it is DbIssue.OrphanOrder },
        "Пробы без наряда" to issues.filter { it is DbIssue.OrphanSample },
        "Битые ссылки на фото" to issues.filter { it is DbIssue.BrokenPhotoLink },
        "Флаг «есть фото» не установлен" to
                issues.filter { it is DbIssue.PhotoFlagMismatch }
    )

    for ((title, list) in groups) {
        if (list.isEmpty()) continue
        item(key = "h_$title") {
            Text(
                "$title · ${list.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        items(list, key = { it.id }) { issue ->
            IssueRow(
                issue = issue,
                checked = issue.id in selectedIds,
                onClick = { onToggle(issue.id) }
            )
        }
    }
}

@Composable
private fun IssueRow(
    issue: DbIssue,
    checked: Boolean,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = { onClick() })
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    primaryText(issue),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    secondaryText(issue),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun primaryText(issue: DbIssue): String = when (issue) {
    is DbIssue.OrphanOrder -> "Наряд №${issue.orderNumber}"
    is DbIssue.OrphanSample ->
        "Проба ${issue.sampleNumber} · скв. ${issue.wellNumber}"
    is DbIssue.BrokenPhotoLink ->
        "Фото #${issue.imageId}"
    is DbIssue.PhotoFlagMismatch ->
        "Проба ${issue.sampleNumber}"
}

private fun secondaryText(issue: DbIssue): String = when (issue) {
    is DbIssue.OrphanOrder ->
        "Участок с id=${issue.orderId} отсутствует"
    is DbIssue.OrphanSample ->
        "Наряд с id=${issue.orderId} отсутствует"
    is DbIssue.BrokenPhotoLink ->
        "Файла нет: ${issue.imagePath}"
    is DbIssue.PhotoFlagMismatch ->
        "На диске ${issue.photoCount} фото, флаг не установлен"
}

// ============================================================
// Applying
// ============================================================

@Composable
private fun ApplyingDialog(message: String) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Диагностика БД") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(12.dp))
                Text(message)
            }
        },
        confirmButton = { }
    )
}

// ============================================================
// Done
// ============================================================

@Composable
private fun DoneDialog(
    fixedCount: Int,
    onReload: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Готово") },
        text = {
            Text("Исправлено: $fixedCount. Авто-бэкап сохранён.")
        },
        confirmButton = {
            TextButton(onClick = onReload) { Text("Проверить снова") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

// ============================================================
// Error
// ============================================================

@Composable
private fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ошибка") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}