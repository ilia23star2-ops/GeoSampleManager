package com.example.geosamplemanager.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geosamplemanager.GeoSampleApp
import com.example.geosamplemanager.data.settings.ImportSettings
import com.example.geosamplemanager.data.settings.OrderNumberRule
import com.example.geosamplemanager.data.settings.OrderSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as GeoSampleApp).settingsRepository

    private val _settings = MutableStateFlow(repo.load())
    val settings: StateFlow<ImportSettings> = _settings.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    // ============ Участки и префиксы ============

    fun addArea(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        val current = _settings.value
        if (current.areaPrefixes.containsKey(n)) {
            _message.value = "Участок «$n» уже есть"
            return
        }
        _settings.value = current.copy(
            areaPrefixes = current.areaPrefixes + (n to emptyList())
        )
    }

    fun removeArea(name: String) {
        val current = _settings.value
        _settings.value = current.copy(
            areaPrefixes = current.areaPrefixes - name
        )
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
        current.areaPrefixes.forEach { (k, v) ->
            if (k == oldName) updated[n] = v else updated[k] = v
        }
        _settings.value = current.copy(areaPrefixes = updated)
    }

    fun setAreaPrefixes(areaName: String, csv: String) {
        val list = csv.split(',', ';', ' ', '\n')
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .distinct()
        val current = _settings.value
        _settings.value = current.copy(
            areaPrefixes = current.areaPrefixes + (areaName to list)
        )
    }

    // ============ Общие правила ============

    fun setOrderSource(source: OrderSource) {
        _settings.value = _settings.value.copy(orderSource = source)
    }

    fun setOrderNumberRule(rule: OrderNumberRule) {
        _settings.value = _settings.value.copy(orderNumberRule = rule)
    }

    fun setHollowKeywords(csv: String) {
        _settings.value = _settings.value.copy(hollowKeywords = parseCsv(csv))
    }

    fun setAugerKeywords(csv: String) {
        _settings.value = _settings.value.copy(augerKeywords = parseCsv(csv))
    }

    fun setChannelKeywords(csv: String) {
        _settings.value = _settings.value.copy(channelKeywords = parseCsv(csv))
    }

    fun setBlankKeywords(csv: String) {
        _settings.value = _settings.value.copy(blankKeywords = parseCsv(csv))
    }

    fun setSkipBlanks(value: Boolean) {
        _settings.value = _settings.value.copy(skipBlanksWithoutData = value)
    }

    private fun parseCsv(s: String): List<String> =
        s.split(',', ';', '\n')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()

    // ============ Сохранение / сброс / экспорт / импорт ============

    fun save() {
        repo.save(_settings.value)
        _message.value = "Настройки сохранены"
    }

    fun resetToDefaults() {
        _settings.value = repo.resetToDefaults()
        _message.value = "Настройки сброшены к стандартным"
    }

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
                onFailure = {
                    _message.value = "Ошибка импорта: ${it.message}"
                }
            )
        }
    }
}