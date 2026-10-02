package com.example.geosamplemanager

import android.app.Application
import com.example.geosamplemanager.data.DatabaseRepository
import com.example.geosamplemanager.data.history.ImportHistoryRepository
import com.example.geosamplemanager.data.settings.SettingsRepository
import com.example.geosamplemanager.data.voice.VoiceSettingsRepository
import com.example.geosamplemanager.data.voice.VoiceTtsHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vosk.Model

/**
 * FIX 5.9-db-restore-v2:
 *  - добавлен resetRepository() — после замены файла БД создаём
 *    свежий DatabaseRepository (новое соединение с новой БД).
 *
 * FIX 5.9-db-soft-restart:
 *  - restartRequest — StateFlow, через который UI может запросить
 *    пересоздание всех ViewModel'ей без пересоздания Activity.
 *    tick меняется каждый раз — это сигнал Compose обновить
 *    поддерево и обнулить ViewModelStore.
 *  - restartMessage — одноразовое сообщение, которое UI покажет
 *    после пересборки (например, «Готово. БД очищена»).
 */
data class RestartRequest(val tick: Int, val route: String)

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

    /**
     * FIX 5.9-db-soft-restart:
     * Запрос на пересоздание ViewModel'ей. Значение tick растёт
     * с каждым запросом — Compose реагирует на изменение.
     */
    private val _restartRequest = MutableStateFlow<RestartRequest?>(null)
    val restartRequest: StateFlow<RestartRequest?> = _restartRequest.asStateFlow()

    /**
     * FIX 5.9-db-soft-restart:
     * Сообщение для показа ПОСЛЕ пересборки. Одноразовое:
     * consumeRestartMessage читает и обнуляет.
     */
    private val _restartMessage = MutableStateFlow<String?>(null)

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

    /**
     * FIX 5.9-db-soft-restart:
     * Запросить пересоздание ViewModel'ей с возвратом на route.
     */
    fun requestRestart(route: String) {
        val currentTick = _restartRequest.value?.tick ?: 0
        _restartRequest.value = RestartRequest(currentTick + 1, route)
    }

    /**
     * FIX 5.9-db-soft-restart:
     * Сохранить сообщение для показа после пересборки. Например,
     * «Готово. БД очищена» — оно будет показано в новом поддереве.
     */
    fun scheduleRestartMessage(message: String) {
        _restartMessage.value = message
    }

    /**
     * FIX 5.9-db-soft-restart:
     * Прочитать и обнулить отложенное сообщение.
     * Вызывается из UI (DbScreen) один раз при появлении.
     */
    fun consumeRestartMessage(): String? {
        val m = _restartMessage.value
        _restartMessage.value = null
        return m
    }
}