package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * FIX 5.9-db-backup-v2: диалог экспорта бэкапа.
 *
 * FIX 5.9-db-backup-fix:
 *  - убран чекбокс «Сохранить во внутреннюю папку»;
 *  - добавлен чекбокс «Поделиться после сохранения»;
 *  - куда сохраняем — решает UI (публичные Загрузки или SAF fallback).
 *
 * Возвращает onExport: (имя без расширения, shareAfter).
 */
@Composable
fun DbBackupDialog(
    defaultName: String,
    onExport: (name: String, shareAfter: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
    var shareAfter by remember { mutableStateOf(false) }

    val trimmed = name.trim()
    val valid = trimmed.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Экспорт БД") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Архив с базой и фото сохранится в папку «Загрузки» " +
                            "(Downloads/GeoSampleManager). Формат — .gsmbackup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя файла") },
                    singleLine = true,
                    isError = !valid,
                    supportingText = {
                        if (!valid) {
                            Text(
                                "Введите имя",
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                "Будет: $trimmed.gsmbackup",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = shareAfter,
                            onValueChange = { shareAfter = it }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = shareAfter,
                        onCheckedChange = { shareAfter = it }
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            "Поделиться после сохранения",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Откроется диалог «Отправить» — можно в Drive, " +
                                    "Telegram, почту",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onExport(trimmed, shareAfter) }
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Экспортировать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}