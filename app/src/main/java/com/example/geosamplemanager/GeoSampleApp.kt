package com.example.geosamplemanager

import android.app.Application
import com.example.geosamplemanager.data.DatabaseRepository
import com.example.geosamplemanager.data.bluetooth.BluetoothSettingsRepository
import com.example.geosamplemanager.data.history.ImportHistoryRepository
import com.example.geosamplemanager.data.logs.CrashHandler
import com.example.geosamplemanager.data.logs.DeviceInfo
import com.example.geosamplemanager.data.logs.Log
import com.example.geosamplemanager.data.logs.LogWriter
import com.example.geosamplemanager.data.logs.LogsDatabase
import com.example.geosamplemanager.data.session.SessionStateRepository
import com.example.geosamplemanager.data.settings.AppearanceSettings
import com.example.geosamplemanager.data.settings.AppearanceSettingsRepository
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
 * FIX 5.9-db-restore-v2: resetRepository().
 * FIX 5.9-db-soft-restart: restartRequest, restartMessage.
 * FIX 5.9-logs-3: LogWriter.init, CrashHandler.install, app_start.
 * FIX 5.9-settings-bt: bluetoothSettingsRepository.
 * FIX 5.9-settings-scale: appearanceSettingsRepository, appearance.
 * FIX 5.9-settings-theme: тема в AppearanceSettings.
 *
 * FIX 5.9-main-a:
 *  - sessionStateRepository — последний активный наряд;
 *  - pendingSearchRequest — одноразовый запрос «открыть Сверку
 *    с конкретным нарядом». Устанавливается с Главной, читается
 *    в ReconciliationViewModel при первом появлении экрана.
 */
data class RestartRequest(val tick: Int, val route: String)

/**
 * FIX 5.9-main-a:
 * Одноразовый запрос от Главной — открыть Сверку с выбранным
 * нарядом. ReconciliationViewModel в init читает и очищает.
 */
data class PendingSearchRequest(
    val orderId: Long,
    val areaTitle: String,
    val orderTitle: String
)

/**
 * FIX 5.9-main-b:
 * Одноразовый запрос от Главной — открыть Статистику с уже
 * выбранным нарядом для отчёта. StatsScreen читает в LaunchedEffect
 * и открывает ReportFormatDialog.
 */
data class PendingReportRequest(
    val orderId: Long
)

class GeoSampleApp : Application() {

    lateinit var repository: DatabaseRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var importHistoryRepository: ImportHistoryRepository
        private set

    lateinit var voiceSettingsRepository: VoiceSettingsRepository
        private set

    lateinit var bluetoothSettingsRepository: BluetoothSettingsRepository
        private set

    lateinit var appearanceSettingsRepository: AppearanceSettingsRepository
        private set

    lateinit var sessionStateRepository: SessionStateRepository
        private set

    var voiceModel: Model? = null

    var voiceUseGrammar: Boolean = true

    private val _restartRequest = MutableStateFlow<RestartRequest?>(null)
    val restartRequest: StateFlow<RestartRequest?> = _restartRequest.asStateFlow()

    private val _restartMessage = MutableStateFlow<String?>(null)

    private val _appearance = MutableStateFlow(AppearanceSettings())
    val appearance: StateFlow<AppearanceSettings> = _appearance.asStateFlow()

    /**
     * FIX 5.9-main-a:
     * Одноразовый запрос «открыть Сверку с нарядом X». Устанавливается
     * с Главной перед навигацией. ReconciliationViewModel в init
     * читает и очищает.
     */
    private val _pendingSearchRequest = MutableStateFlow<PendingSearchRequest?>(null)

    /**
     * FIX 5.9-main-b:
     * Одноразовый запрос от Главной — открыть Статистику с
     * выбранным нарядом для отчёта.
     */
    private val _pendingReportRequest = MutableStateFlow<PendingReportRequest?>(null)

    private val logsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        LogWriter.init(this)
        CrashHandler.install(this)

        repository = DatabaseRepository(this)
        settingsRepository = SettingsRepository(this)
        importHistoryRepository = ImportHistoryRepository(this)
        voiceSettingsRepository = VoiceSettingsRepository(this)
        bluetoothSettingsRepository = BluetoothSettingsRepository(this)

        appearanceSettingsRepository = AppearanceSettingsRepository(this)
        _appearance.value = appearanceSettingsRepository.load()

        sessionStateRepository = SessionStateRepository(this)

        VoiceTtsHolder.init(this)

        logAppStart()
    }

    fun updateAppearance(settings: AppearanceSettings) {
        _appearance.value = settings
        appearanceSettingsRepository.save(settings)
    }

    /**
     * FIX 5.9-main-a:
     * Запросить открытие Сверки с конкретным нарядом.
     */
    fun requestSearchForOrder(orderId: Long, areaTitle: String, orderTitle: String) {
        _pendingSearchRequest.value = PendingSearchRequest(
            orderId = orderId,
            areaTitle = areaTitle,
            orderTitle = orderTitle
        )
    }

    /**
     * FIX 5.9-main-a:
     * Прочитать и обнулить запрос. Вызывается из ReconciliationViewModel.
     */
    fun consumeSearchRequest(): PendingSearchRequest? {
        val r = _pendingSearchRequest.value
        _pendingSearchRequest.value = null
        return r
    }
    /**
     * FIX 5.9-main-b:
     * Запросить открытие Статистики с конкретным нарядом для отчёта.
     */
    fun requestReportFor(orderId: Long) {
        _pendingReportRequest.value = PendingReportRequest(orderId = orderId)
    }

    /**
     * FIX 5.9-main-b:
     * Прочитать и обнулить запрос. Вызывается из StatsScreen.
     */
    fun consumeReportRequest(): PendingReportRequest? {
        val r = _pendingReportRequest.value
        _pendingReportRequest.value = null
        return r
    }
    private fun logAppStart() {
        logsScope.launch {
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

    override fun onTerminate() {
        super.onTerminate()
        try { LogsDatabase.closeAndReset() } catch (_: Exception) {}
    }

    fun resetRepository() {
        repository = DatabaseRepository(this)
    }

    fun requestRestart(route: String) {
        val currentTick = _restartRequest.value?.tick ?: 0
        _restartRequest.value = RestartRequest(currentTick + 1, route)
    }

    fun scheduleRestartMessage(message: String) {
        _restartMessage.value = message
    }

    fun consumeRestartMessage(): String? {
        val m = _restartMessage.value
        _restartMessage.value = null
        return m
    }
}