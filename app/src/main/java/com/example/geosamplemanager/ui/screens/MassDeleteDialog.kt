package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * FIX 5.9-edit-mass-ops:
 * Диалог массового удаления.
 * Один чекбокс «Пересчитать № проб в скважинах» (по умолчанию выкл.).
 */
@Composable
fun MassDeleteDialog(
    count: Int,
    onConfirm: (renumber: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var renumber by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить выбранные?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Будет удалено проб: $count. " +
                            "Действие можно отменить кнопкой «Отмена» " +
                            "в шапке экрана.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = renumber,
                            onValueChange = { renumber = it }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = renumber,
                        onCheckedChange = { renumber = it }
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text("Пересчитать № проб в скважинах")
                        Text(
                            "Оставшиеся пробы перенумеруются подряд",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(renumber) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Удалить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}