package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.backup.BackupManifest
import com.example.geosamplemanager.data.backup.BackupSource
import com.example.geosamplemanager.data.backup.GsmBackupReader
import com.example.geosamplemanager.data.backup.RollbackBackup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FIX 5.9-db-rollback-public:
 * Диалог выбора авто-бэкапа для отката.
 *  - список объединяет приватные и публичные pre_*;
 *  - в строке: имя файла, плашка операции, метка источника,
 *    дата, размер, счётчики;
 *  - приватный в приоритете при совпадении имени.
 */
@Composable
fun DbRollbackDialog(
    backups: List<RollbackBackup>,
    loading: Boolean,
    onSelect: (RollbackBackup) -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale("ru", "RU"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Откат к авто-бэкапу") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (backups.isEmpty()) {
                    Text(
                        "Авто-бэкапов нет. Они создаются автоматически " +
                                "перед импортом, откатом и очисткой БД.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Найдено ${backups.size}. Выберите точку отката:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(backups, key = { it.fileName }) { b ->
                            RollbackBackupRow(b, dateFormat) { onSelect(b) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
private fun RollbackBackupRow(
    backup: RollbackBackup,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Имя файла — первой строкой.
            Text(
                backup.fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = operationColor(backup.operation),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        operationLabel(backup.operation),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        sourceLabel(backup.source),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    dateFormat.format(Date(backup.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                formatSize(backup.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val mf = backup.manifest
            if (mf != null) {
                Text(
                    "Пробы: ${mf.samples}, наряды: ${mf.orders}, " +
                            "фото: ${mf.photos}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Манифест не прочитан",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun sourceLabel(source: BackupSource): String = when (source) {
    BackupSource.PRIVATE -> "Внутренний"
    BackupSource.PUBLIC -> "Загрузки"
}

private fun operationLabel(operation: String): String = when (operation) {
    "restore" -> "Импорт"
    "rollback" -> "Откат"
    "clean" -> "Очистка"
    else -> "Авто"
}

@Composable
private fun operationColor(operation: String) = when (operation) {
    "restore" -> MaterialTheme.colorScheme.primaryContainer
    "rollback" -> MaterialTheme.colorScheme.tertiaryContainer
    "clean" -> MaterialTheme.colorScheme.errorContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}

/**
 * Второй шаг подтверждения — без изменений.
 */
@Composable
fun DbRollbackConfirmDialog(
    fileName: String,
    manifest: BackupManifest?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale("ru", "RU"))
    val schemaOk = manifest == null || manifest.dbSchemaVersion == 2
    val formatOk = manifest == null ||
            manifest.formatVersion == GsmBackupReader.EXPECTED_FORMAT_VERSION

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подтвердить откат") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InfoRow("Файл", fileName)
                if (manifest != null) {
                    InfoRow(
                        "Создан",
                        if (manifest.createdAt > 0)
                            dateFormat.format(Date(manifest.createdAt))
                        else "—"
                    )
                    InfoRow("Операция", operationLabel(manifest.operation))
                    InfoRow("Схема БД", manifest.dbSchemaVersion.toString())

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    Text(
                        "Содержимое:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    InfoRow("Участков", manifest.areas.toString())
                    InfoRow("Нарядов", manifest.orders.toString())
                    InfoRow("Проб", manifest.samples.toString())
                    InfoRow("Фото", manifest.photos.toString())
                    InfoRow("Заметок", manifest.notes.toString())

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                }

                if (!schemaOk) {
                    WarningCard(
                        "Схема БД в архиве: ${manifest?.dbSchemaVersion}, " +
                                "ожидается 2. Возможны ошибки. " +
                                "Продолжать не рекомендуется."
                    )
                }
                if (!formatOk) {
                    WarningCard(
                        "Несовместимая версия формата: " +
                                "${manifest?.formatVersion}. Ожидается " +
                                "${GsmBackupReader.EXPECTED_FORMAT_VERSION}. " +
                                "Откат заблокирован.",
                        error = true
                    )
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    "Текущая БД будет ЗАМЕНЕНА. Перед откатом автоматически " +
                            "сохраним её как pre_rollback_* " +
                            "(в Загрузки/GeoSampleManager и внутри приложения).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = formatOk,
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Откатиться") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun WarningCard(text: String, error: Boolean = false) {
    val container = if (error) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.tertiaryContainer
    val content = if (error) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onTertiaryContainer

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = content
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(140.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format(Locale.US, "%.1f МБ", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.0f КБ", kb)
        else -> "$bytes Б"
    }
}