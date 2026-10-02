package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geosamplemanager.data.backup.BackupManagerStats
import com.example.geosamplemanager.data.backup.BackupSource
import com.example.geosamplemanager.data.backup.RollbackBackup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FIX 5.9-db-backup-manager:
 * Диалог управления всеми бэкапами.
 *
 * FIX 5.9-db-backup-manager (fix):
 *  - компактные кнопки;
 *  - удаление одного стирает обе копии.
 *
 * FIX 5.9-db-backups-fix:
 *  - кнопка «Удалить авто» — только авто-бэкапы;
 *  - экспорты остаются.
 */
@Composable
fun BackupManagerDialog(
    backups: List<RollbackBackup>,
    loading: Boolean,
    onDelete: (RollbackBackup) -> Unit,
    onDeleteOld: () -> Unit,
    onDeleteAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale("ru", "RU"))
    val summary = remember(backups) { BackupManagerStats.summarize(backups) }

    var pendingDelete by remember { mutableStateOf<RollbackBackup?>(null) }
    var showDeleteOld by remember { mutableStateOf(false) }
    var showDeleteAll by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Управление бэкапами") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            "Всего: ${summary.totalCount} · " +
                                    BackupManagerStats.formatSize(
                                        summary.totalSizeBytes
                                    ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Внутренних: ${summary.privateCount} · " +
                                    "В Загрузках: ${summary.publicCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Авто: ${summary.autoCount} · " +
                                    "Экспорт: ${summary.exportCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showDeleteOld = true },
                        enabled = !loading && backups.isNotEmpty(),
                        contentPadding = PaddingValues(
                            horizontal = 8.dp, vertical = 4.dp
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Удалить старые",
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    TextButton(
                        onClick = { showDeleteAll = true },
                        enabled = !loading && backups.isNotEmpty(),
                        contentPadding = PaddingValues(
                            horizontal = 8.dp, vertical = 4.dp
                        ),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Удалить авто",
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                when {
                    loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                    backups.isEmpty() -> Text(
                        "Бэкапов нет.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(backups, key = { it.fileName + "|" + it.source.name }) { b ->
                            ManagerRow(b, dateFormat) { pendingDelete = b }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )

    pendingDelete?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить бэкап?") },
            text = {
                Text(
                    "Все копии «${backup.fileName}» будут удалены — " +
                            "и внутренняя, и в Загрузках (если есть)."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(backup)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Отмена") }
            }
        )
    }

    if (showDeleteOld) {
        AlertDialog(
            onDismissRequest = { showDeleteOld = false },
            title = { Text("Удалить старые?") },
            text = {
                Text(
                    "Оставим по 5 последних бэкапов для каждой операции " +
                            "(импорт, откат, очистка). Внутренние и в " +
                            "Загрузках — отдельно. Экспорты не тронем."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteOld = false
                        onDeleteOld()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteOld = false }) { Text("Отмена") }
            }
        )
    }

    if (showDeleteAll) {
        var confirmed by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text("Удалить все авто-бэкапы?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Все авто-бэкапы (внутренние и в Загрузках) будут " +
                                "удалены безвозвратно. Экспорты останутся — " +
                                "их можно удалить по одному крестиком. " +
                                "Текущая БД не тронута, но откатиться будет " +
                                "некуда."
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { confirmed = !confirmed },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = confirmed,
                            onCheckedChange = { confirmed = !confirmed }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Понимаю, что удалю все авто-бэкапы",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = confirmed,
                    onClick = {
                        showDeleteAll = false
                        onDeleteAll()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Удалить авто") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAll = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun ManagerRow(
    backup: RollbackBackup,
    dateFormat: SimpleDateFormat,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    backup.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            sourceLabel(backup.source),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(
                                horizontal = 6.dp,
                                vertical = 2.dp
                            )
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        dateFormat.format(Date(backup.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        BackupManagerStats.formatSize(backup.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun sourceLabel(source: BackupSource): String = when (source) {
    BackupSource.PRIVATE -> "Внутренний"
    BackupSource.PUBLIC -> "Загрузки"
}