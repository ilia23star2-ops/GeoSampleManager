package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.backup.BackupManifest
import com.example.geosamplemanager.data.backup.GsmBackupReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FIX 5.9-db-restore-v2:
 * Диалог превью перед импортом.
 *
 *  - имя файла;
 *  - дата создания из манифеста;
 *  - версия схемы (сравнение с 2);
 *  - счётчики;
 *  - предупреждение, что текущая БД будет ЗАМЕНЕНА.
 *
 * Импорт блокируется, если format_version != 1.
 * Несовпадение db_schema_version — жёлтое предупреждение, но импорт
 * доступен.
 */
@Composable
fun DbRestoreDialog(
    fileName: String,
    manifest: BackupManifest,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale("ru", "RU"))
    val formatOk = manifest.formatVersion == GsmBackupReader.EXPECTED_FORMAT_VERSION
    val schemaOk = manifest.dbSchemaVersion == 2

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Импорт БД") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InfoRow("Файл", fileName)
                InfoRow(
                    "Создан",
                    if (manifest.createdAt > 0)
                        dateFormat.format(Date(manifest.createdAt))
                    else "—"
                )
                InfoRow("Версия приложения", manifest.appVersion)
                InfoRow("Версия схемы БД", manifest.dbSchemaVersion.toString())

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

                // Жёлтое предупреждение о несовпадении схемы.
                if (!schemaOk) {
                    WarningCard(
                        text = "Схема БД в архиве: ${manifest.dbSchemaVersion}, " +
                                "ожидается 2. Возможны ошибки. " +
                                "Продолжать не рекомендуется."
                    )
                }

                // Красная плашка — если формат не 1.
                if (!formatOk) {
                    WarningCard(
                        text = "Несовместимая версия формата: " +
                                "${manifest.formatVersion}. Ожидается " +
                                "${GsmBackupReader.EXPECTED_FORMAT_VERSION}. " +
                                "Импорт заблокирован.",
                        error = true
                    )
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    "Текущая БД будет ЗАМЕНЕНА. Перед заменой автоматически " +
                            "сохраним её в Загрузки/GeoSampleManager.",
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
            ) { Text("Импортировать") }
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