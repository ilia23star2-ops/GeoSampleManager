package com.example.geosamplemanager.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geosamplemanager.GeoSampleApp
import com.example.geosamplemanager.data.bluetooth.BluetoothController
import com.example.geosamplemanager.data.bluetooth.BluetoothSettings
import com.example.geosamplemanager.data.bluetooth.BtDevice
import com.example.geosamplemanager.data.bluetooth.BtProfile
import com.example.geosamplemanager.data.settings.ImportSettings
import com.example.geosamplemanager.data.settings.OrderNumberRule
import com.example.geosamplemanager.data.settings.OrderSource
import com.example.geosamplemanager.data.voice.TtsVolume
import com.example.geosamplemanager.data.voice.VoiceMode
import com.example.geosamplemanager.data.voice.VoiceSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SettingsCategory(val title: String) {
    IMPORT("Импорт Excel"),
    VOICE("Голос"),
    BLUETOOTH("Bluetooth"),
    APPEARANCE("Внешний вид"),
    SYSTEM("Система"),
    ABOUT("О приложении")
}

/**
 * FIX 5.9-settings: раздел «Голос» наполнен.
 * FIX 5.9-settings-bt: раздел «Bluetooth» наполнен.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as GeoSampleApp).settingsRepository
    private val voiceRepo = (application as GeoSampleApp).voiceSettingsRepository
    private val btRepo = (application as GeoSampleApp).bluetoothSettingsRepository

    private val btController = BluetoothController(application.applicationContext)

    private val _settings = MutableStateFlow(repo.load())
    val settings: StateFlow<ImportSettings> = _settings.asStateFlow()

    private val _voiceSettings = MutableStateFlow(VoiceSettings())
    val voiceSettings: StateFlow<VoiceSettings> = _voiceSettings.asStateFlow()

    private val _btSettings = MutableStateFlow(BluetoothSettings())
    val btSettings: StateFlow<BluetoothSettings> = _btSettings.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BtDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BtDevice>> = _pairedDevices.asStateFlow()

    private val _btEnabled = MutableStateFlow(false)
    val btEnabled: StateFlow<Boolean> = _btEnabled.asStateFlow()

    private val _btHasPermission = MutableStateFlow(true)
    val btHasPermission: StateFlow<Boolean> = _btHasPermission.asStateFlow()

    private val _btActiveDevice = MutableStateFlow<BtDevice?>(null)
    val btActiveDevice: StateFlow<BtDevice?> = _btActiveDevice.asStateFlow()

    private val _selectedCategory = MutableStateFlow(SettingsCategory.IMPORT)
    val selectedCategory: StateFlow<SettingsCategory> = _selectedCategory.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            _voiceSettings.value = voiceRepo.load()
            _btSettings.value = btRepo.load()
            refreshBluetoothState()
        }
    }

    fun clearMessage() { _message.value = null }

    fun selectCategory(category: SettingsCategory) {
        _selectedCategory.value = category
        if (category == SettingsCategory.BLUETOOTH) {
            refreshBluetoothState()
        }
    }

    /** Перечитать настройки из файлов. Вызывается при открытии экрана. */
    fun reload() {
        _settings.value = repo.load()
        viewModelScope.launch {
            _voiceSettings.value = voiceRepo.load()
            _btSettings.value = btRepo.load()
            refreshBluetoothState()
        }
    }

    private fun persist(updated: ImportSettings) {
        _settings.value = updated
        repo.save(updated)
    }

    private fun persistVoice(updated: VoiceSettings) {
        _voiceSettings.value = updated
        viewModelScope.launch { voiceRepo.save(updated) }
    }

    private fun persistBt(updated: BluetoothSettings) {
        _btSettings.value = updated
        viewModelScope.launch { btRepo.save(updated) }
    }

    // ============ УЧАСТКИ ============

    fun addArea(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        val current = _settings.value
        if (current.areaPrefixes.containsKey(n)) {
            _message.value = "Участок «$n» уже есть"
            return
        }
        persist(current.copy(areaPrefixes = current.areaPrefixes + (n to emptyList())))
    }

    fun removeArea(name: String) {
        val current = _settings.value
        persist(current.copy(areaPrefixes = current.areaPrefixes - name))
    }

    fun renameArea(oldName: String, newName: String) {
        val n = newName.trim()
        if (n.isEmpty() || oldName == n) return
        val current = _settings.value
        if (current.areaPrefixes.containsKey(n)) {
            _message.value = "Участок «$n» уже есть"
            return
        }
        val updated = LinkedHashMap<String, List<String>>()
        current.areaPrefixes.forEach { (k, v) -> if (k == oldName) updated[n] = v else updated[k] = v }
        persist(current.copy(areaPrefixes = updated))
    }

    fun setAreaPrefixes(areaName: String, csv: String) {
        val list = csv.split(',', ';', ' ', '\n')
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .distinct()
        val current = _settings.value
        persist(current.copy(areaPrefixes = current.areaPrefixes + (areaName to list)))
    }

    // ============ НАРЯД ============

    fun setOrderSource(source: OrderSource) {
        persist(_settings.value.copy(orderSource = source))
    }

    fun setOrderNumberRule(rule: OrderNumberRule) {
        persist(_settings.value.copy(orderNumberRule = rule))
    }

    // ============ БЛАНКИ ============

    fun setBlankKeywords(csv: String) {
        val list = csv.split(',', ';', '\n')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
        persist(_settings.value.copy(blankKeywords = list))
    }

    fun setSkipBlanks(value: Boolean) {
        persist(_settings.value.copy(skipBlanksWithoutData = value))
    }

    // ============ ПОЛЬЗОВАТЕЛЬСКИЕ СЛОВА ============

    fun addUserHeaderKeyword(role: String, word: String) {
        persist(_settings.value.addUserHeaderKeyword(role, word))
    }

    fun removeUserHeaderKeyword(role: String, word: String) {
        persist(_settings.value.removeUserHeaderKeyword(role, word))
    }

    fun addUserTypeKeyword(typeCode: String, word: String) {
        persist(_settings.value.addUserTypeKeyword(typeCode, word))
    }

    fun removeUserTypeKeyword(typeCode: String, word: String) {
        persist(_settings.value.removeUserTypeKeyword(typeCode, word))
    }

    fun resetUserKeywords() {
        persist(_settings.value.resetUserKeywords())
        _message.value = "Пользовательские слова сброшены"
    }

    // ============ СБРОС ============

    fun resetToDefaults() {
        _settings.value = repo.resetToDefaults()
        _message.value = "Настройки сброшены к стандартным"
    }

    // ============ ГОЛОСОВОЙ ПОМОЩНИК ============

    fun setTtsVolume(volume: TtsVolume) {
        persistVoice(_voiceSettings.value.copy(ttsVolume = volume))
    }

    fun setVoiceMode(mode: VoiceMode) {
        persistVoice(_voiceSettings.value.copy(mode = mode))
    }

    fun setAutoStopMinutes(minutes: Int) {
        persistVoice(_voiceSettings.value.copy(autoStopMinutes = minutes))
    }

    fun setShowCharacteristic(value: Boolean) {
        persistVoice(_voiceSettings.value.copy(showCharacteristic = value))
    }

    fun setShowOnboarding(value: Boolean) {
        persistVoice(_voiceSettings.value.copy(showOnboarding = value))
    }

    fun setUseGrammar(value: Boolean) {
        persistVoice(_voiceSettings.value.copy(useGrammar = value))
    }

    fun resetVoiceToDefaults() {
        persistVoice(VoiceSettings())
        _message.value = "Настройки голоса сброшены"
    }

    // ============ BLUETOOTH ============

    /**
     * Перечитать состояние Bluetooth: разрешение, адаптер,
     * список сопряжённых, активное устройство.
     */
    fun refreshBluetoothState() {
        _btHasPermission.value = btController.hasPermission()
        _btEnabled.value = btController.isBluetoothEnabled()
        _pairedDevices.value = if (_btEnabled.value) {
            btController.listPairedDevices()
        } else {
            emptyList()
        }
        _btActiveDevice.value = btController.currentActiveDevice()
    }

    fun setBluetoothEnabled(value: Boolean) {
        persistBt(_btSettings.value.copy(enabled = value))
    }

    /**
     * Выбор устройства. Если выбран встроенный — записываем
     * address = null, enabled = false. Если внешний — записываем
     * address + name, enabled = true.
     */
    fun setBluetoothDevice(device: BtDevice) {
        if (device.isBuiltIn) {
            persistBt(
                _btSettings.value.copy(
                    enabled = false,
                    deviceAddress = null,
                    deviceName = null
                )
            )
        } else {
            persistBt(
                _btSettings.value.copy(
                    enabled = true,
                    deviceAddress = device.address,
                    deviceName = device.name
                )
            )
        }
    }

    fun setBtProfile(profile: BtProfile) {
        persistBt(_btSettings.value.copy(profile = profile))
    }

    fun setFallbackToBuiltIn(value: Boolean) {
        persistBt(_btSettings.value.copy(fallbackToBuiltIn = value))
    }

    fun openBluetoothSystemSettings() {
        btController.openSystemSettings()
    }

    // ============ ЭКСПОРТ / ИМПОРТ ============

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val r = repo.exportTo(uri, _settings.value)
            _message.value = r.fold(
                onSuccess = { "Настройки экспортированы" },
                onFailure = { "Ошибка экспорта: ${it.message}" }
            )
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val r = repo.importFrom(uri)
            r.fold(
                onSuccess = {
                    _settings.value = it
                    repo.save(it)
                    _message.value = "Настройки импортированы"
                },
                onFailure = { _message.value = "Ошибка импорта: ${it.message}" }
            )
        }
    }
}