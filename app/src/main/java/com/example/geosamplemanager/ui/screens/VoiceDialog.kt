package com.example.geosamplemanager.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.core.content.ContextCompat
import com.example.geosamplemanager.data.util.VoiceController
import com.example.geosamplemanager.data.voice.AnswerReason
import com.example.geosamplemanager.data.voice.VoiceCallback
import com.example.geosamplemanager.data.voice.VoiceCommand
import com.example.geosamplemanager.data.voice.VoiceCommandParser
import com.example.geosamplemanager.data.voice.VoiceExecResult
import com.example.geosamplemanager.data.voice.VoiceFeedback
import com.example.geosamplemanager.data.voice.VoiceOrdinals
import com.example.geosamplemanager.data.voice.VoiceSessionMode
import com.example.geosamplemanager.data.voice.VoiceSpeaker
import com.example.geosamplemanager.data.voice.VoiceStatus
import com.example.geosamplemanager.data.voice.isSubstituted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    var expanded by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        Log.e(LOG_TAG, "DisposableEffect: НАЧАЛО")

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
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

                val isPendingChoice = viewModel.voiceSession.pendingMarkChoice != null
                val awaitingWeight = viewModel.voiceSession.awaitingWeight

                val weightCmd = if (awaitingWeight && !isPendingChoice) {
                    commandParser.parseWeightAnswer(text)?.let { VoiceCommand.SetWeight(it) }
                } else {
                    null
                }

                val cmd = weightCmd ?: commandParser.parseWithState(
                    text,
                    viewModel.voiceSession.state,
                    viewModel.voiceSession.mode
                )
                Log.e(
                    LOG_TAG,
                    "parsed command = $cmd, pendingChoice=$isPendingChoice, " +
                            "awaitingWeight=$awaitingWeight"
                )
                scope.launch {
                    try {
                        val result = viewModel.voiceExecute(cmd)
                        Log.e(LOG_TAG, "voiceExecute вернул: $result")

                        resultText = describeResult(result, viewModel)
                        status = "Готово"
                        viewModel.setVoiceStatus(statusFromResult(result))

                        if (cmd is VoiceCommand.Stop) {
                            Log.e(LOG_TAG, "STOP: закрываю диалог")
                            onDismiss()
                            return@launch
                        }

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

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        VoicePanel(
            status = status,
            partialText = partialText,
            finalText = finalText,
            resultText = resultText,
            voiceStatus = viewModel.state.voiceStatus,
            expanded = expanded,
            onToggleExpand = { expanded = !expanded },
            onRetry = {
                finalText = ""
                resultText = ""
                controller?.let { ctrl ->
                    scope.launch {
                        ctrl.stopListening()
                        delay(300)
                        ctrl.startListening()
                    }
                }
            },
            onDismiss = onDismiss
        )
    }
}

private fun handleFeedback(
    result: VoiceExecResult,
    fb: VoiceFeedback,
    controller: VoiceController?,
    viewModel: ReconciliationViewModel
) {
    val isSort = viewModel.voiceSession.mode == VoiceSessionMode.SORT

    when (result) {
        is VoiceExecResult.FoundOne -> {
            if (result.attentionReason != null) {
                fb.soundAttention()
                viewModel.voiceSession.awaitingContinue = true
                viewModel.voiceSession.isAutoMode = false
                val phrase = buildAttentionFoundOnePhrase(result)
                controller?.speak(phrase)
            } else {
                val phrase = if (isSort) {
                    buildFoundOneShortPhrase(result)
                } else {
                    buildFoundOnePhrase(result)
                }
                controller?.speak(phrase)
            }
        }

        is VoiceExecResult.Marked -> {
            fb.soundOk()

            val ordWord = VoiceOrdinals.word(result.ordinal)
            val subject = ordWord ?: "Проба ${VoiceSpeaker.spellOut(result.sampleNumber)}"

            // FIX 5.8.11-e4-pin-7:
            // Слово «Распознано» убрано из голоса — TTS ставил неправильное
            // ударение, резало слух. Теперь просто проговариваем слово,
            // которое услышали, перед результатом.
            // На экране (describeResult) «Распознано» осталось.
            val substPrefix = substitutedPhrase(result)

            val phrase = when {
                result.needsWeight && result.isWeightControl ->
                    "$substPrefix$subject — весовой контроль. Вес?"
                result.needsWeight ->
                    "$substPrefix$subject — холостая. Вес?"
                result.isWeightControl ->
                    "$substPrefix$subject — весовой контроль, отмечена."
                else ->
                    "$substPrefix$subject отмечена."
            }

            controller?.speak(phrase)
        }

        is VoiceExecResult.MarkedMultiple -> {
            fb.soundOk()
            val ordinals = resolveOrdinals(result.sampleNumbers, viewModel)
            controller?.speak(buildMarkedMultiplePhrase(ordinals, result.sampleNumbers.size))
        }

        is VoiceExecResult.MarkedAll -> {
            fb.soundOk()
            val base = markedSamplesPhrase(result.count)
            controller?.speak("$base Все пробы скважины.")
        }

        is VoiceExecResult.WeightSet -> {
            fb.soundOk()
            val spokenWeight = VoiceSpeaker.spokenWeight(result.weight)
            controller?.speak("Вес $spokenWeight. Проба отмечена.")
        }

        is VoiceExecResult.Unmarked -> {
            fb.soundOk()
            controller?.speak("Снято.")
        }

        VoiceExecResult.Undone -> {
            fb.soundOk()
            controller?.speak("Отменено.")
        }

        VoiceExecResult.Redone -> {
            fb.soundOk()
            controller?.speak("Повторено.")
        }

        is VoiceExecResult.FoundMany -> {
            fb.soundAttention()
            controller?.speak("Найден в нескольких нарядах. Выберите на экране.")
        }

        VoiceExecResult.NotFound -> {
            fb.soundError()
            controller?.speak("Не нашёл.")
        }

        is VoiceExecResult.ModeChanged -> {
            fb.soundOk()
            val label = when (result.mode) {
                VoiceSessionMode.SORT -> "Сортировка."
                VoiceSessionMode.SEARCH -> "Поиск."
            }
            controller?.speak(label)
        }

        is VoiceExecResult.Message -> {
            if (viewModel.voiceSession.awaitingContinue ||
                viewModel.voiceSession.pendingMarkChoice != null
            ) {
                fb.soundAttention()
            }
            controller?.speak(result.text)
        }

        VoiceExecResult.Next -> {
            fb.soundOk()
            controller?.speak("Слушаю следующую скважину.")
        }

        VoiceExecResult.Stopped -> {}
    }
}

/**
 * FIX 5.8.11-e4-pin-6:
 * Голосовой префикс при подмене номера.
 *
 * FIX 5.8.11-e4-pin-7:
 * Убрано слово «Распознано» — TTS ставил неверное ударение
 * («распОзнано» вместо «распознАно»), резало слух. Просто
 * проговариваем слово, которое услышали.
 *
 * «Четырнадцатая. » — если Vosk услышал 14, а отметили 4.
 * Пусто — если подмены не было.
 */
private fun substitutedPhrase(result: VoiceExecResult.Marked): String {
    if (!result.isSubstituted()) return ""
    val heard = result.recognizedOrdinal?.let { VoiceOrdinals.word(it) } ?: return ""
    return "$heard. "
}

private fun resolveOrdinals(
    sampleNumbers: List<String>,
    viewModel: ReconciliationViewModel
): List<Int> {
    if (sampleNumbers.isEmpty()) return emptyList()

    val index = HashMap<String, Int>(sampleNumbers.size * 2)
    viewModel.state.groups.forEach { group ->
        group.rows.forEach { row ->
            if (row.sampleNumber in sampleNumbers && row.sampleNumber !in index) {
                index[row.sampleNumber] = row.numberInWell
            }
        }
    }

    return sampleNumbers.mapNotNull { index[it] }
}

private fun buildMarkedMultiplePhrase(
    ordinals: List<Int>,
    fallbackCount: Int
): String {
    if (ordinals.isEmpty()) {
        return if (fallbackCount <= 0) "Отмечено ни одной."
        else markedSamplesPhrase(fallbackCount)
    }

    if (ordinals.size == 1) {
        val word = VoiceOrdinals.word(ordinals[0])
        return if (word != null) "Отмечена $word." else markedSamplesPhrase(fallbackCount)
    }

    val words = ordinals.mapNotNull { VoiceOrdinals.word(it) }
    if (words.size != ordinals.size) {
        return markedSamplesPhrase(fallbackCount)
    }

    return "Отмечено: ${words.joinToString(", ")}."
}

private fun buildAttentionFoundOnePhrase(r: VoiceExecResult.FoundOne): String {
    val spoken = spokenNumberOf(r, r.wellNumber)
    val sb = StringBuilder()
    sb.append("Скважина $spoken. ")

    when (r.attentionReason) {
        AnswerReason.FOUND_OTHER_AREA -> {
            val area = r.otherAreaTitle ?: "другой"
            val ord = r.otherOrderNumber ?: ""
            sb.append("Другой участок — $area")
            if (ord.isNotEmpty()) sb.append(", наряд $ord")
            sb.append(". ")
        }
        AnswerReason.FOUND_OTHER_ORDER -> {
            val ord = r.otherOrderNumber ?: "другой"
            sb.append("Другой наряд — $ord. ")
        }
        else -> {}
    }

    sb.append("Выберите на экране.")
    return sb.toString()
}

private fun buildFoundOnePhrase(r: VoiceExecResult.FoundOne): String {
    val spokenNumber = spokenNumberOf(r, r.query)
    val sb = StringBuilder()

    if (r.queueSize > 1) {
        sb.append("Найдено ${VoiceSpeaker.wells(r.queueSize)}. Слушайте первую. ")
    }

    if (r.isSample) {
        sb.append("Проба $spokenNumber. ${r.orderTitle}. ")
        val stateText = if (r.foundSamples > 0) "уже отмечена" else "не отмечена"
        sb.append(stateText).append(".")
    } else {
        sb.append("Скважина $spokenNumber. ${r.orderTitle}. ")

        val totalPhrase = if (r.totalSamples == 1) {
            "Всего одна проба."
        } else {
            "Всего ${VoiceSpeaker.samples(r.totalSamples)}."
        }

        val foundPhrase = when (r.foundSamples) {
            0 -> "Отмечено ни одной."
            1 -> "Отмечена одна."
            else -> "Отмечено ${VoiceSpeaker.samples(r.foundSamples)}."
        }

        sb.append(totalPhrase).append(" ").append(foundPhrase)
    }

    val extras = mutableListOf<String>()

    if (r.blanks > 0) {
        val blanksPhrase = if (r.blanks == 1) "Холостая одна."
        else "Холостых ${spokenCount(r.blanks, feminine = true)}."
        extras.add(blanksPhrase)
    }

    if (r.weightControls > 0) {
        val vkPhrase = if (r.weightControls == 1) "Весовой контроль один."
        else "Весового контроля ${spokenCount(r.weightControls, feminine = false)}."
        extras.add(vkPhrase)
    }

    if (r.postponed > 0) {
        val postponedPhrase = if (r.postponed == 1) "Отложена одна."
        else "Отложено ${spokenCount(r.postponed, feminine = true)}."
        extras.add(postponedPhrase)
    }

    if (extras.isNotEmpty()) {
        sb.append(" ").append(extras.joinToString(" "))
    }

    return sb.toString().replace(Regex(" +"), " ").trim()
}

private fun buildFoundOneShortPhrase(r: VoiceExecResult.FoundOne): String {
    val spokenNumber = spokenNumberOf(r, r.query)
    return if (r.isSample) {
        "Проба $spokenNumber. ${r.orderTitle}."
    } else {
        "Скважина $spokenNumber. ${r.orderTitle}."
    }
}

private fun spokenNumberOf(r: VoiceExecResult.FoundOne, fallback: String): String =
    if (r.groups.isNotEmpty()) {
        VoiceSpeaker.spellOut(r.groups)
    } else {
        VoiceSpeaker.spellOut(fallback)
    }

private fun statusFromResult(result: VoiceExecResult): VoiceStatus = when (result) {
    is VoiceExecResult.FoundOne -> {
        if (result.attentionReason != null) {
            VoiceStatus.Found("${result.query} — ${result.attentionReason.shortLabel}")
        } else {
            VoiceStatus.Found("${result.query} — ${result.orderTitle}")
        }
    }

    is VoiceExecResult.FoundMany -> VoiceStatus.Found("Найден в нескольких нарядах")
    is VoiceExecResult.Marked -> VoiceStatus.Marked(result.sampleNumber)

    is VoiceExecResult.MarkedMultiple ->
        VoiceStatus.Marked("Отмечено: ${result.sampleNumbers.size}")

    is VoiceExecResult.MarkedAll ->
        VoiceStatus.Marked("Все отмечены: ${result.count}")

    is VoiceExecResult.WeightSet -> VoiceStatus.Marked("Вес: ${formatWeightUi(result.weight)}")
    is VoiceExecResult.Unmarked -> VoiceStatus.Marked("Снято: ${result.sampleNumber}")

    is VoiceExecResult.ModeChanged -> VoiceStatus.Found(
        when (result.mode) {
            VoiceSessionMode.SORT -> "Режим: сортировка"
            VoiceSessionMode.SEARCH -> "Режим: поиск"
        }
    )

    is VoiceExecResult.Message -> VoiceStatus.Found(result.text)
    VoiceExecResult.Next -> VoiceStatus.Idle
    VoiceExecResult.NotFound -> VoiceStatus.Error("Не нашёл")
    VoiceExecResult.Stopped -> VoiceStatus.Idle
    VoiceExecResult.Undone -> VoiceStatus.Idle
    VoiceExecResult.Redone -> VoiceStatus.Idle
}

private fun describeResult(
    result: VoiceExecResult,
    viewModel: ReconciliationViewModel
): String {
    val isSort = viewModel.voiceSession.mode == VoiceSessionMode.SORT

    return when (result) {
        is VoiceExecResult.FoundOne -> {
            if (result.attentionReason != null) {
                val sb = StringBuilder()
                sb.append("Скважина ${result.query}. ")

                when (result.attentionReason) {
                    AnswerReason.FOUND_OTHER_AREA -> {
                        val area = result.otherAreaTitle ?: "другой"
                        val ord = result.otherOrderNumber ?: ""
                        sb.append("⚠ Другой участок — $area")
                        if (ord.isNotEmpty()) sb.append(", наряд $ord")
                        sb.append(". Выберите на экране.")
                    }
                    AnswerReason.FOUND_OTHER_ORDER -> {
                        val ord = result.otherOrderNumber ?: "другой"
                        sb.append("⚠ Другой наряд — $ord. Выберите на экране.")
                    }
                    else -> {}
                }

                sb.toString()
            } else if (isSort) {
                if (result.isSample) {
                    "Проба ${result.query} → ${result.orderTitle}."
                } else {
                    "Скважина ${result.query} → ${result.orderTitle}."
                }
            } else if (result.isSample) {
                val label = if (result.foundSamples > 0) "Уже отмечена" else "Не отмечена"
                "Проба ${result.query} → ${result.orderTitle}. $label."
            } else {
                buildString {
                    append("Скважина ${result.query} → ${result.orderTitle}. ")
                    append("Всего проб: ${result.totalSamples}, " +
                            "отмечено: ${result.foundSamples}")

                    val extras = mutableListOf<String>()
                    if (result.blanks > 0) extras.add("холостых ${result.blanks}")
                    if (result.weightControls > 0) extras.add("ВК ${result.weightControls}")
                    if (result.postponed > 0) extras.add("отложено ${result.postponed}")

                    if (extras.isNotEmpty()) {
                        append(". ").append(extras.joinToString(", "))
                    }
                }
            }
        }

        is VoiceExecResult.FoundMany ->
            "⚠ Найден в нескольких нарядах (${result.variants})"

        is VoiceExecResult.Marked -> {
            val ordWord = VoiceOrdinals.word(result.ordinal)
            val subject = ordWord?.replaceFirstChar { it.uppercase() }
                ?: "Проба ${result.sampleNumber}"

            val extra = when {
                result.needsWeight && result.isWeightControl -> " — весовой контроль, вес?"
                result.needsWeight -> " — холостая, вес?"
                result.isWeightControl -> " — весовой контроль"
                else -> ""
            }

            // FIX 5.8.11-e4-pin-6: на экране «Распознано» оставляем —
            // глазами читается нормально, ударение не слышно.
            val substPrefix = if (result.isSubstituted()) {
                val heard = result.recognizedOrdinal
                    ?.let { VoiceOrdinals.word(it) }
                    ?: "№${result.recognizedOrdinal}"
                "Распознано «$heard» → "
            } else {
                ""
            }

            "$substPrefix$subject отмечена: ${result.sampleNumber}$extra"
        }

        is VoiceExecResult.MarkedMultiple -> {
            "Отмечено проб: ${result.sampleNumbers.size} " +
                    "(${result.sampleNumbers.joinToString(", ")})"
        }

        is VoiceExecResult.MarkedAll ->
            "Отмечено всех проб: ${result.count}"

        is VoiceExecResult.WeightSet ->
            "Вес: ${formatWeightUi(result.weight)} кг. Проба отмечена."

        is VoiceExecResult.Unmarked ->
            "Снято: ${result.sampleNumber}"

        is VoiceExecResult.ModeChanged -> when (result.mode) {
            VoiceSessionMode.SORT -> "Режим: Сортировка"
            VoiceSessionMode.SEARCH -> "Режим: Поиск"
        }

        is VoiceExecResult.Message -> result.text
        VoiceExecResult.Next -> "Следующая скважина"
        VoiceExecResult.Undone -> "Отменено"
        VoiceExecResult.Redone -> "Повторено"
        VoiceExecResult.Stopped -> "Стоп"
        VoiceExecResult.NotFound -> "Не найдено"
    }
}

private fun markedSamplesPhrase(n: Int): String = when (n) {
    0 -> "Отмечено ни одной пробы."
    1 -> "Отмечена одна проба."
    else -> "Отмечено ${VoiceSpeaker.samples(n)}."
}

private fun spokenCount(n: Int, feminine: Boolean = false): String =
    VoiceSpeaker.numberWords(n, feminine)

private fun formatWeightUi(value: Double): String {
    val rounded = (value * 100.0).roundToInt() / 100.0
    return if (rounded % 1.0 == 0.0) {
        rounded.toInt().toString()
    } else {
        rounded.toString().replace('.', ',')
    }
}
