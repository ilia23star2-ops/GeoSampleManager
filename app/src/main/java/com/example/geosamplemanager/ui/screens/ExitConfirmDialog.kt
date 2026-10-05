package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * FIX 5.9-exit:
 * Диалог подтверждения выхода из приложения.
 *
 * Если база пуста — предупреждаем, что бэкап не будет создан.
 * Без чек-бокса — ОП не должен думать о деталях.
 */
@Composable
fun ExitConfirmDialog(
    dbEmpty: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выйти из приложения?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (dbEmpty) {
                    Text(
                        "База пуста — резервный бэкап не будет создан.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        "Перед выходом будет создан резервный бэкап базы. " +
                                "Он сохранится на устройстве.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    "Приложение закроется полностью.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Icon(
                    Icons.Filled.ExitToApp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Выйти", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}