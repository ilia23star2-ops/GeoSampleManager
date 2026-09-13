package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.util.VoiceController
import com.example.geosamplemanager.data.voice.VoiceCommand
import com.example.geosamplemanager.data.voice.VoiceCommandParser

/**
 * Диалог голосового ввода.
 *
 * 5.8.4:
 *  • Распознаёт фразу.
 *  • Прогоняет через VoiceCommandParser.
 *  • Показывает распознанную команду.
 *
 * Выполнение команд — 5.8.5.
 */
@Composable
fun VoiceDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val commandParser = remember { VoiceCommandParser() }

    var status by remember { mutableStateOf("Инициализация...") }
    var partialText by remember { mutableStateOf("") }
    var finalText by remember { mutableStateOf("") }
    var parsedCommand by remember { mutableStateOf<VoiceCommand?>(null) }

    var controller by remember { mutableStateOf<VoiceController?>(null) }

    DisposableEffect(Unit) {
        val c = VoiceController(
            context = context,
            onReady = {
                status = "Слушаю..."
                partialText = ""
            },
            onPartialResult = { text ->
                partialText = text
            },
            onResult = { text ->
                finalText = text
                parsedCommand = if (text.isBlank()) null
                else commandParser.parse(text)
                status = if (text.isBlank()) "Не расслышал" else "Готово"
                partialText = ""
                if (text.isNotBlank()) controller?.speak(text)
            },
            onError = { msg ->
                status = msg
                partialText = ""
            }
        )
        controller = c
        c.startListening()

        onDispose {
            c.destroy()
            controller = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Голосовой ввод") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                }

                if (partialText.isNotEmpty()) {
                    Text(
                        "Слышу: $partialText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (finalText.isNotEmpty()) {
                    Card {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Распознано:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(finalText, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                parsedCommand?.let { cmd ->
                    Card {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Команда:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(
                                describeCommand(cmd),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Text(
                    "Примеры команд: «первая», «снять первую», «вес два пять», " +
                            "«следующая», «стоп». Номера: «1524», «KPD1090031». " +
                            "Сортировка: «1524 и 1525».",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                parsedCommand = null
                finalText = ""
                controller?.startListening()
            }) {
                Text("Ещё раз")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

/**
 * Текстовое описание команды — для отладки и проверки.
 */
private fun describeCommand(cmd: VoiceCommand): String = when (cmd) {
    is VoiceCommand.Search       -> "Поиск: ${cmd.query}"
    is VoiceCommand.MarkOrdinal  -> "Отметить пробу №${cmd.ordinal}"
    is VoiceCommand.SetWeight    -> "Вес: ${cmd.value} кг"
    is VoiceCommand.ClearOrdinal -> "Снять отметку у №${cmd.ordinal}"
    is VoiceCommand.ClearLast    -> "Снять последнюю отметку"
    is VoiceCommand.ClearAll     -> "Снять все отметки"
    is VoiceCommand.Unpostpone   -> "Снять «отложена»"
    is VoiceCommand.Next         -> "Следующая скважина"
    is VoiceCommand.Undo         -> "Отмена"
    is VoiceCommand.Redo         -> "Повторить"
    is VoiceCommand.Pause        -> "Пауза"
    is VoiceCommand.Resume       -> "Продолжить"
    is VoiceCommand.Stop         -> "Стоп"
    is VoiceCommand.HowManyLeft  -> "Сколько осталось"
    is VoiceCommand.ShowPostponed-> "Показать отложенные"
    is VoiceCommand.ShowFound    -> "Показать найденные"
    is VoiceCommand.Help         -> "Справка"
    is VoiceCommand.Sort         -> "Сортировка: ${cmd.queries.joinToString(", ")}"
    VoiceCommand.Unknown         -> "Не понял"
}