package com.example.geosamplemanager.data.voice

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Настройки голосового помощника (§14 VOICE.md).
 */
data class VoiceSettings(
    val segmentPauseMs: Long = 800L,
    val autoStopMinutes: Int = 5,          // 0 = выкл
    val ttsVolume: TtsVolume = TtsVolume.NORMAL,
    val mode: VoiceMode = VoiceMode.NOVICE,
    val customPrefixPronunciations: Map<String, String> = emptyMap(),
    val showOnboarding: Boolean = true,
    /**
     * Использовать грамматику Vosk (ограниченный словарь).
     * Резко повышает точность распознавания чисел.
     */
    val useGrammar: Boolean = true
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
            val parsed = gson.fromJson(json, VoiceSettings::class.java) ?: VoiceSettings()
            // Gson игнорирует Kotlin-дефолты: если поля не было в JSON,
            // Boolean станет false. Восстанавливаем true.
            if (!json.contains("\"useGrammar\"")) {
                parsed.copy(useGrammar = true)
            } else {
                parsed
            }
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