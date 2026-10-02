package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.DbInfo

/**
 * FIX 5.9-db-clean:
 * Диалог полной очистки БД.
 *  - показывает счётчики текущего содержимого;
 *  - кнопка «Очистить» активна только после галочки;
 *  - напоминает, что авто-бэкап pre_clean_* будет создан.
 */
@Composable
fun DbCleanDialog(
    info: DbInfo?,
    loading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmState by remember { mutableStateOf(CleanConfirmState.Initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Очистить всю БД?") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (loading || info == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text(
                        "Будет удалено:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    InfoRow("Участков", info.areasCount.toString())
                    InfoRow("Нарядов", info.ordersCount.toString())
                    InfoRow("Скважин", info.wellsCount.toString())
                    InfoRow("Проб", info.samplesCount.toString())
                    InfoRow("Заметок", info.notesCount.toString())
                    InfoRow("Фото", info.photosCount.toString())
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                WarningCard(
                    "Все данные будут удалены безвозвратно. " +
                            "Перед очисткой автоматически создаётся " +
                            "авто-бэкап pre_clean_* — его можно будет " +
                            "использовать для отката.",
                    error = true
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { confirmState = confirmState.toggle() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = confirmState.confirmed,
                        onCheckedChange = { confirmState = confirmState.toggle() }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Понимаю, что все данные будут удалены",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = confirmState.confirmEnabled,
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Очистить") }
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