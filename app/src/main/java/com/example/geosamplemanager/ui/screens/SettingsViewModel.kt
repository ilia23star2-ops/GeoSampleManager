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

enum class SettingsCategory(val title: String) {
    IMPORT("Импорт Excel"),
    VOICE("Голос"),
    APPEARANCE("Внешний вид"),
    SYSTEM("Система"),
    ABOUT("О приложении")
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as GeoSampleApp).settingsRepository

    private val _settings = MutableStateFlow(repo.load())
    val settings: StateFlow<ImportSettings> = _settings.asStateFlow()

    private val _selectedCategory = MutableStateFlow(SettingsCategory.IMPORT)
    val selectedCategory: StateFlow<SettingsCategory> = _selectedCategory.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    fun selectCategory(category: SettingsCategory) {
        _selectedCategory.value = category
    }

    /** Перечитать настройки из файла. Вызывается при открытии экрана. */
    fun reload() {
        _settings.value = repo.load()
    }

    private fun persist(updated: ImportSettings) {
        _settings.value = updated
        repo.save(updated)
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