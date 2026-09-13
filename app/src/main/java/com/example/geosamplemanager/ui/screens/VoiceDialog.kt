package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.util.VoiceController

/**
 * Простейший диалог голосового ввода (каркас 5.8.1).
 *
 * Что делает:
 *  1. Открывается → автоматически начинает слушать.
 *  2. Распознаёт фразу.
 *  3. Показывает распознанный текст.
 *  4. Озвучивает его обратно через TTS (для проверки, что всё работает).
 *
 * Сценарии «скважина → список проб», «отметь первую» и т.п. — в 5.8.2+.
 */
@Composable
fun VoiceDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var status by remember { mutableStateOf("Инициализация...") }
    var partialText by remember { mutableStateOf("") }
    var finalText by remember { mutableStateOf("") }

    // Контроллер создаётся один раз в DisposableEffect и живёт до закрытия.
    // Ссылку храним в remember, чтобы колбэки могли её использовать.
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
                status = if (text.isBlank()) "Не расслышал" else "Готово"
                partialText = ""
                if (text.isNotBlank()) {
                    // Ссылаемся на controller через remember-переменную,
                    // а не на локальную c (её замыкание невозможно).
                    controller?.speak(text)
                }
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
                modifier = Modifier.fillMaxWidth(),
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

                Text(
                    "Скажите любую фразу — она распознается и озвучится. " +
                            "Это проверка голосового движка.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { controller?.startListening() }) {
                Text("Ещё раз")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}