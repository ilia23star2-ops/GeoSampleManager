package com.example.geosamplemanager.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.geosamplemanager.data.util.VoiceController
import com.example.geosamplemanager.data.voice.VoiceCallback
import com.example.geosamplemanager.data.voice.VoiceCommandParser
import com.example.geosamplemanager.data.voice.VoiceExecResult
import com.example.geosamplemanager.data.voice.VoiceFeedback
import com.example.geosamplemanager.data.voice.VoiceSpeaker
import com.example.geosamplemanager.data.voice.VoiceStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LOG_TAG = "VoiceDialog"

@Composable
fun VoiceDialog(
    viewModel: ReconciliationViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val commandParser = remember { VoiceCommandParser() }

    var status by remember { mutableStateOf("Инициализация...") }
    var partialText by remember { mutableStateOf("") }
    var finalText by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }
    var controller by remember { mutableStateOf<VoiceController?>(null) }
    var feedback by remember { mutableStateOf<VoiceFeedback?>(null) }

    DisposableEffect(Unit) {
        Log.e(LOG_TAG, "DisposableEffect: НАЧАЛО")

        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            status = "Нет разрешения на микрофон"
            return@DisposableEffect onDispose { }
        }

        val fb = VoiceFeedback(context)
        feedback = fb

        val callback = object : VoiceCallback {
            override fun onReady() {
                status = "Слушаю..."
                partialText = ""
                viewModel.setVoiceStatus(VoiceStatus.Listening)
            }

            override fun onPartial(text: String) {
                if (text.isNotBlank()) {
                    partialText = text
                    viewModel.setVoiceStatus(VoiceStatus.Heard(text))
                }
            }

            override fun onResult(text: String) {
                Log.e(LOG_TAG, "CALLBACK onResult: text=«$text»")
                finalText = text
                partialText = ""
                if (text.isBlank()) {
                    status = "Не расслышал"
                    viewModel.setVoiceStatus(VoiceStatus.Error("Не расслышал"))
                    return
                }

                viewModel.setVoiceStatus(VoiceStatus.Heard(text))
                val cmd = commandParser.parse(text)
                Log.e(LOG_TAG, "parsed command = $cmd")

                scope.launch {
                    try {
                        val result = viewModel.voiceExecute(cmd)
                        Log.e(LOG_TAG, "voiceExecute вернул: $result")
                        resultText = describeResult(result)
                        status = "Готово"
                        viewModel.setVoiceStatus(statusFromResult(result))
                        handleFeedback(result, fb, controller, viewModel)
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, "voiceExecute УПАЛ", e)
                        resultText = "Ошибка: ${e.message}"
                        status = "Ошибка"
                        viewModel.setVoiceStatus(VoiceStatus.Error(e.message ?: "ошибка"))
                        fb.soundError()
                    }
                }
            }

            override fun onError(message: String) {
                status = message
                partialText = ""
                viewModel.setVoiceStatus(VoiceStatus.Error(message))
            }
        }

        val c = VoiceController(context, callback)
        controller = c
        c.startListening()

        onDispose {
            c.destroy()
            fb.release()
            controller = null
            feedback = null
            viewModel.setVoiceStatus(VoiceStatus.Idle)
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
                    Icon(Icons.Filled.Mic, null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                }

                if (partialText.isNotEmpty()) {
                    Text("Слышу: $partialText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (finalText.isNotEmpty()) {
                    Card {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.VolumeUp, null,
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text("Распознано:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(finalText, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                if (resultText.isNotEmpty()) {
                    Card {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.PlayArrow, null,
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text("Результат:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(resultText, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Text(
                    "Команды: «первая», «снять первую», «вес два пять», " +
                            "«следующая», «стоп», «помощь». Номера: «1524», " +
                            "«KPD1090031». Сортировка: «1524 и 1525».",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                finalText = ""
                resultText = ""
                controller?.let { ctrl ->
                    scope.launch {
                        ctrl.stopListening()
                        delay(300)
                        ctrl.startListening()
                    }
                }
            }) { Text("Ещё раз") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

/**
 * Звук + озвучка по результату.
 *
 * Правила (DECISIONS §13.4–13.5):
 *   • OK_SINGLE — без звука, TTS о результате.
 *   • FoundMany — soundAttention + TTS «Выберите на экране».
 *   • Marked — soundOk + TTS подтверждения.
 *   • WeightSet — soundOk.
 *   • NotFound — soundError.
 *   • Message с автопаузой — soundAttention (гвардия К5 глушит повторы).
 */
private fun handleFeedback(
    result: VoiceExecResult,
    fb: VoiceFeedback,
    controller: VoiceController?,
    viewModel: ReconciliationViewModel
) {
    when (result) {
        is VoiceExecResult.FoundOne -> {
            // Успешный поиск — без звука.
            val spokenNumber = VoiceSpeaker.spellOut(result.query)
            val orderPart = result.orderTitle
            val phrase = if (result.isSample) {
                val stateText = if (result.foundSamples > 0) "уже отмечена" else "не отмечена"
                "Проба $spokenNumber. $orderPart. $stateText."
            } else {
                val total = VoiceSpeaker.spellNumber(result.totalSamples)
                val found = VoiceSpeaker.spellNumber(result.foundSamples)
                buildString {
                    append("Скважина $spokenNumber. $orderPart. ")
                    append("Проб $total, отмечено $found.")
                    // Холостые / ВК / отложено — если есть.
                    // Пока эта информация не приходит из VoiceExecResult,
                    // добавим в заходе 5.8.9d вместе с расширением
                    // VoiceExecResult.FoundOne.
                }
            }
            controller?.speak(phrase)
        }

        is VoiceExecResult.Marked -> {
            fb.soundOk()
            val spoken = VoiceSpeaker.spellOut(result.sampleNumber)
            val phrase = when {
                result.needsWeight && result.isWeightControl ->
                    "Проба $spoken — весовой контроль. Вес?"
                result.needsWeight ->
                    "Проба $spoken — холостая. Вес?"
                result.isWeightControl ->
                    "Проба $spoken — весовой контроль, отмечена."
                else ->
                    "Проба $spoken отмечена."
            }
            controller?.speak(phrase)
        }

        is VoiceExecResult.WeightSet -> {
            fb.soundOk()
            val spoken = VoiceSpeaker.spellOut(result.sampleNumber)
            controller?.speak("Вес ${result.weight} килограмма. Проба $spoken.")
        }

        is VoiceExecResult.FoundMany -> {
            fb.soundAttention()
            controller?.speak("Найден в нескольких нарядах. Выберите на экране.")
        }

        VoiceExecResult.NotFound -> {
            fb.soundError()
            controller?.speak("Не нашёл.")
        }

        is VoiceExecResult.Message -> {
            // Если это ответ в автопаузе — это «внимание» (К5 глушит повторы).
            if (viewModel.voiceSession.awaitingContinue) {
                fb.soundAttention()
            }
            controller?.speak(result.text)
        }

        VoiceExecResult.Next -> {
            fb.soundOk()
            controller?.speak("Слушаю следующую скважину.")
        }

        else -> {}
    }
}

private fun statusFromResult(result: VoiceExecResult): VoiceStatus = when (result) {
    is VoiceExecResult.FoundOne -> VoiceStatus.Found("${result.query} — ${result.orderTitle}")
    is VoiceExecResult.FoundMany -> VoiceStatus.Found("Найден в нескольких нарядах")
    is VoiceExecResult.Marked -> VoiceStatus.Marked(result.sampleNumber)
    is VoiceExecResult.WeightSet -> VoiceStatus.Marked("Вес: ${result.weight}")
    is VoiceExecResult.Unmarked -> VoiceStatus.Marked("Снято: ${result.sampleNumber}")
    is VoiceExecResult.Message -> VoiceStatus.Found(result.text)
    VoiceExecResult.Next -> VoiceStatus.Idle
    VoiceExecResult.NotFound -> VoiceStatus.Error("Не нашёл")
    else -> VoiceStatus.Idle
}

private fun describeResult(result: VoiceExecResult): String = when (result) {
    is VoiceExecResult.FoundOne -> {
        if (result.isSample) {
            val label = if (result.foundSamples > 0) "Уже отмечена" else "Не отмечена"
            "Проба ${result.query} → ${result.orderTitle}. $label"
        } else {
            "Скважина ${result.query} → ${result.orderTitle}. " +
                    "Всего проб: ${result.totalSamples}, отмечено: ${result.foundSamples}"
        }
    }
    is VoiceExecResult.FoundMany -> "⚠ Найден в нескольких нарядах (${result.variants})"
    is VoiceExecResult.Marked -> {
        val extra = when {
            result.needsWeight && result.isWeightControl -> " — весовой контроль, вес?"
            result.needsWeight -> " — холостая, вес?"
            result.isWeightControl -> " — весовой контроль"
            else -> ""
        }
        "Отмечена проба №${result.ordinal}: ${result.sampleNumber}$extra"
    }
    is VoiceExecResult.WeightSet -> "Вес: ${result.weight} кг (${result.sampleNumber})"
    is VoiceExecResult.Unmarked -> "Снято: ${result.sampleNumber}"
    is VoiceExecResult.Message -> result.text
    VoiceExecResult.Next -> "Следующая скважина"
    VoiceExecResult.Undone -> "Отменено"
    VoiceExecResult.Redone -> "Повторено"
    VoiceExecResult.Stopped -> "Стоп"
    VoiceExecResult.NotFound -> "Не найдено"
}
