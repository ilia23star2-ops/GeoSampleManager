package com.example.geosamplemanager.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.geosamplemanager.data.bluetooth.BluetoothController
import com.example.geosamplemanager.data.bluetooth.BluetoothSettings
import com.example.geosamplemanager.data.util.VoiceController
import com.example.geosamplemanager.data.voice.VoiceCallback

/**
 * FIX 5.9-settings-bt:
 * Мини-диалог «Проверить микрофон». Слушает, показывает partial
 * и результат распознавания. Никаких команд, отметок — только
 * проверка, что микрофон работает.
 *
 * Используется та же BT-настройка, что и в основном ГП.
 */
@Composable
fun MicrophoneTestDialog(
    btSettings: BluetoothSettings,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var status by remember { mutableStateOf("Инициализация…") }
    var partialText by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<String>()) }
    var controller by remember { mutableStateOf<VoiceController?>(null) }

    DisposableEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            status = "Нет разрешения на микрофон"
            return@DisposableEffect onDispose { }
        }

        val btController = BluetoothController(context)

        val callback = object : VoiceCallback {
            override fun onReady() {
                status = "Слушаю…"
                partialText = ""
            }

            override fun onPartial(text: String) {
                if (text.isNotBlank()) partialText = text
            }

            override fun onResult(text: String) {
                partialText = ""
                if (text.isNotBlank()) {
                    results = results + text
                }
                status = "Слушаю…"
            }

            override fun onError(message: String) {
                status = "Ошибка: $message"
            }
        }

        val c = VoiceController(
            context = context,
            callback = callback,
            btSettings = btSettings,
            btController = btController
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
        title = { Text("Проверить микрофон") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Скажите что-нибудь. Распознанный текст появится ниже.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            "Статус",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            status,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (partialText.isNotBlank()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "Слышу сейчас",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                partialText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                if (results.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "Распознано (${results.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            results.forEachIndexed { idx, r ->
                                Text(
                                    "${idx + 1}. $r",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    results = emptyList()
                    partialText = ""
                    status = "Слушаю…"
                }
            ) { Text("Очистить") }
        }
    )
}