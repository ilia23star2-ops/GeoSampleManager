package com.example.geosamplemanager

import android.app.Application
import com.example.geosamplemanager.data.DatabaseRepository
import com.example.geosamplemanager.data.history.ImportHistoryRepository
import com.example.geosamplemanager.data.settings.SettingsRepository
import com.example.geosamplemanager.data.voice.VoiceSettingsRepository
import com.example.geosamplemanager.data.voice.VoiceTtsHolder
import org.vosk.Model

class GeoSampleApp : Application() {

    lateinit var repository: DatabaseRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var importHistoryRepository: ImportHistoryRepository
        private set

    lateinit var voiceSettingsRepository: VoiceSettingsRepository
        private set

    /**
     * Загруженная модель Vosk.
     */
    var voiceModel: Model? = null

    /**
     * Флаг: использовать ли грамматику Vosk (ограниченный словарь).
     * По умолчанию true.
     */
    var voiceUseGrammar: Boolean = true

    override fun onCreate() {
        super.onCreate()
        repository = DatabaseRepository(this)
        settingsRepository = SettingsRepository(this)
        importHistoryRepository = ImportHistoryRepository(this)
        voiceSettingsRepository = VoiceSettingsRepository(this)

        // Прогреваем TTS заранее — чтобы при первом «скажи» не ждать 3 секунды.
        VoiceTtsHolder.init(this)
    }
}