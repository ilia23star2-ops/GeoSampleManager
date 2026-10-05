package com.example.geosamplemanager

import android.app.Application
import com.example.geosamplemanager.data.DatabaseRepository
import com.example.geosamplemanager.data.history.ImportHistoryRepository
import com.example.geosamplemanager.data.logs.CrashHandler
import com.example.geosamplemanager.data.logs.DeviceInfo
import com.example.geosamplemanager.data.logs.Log
import com.example.geosamplemanager.data.logs.LogWriter
import com.example.geosamplemanager.data.logs.LogsDatabase
import com.example.geosamplemanager.data.settings.SettingsRepository
import com.example.geosamplemanager.data.voice.VoiceSettingsRepository
import com.example.geosamplemanager.data.voice.VoiceTtsHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 *
 * FIX 5.9-logs-3:
 *  - LogWriter.init() — старт писателя журнала;
 *  - CrashHandler.install() — глобальный перехват падений;
 *  - запись app_start с данными устройства и счётчиками БД;
 *  - чтение pending_crash.json из прошлого запуска.
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

    /**
     * FIX 5.9-logs-3:
     * Отдельный scope для логирования старта и чтения pending-крэша.
     * SupervisorJob — не валим приложение, если что-то в корутине
     * упадёт (журнал — вспомогательная функция, не критичная).
     */
    private val logsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // FIX 5.9-logs-3: журнал и крэш-хендлер — раньше всего
        // остального, чтобы поймать падения при инициализации БД.
        LogWriter.init(this)
        CrashHandler.install(this)

        repository = DatabaseRepository(this)
        settingsRepository = SettingsRepository(this)
        importHistoryRepository = ImportHistoryRepository(this)
        voiceSettingsRepository = VoiceSettingsRepository(this)

        VoiceTtsHolder.init(this)

        logAppStart()
    }

    /**
     * FIX 5.9-logs-3:
     * Запись app_start в журнал. Плюс, если в прошлой сессии
     * приложение упало — читаем pending_crash.json и пишем
     * отдельной записью категории ERROR.
     */
    private fun logAppStart() {
        logsScope.launch {
            // Сначала читаем прошлый крэш (если был) — до записи
            // app_start, чтобы в порядке лога крэш шёл первым и
            // был явно виден как причина запуска.
            val previousCrash = CrashHandler.readAndClear(this@GeoSampleApp)
            if (previousCrash != null) {
                Log.error("Приложение было аварийно завершено в прошлой сессии")
                    .detail("crash_time", previousCrash.timestamp)
                    .detail("crash_session", previousCrash.sessionId)
                    .detail("crash_thread", previousCrash.threadName)
                    .detail("error_type", previousCrash.exceptionType)
                    .detail("error_message", previousCrash.message)
                    .detail("error_stack", previousCrash.stackTrace)
                    .write()
            }

            // Основная запись app_start.
            val details = HashMap<String, Any?>(
                DeviceInfo.snapshot(this@GeoSampleApp)
            )
            details["previous_crash"] = previousCrash != null
            details["session"] = LogWriter.currentSessionId()

            try {
                val info = repository.getDbInfo()
                details["samples"] = info.samplesCount
                details["orders"] = info.ordersCount
                details["areas"] = info.areasCount
            } catch (_: Exception) {
                details["samples"] = null
                details["orders"] = null
                details["areas"] = null
            }

            Log.app("Приложение запущено")
                .details(details)
                .write()
        }
    }

    /**
     * FIX 5.9-logs-3:
     * Закрыть LogsDatabase при завершении процесса. Не критично
     * (Room сам закрывает), но полезно для чистоты.
     */
    override fun onTerminate() {
        super.onTerminate()
        try { LogsDatabase.closeAndReset() } catch (_: Exception) {}
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