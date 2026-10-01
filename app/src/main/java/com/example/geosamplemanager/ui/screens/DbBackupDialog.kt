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
 * FIX 5.9-db-backup-v2:
 * Диалог экспорта бэкапа.
 *
 *  - редактируемое имя файла (без .gsmbackup);
 *  - чекбокс «Сохранить во внутреннюю папку» (по умолчанию ВКЛ);
 *    если снять — откроется системный диалог «Куда сохранить?».
 *
 * Возвращает через onExport: (имя без расширения, saveInternal).
 */
@Composable
fun DbBackupDialog(
    defaultName: String,
    onExport: (name: String, saveInternal: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
    var saveInternal by remember { mutableStateOf(true) }

    val trimmed = name.trim()
    val valid = trimmed.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Экспорт БД") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Создастся архив с базой и фото. Формат — .gsmbackup.",
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
                            value = saveInternal,
                            onValueChange = { saveInternal = it }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = saveInternal,
                        onCheckedChange = { saveInternal = it }
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            "Сохранить во внутреннюю папку",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (saveInternal)
                                "Файл останется внутри приложения (db_backups/)"
                            else
                                "Откроется системный диалог — можно на флешку, Drive",
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
                onClick = { onExport(trimmed, saveInternal) }
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