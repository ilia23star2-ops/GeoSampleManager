package com.example.geosamplemanager.data.voice

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Настройки голосового помощника (§14 VOICE.md).
 *
 * FIX 5.8.10-a (И-10): showCharacteristic.
 * FIX 5.8.10-b (И-9): защита showOnboarding = true.
 * FIX 5.9-settings-sound: feedbackEnabled, feedbackVolume.
 *
 * FIX 5.9-settings-sound-3-fix-2:
 *  - ttsSpeed: Float (0.5..2.0) вместо enum — в UI ползунок;
 *  - поле переименовано (было enum ttsSpeed), старое значение
 *    в JSON игнорируется, дефолт 1.10;
 *  - range и дефолт вынесены в companion.
 */
data class VoiceSettings(
    val segmentPauseMs: Long = 800L,
    val autoStopMinutes: Int = 5,
    val ttsVolume: TtsVolume = TtsVolume.NORMAL,
    val mode: VoiceMode = VoiceMode.NOVICE,
    val customPrefixPronunciations: Map<String, String> = emptyMap(),
    val showOnboarding: Boolean = true,
    val useGrammar: Boolean = true,
    val showCharacteristic: Boolean = true,

    val feedbackEnabled: Boolean = true,
    val feedbackVolume: SoundLevel = SoundLevel.NORMAL,

    /** FIX 5.9-settings-sound-3-fix-2: скорость озвучки 0.5..2.0. */
    val ttsSpeedValue: Float = DEFAULT_TTS_SPEED
) {
    companion object {
        const val DEFAULT_TTS_SPEED = 1.10f
        const val MIN_TTS_SPEED = 0.5f
        const val MAX_TTS_SPEED = 2.0f
    }
}

enum class TtsVolume { OFF, QUIET, NORMAL, LOUD }

enum class VoiceMode { NOVICE, EXPERIENCED }

/**
 * Репозиторий настроек ГП.
 * Файл: filesDir/voice_settings.json. Сериализация через Gson.
 */
class VoiceSettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val gson = Gson()

    private val file: File
        get() = File(appContext.filesDir, FILE_NAME)

    suspend fun load(): VoiceSettings = withContext(Dispatchers.IO) {
        try {
            val f = file
            if (!f.exists()) return@withContext VoiceSettings()
            val json = f.readText()
            var parsed = gson.fromJson(json, VoiceSettings::class.java) ?: VoiceSettings()

            // Gson игнорирует Kotlin-дефолты: если поля не было в JSON,
            // Boolean становится false. Восстанавливаем true там, где
            // дефолт — true.
            if (!json.contains("\"useGrammar\"")) {
                parsed = parsed.copy(useGrammar = true)
            }
            if (!json.contains("\"showCharacteristic\"")) {
                parsed = parsed.copy(showCharacteristic = true)
            }
            if (!json.contains("\"showOnboarding\"")) {
                parsed = parsed.copy(showOnboarding = true)
            }
            if (!json.contains("\"feedbackEnabled\"")) {
                parsed = parsed.copy(feedbackEnabled = true)
            }
            if (!json.contains("\"feedbackVolume\"")) {
                parsed = parsed.copy(feedbackVolume = SoundLevel.NORMAL)
            }
            // FIX 5.9-settings-sound-3-fix-2:
            // если старого поля ttsSpeedValue нет — дефолт.
            if (!json.contains("\"ttsSpeedValue\"")) {
                parsed = parsed.copy(ttsSpeedValue = VoiceSettings.DEFAULT_TTS_SPEED)
            }
            // Защита от некорректного значения (например, старый
            // enum-ttsSpeed случайно попал в новое поле).
            parsed = parsed.copy(
                ttsSpeedValue = parsed.ttsSpeedValue
                    .coerceIn(VoiceSettings.MIN_TTS_SPEED, VoiceSettings.MAX_TTS_SPEED)
            )

            parsed
        } catch (e: Exception) {
            VoiceSettings()
        }
    }

    suspend fun save(settings: VoiceSettings) = withContext(Dispatchers.IO) {
        try {
            file.writeText(gson.toJson(settings))
        } catch (e: Exception) {
            // Настройки не критичны — молча игнорируем.
        }
    }

    companion object {
        private const val FILE_NAME = "voice_settings.json"
    }
}