package com.example.geosamplemanager

import android.app.Application
import com.example.geosamplemanager.data.DatabaseRepository
import com.example.geosamplemanager.data.history.ImportHistoryRepository
import com.example.geosamplemanager.data.settings.SettingsRepository
import com.example.geosamplemanager.data.voice.VoiceSettingsRepository
import com.example.geosamplemanager.data.voice.VoiceTtsHolder
import org.vosk.Model

/**
 * FIX 5.9-db-restore-v2:
 *  - добавлен resetRepository() — после замены файла БД создаём
 *    свежий DatabaseRepository (новое соединение с новой БД).
 */
class GeoSampleApp : Application() {

    lateinit var repository: DatabaseRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var importHistoryRepository: ImportHistoryRepository
        private set

    lateinit var voiceSettingsRepository: VoiceSettingsRepository
        private set

    var voiceModel: Model? = null

    var voiceUseGrammar: Boolean = true

    override fun onCreate() {
        super.onCreate()
        repository = DatabaseRepository(this)
        settingsRepository = SettingsRepository(this)
        importHistoryRepository = ImportHistoryRepository(this)
        voiceSettingsRepository = VoiceSettingsRepository(this)

        VoiceTtsHolder.init(this)
    }

    /**
     * FIX 5.9-db-restore-v2:
     * Пересоздать DatabaseRepository — новое соединение с новой БД.
     * Вызывать ТОЛЬКО после AppDatabase.closeAndReset() и замены файла.
     */
    fun resetRepository() {
        repository = DatabaseRepository(this)
    }
}