package com.example.geosamplemanager.data.voice

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Настройки голосового помощника (§14 VOICE.md).
 *
 * FIX 5.8.10-a (И-10): showCharacteristic — переключатель колонки.
 * FIX 5.8.10-b (И-9): защита showOnboarding = true.
 *
 * FIX 5.9-settings-sound:
 *  - feedbackEnabled — вкл/выкл звуковые сигналы;
 *  - feedbackVolume — громкость сигналов (QUIET/NORMAL/LOUD).
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

    /** FIX 5.9-settings-sound: вкл/выкл звуковые сигналы. */
    val feedbackEnabled: Boolean = true,

    /** FIX 5.9-settings-sound: громкость звуковых сигналов. */
    val feedbackVolume: SoundLevel = SoundLevel.NORMAL
)

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
            // Boolean становится false. Восстанавливаем true для тех
            // полей, где дефолт — true.
            if (!json.contains("\"useGrammar\"")) {
                parsed = parsed.copy(useGrammar = true)
            }
            if (!json.contains("\"showCharacteristic\"")) {
                parsed = parsed.copy(showCharacteristic = true)
            }
            if (!json.contains("\"showOnboarding\"")) {
                parsed = parsed.copy(showOnboarding = true)
            }
            // FIX 5.9-settings-sound: новые поля.
            if (!json.contains("\"feedbackEnabled\"")) {
                parsed = parsed.copy(feedbackEnabled = true)
            }
            if (!json.contains("\"feedbackVolume\"")) {
                parsed = parsed.copy(feedbackVolume = SoundLevel.NORMAL)
            }

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