package com.example.geosamplemanager.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geosamplemanager.GeoSampleApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel экрана «Сверка и поиск».
 *
 * Оптимизация: одиночные действия используют атомарные UPDATE в БД —
 * без чтения строки перед записью.
 */
class ReconciliationViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as GeoSampleApp).repository

    val state = ReconciliationState(emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    init {
        loadAll()
    }

    private fun loadAll() {
        viewModelScope.launch {
            try {
                val areas = repo.getAreas()
                val allGroups = mutableListOf<SampleGroup>()
                areas.forEach { area ->
                    val orders = repo.getOrdersForAreaList(area.id)
                    orders.forEach { order ->
                        val samples = repo.getSamplesForOrderList(order.id)
                        if (samples.isNotEmpty()) {
                            allGroups.add(buildSampleGroup(order, area, samples))
                        }
                    }
                }
                state.setGroups(allGroups)
            } catch (e: Exception) {
                _message.value = "Ошибка загрузки: ${e.message}"
            }
        }
    }

    private fun rowById(rowId: String): SampleRow? =
        state.groups.flatMap { it.rows }.firstOrNull { it.id == rowId }

    // ================================================================
    // Одиночные действия — атомарные UPDATE
    // ================================================================

    fun toggleFound(rowId: String) {
        state.toggleFound(rowId)
        val id = rowId.toLongOrNull() ?: return
        val found = rowById(rowId)?.found ?: return
        viewModelScope.launch {
            try { repo.setFound(id, found) }
            catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setFound(rowId: String, value: Boolean) {
        state.setFound(rowId, value)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try { repo.setFound(id, value) }
            catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setControlWeightAndFound(rowId: String, weight: Double) {
        state.setControlWeightAndFound(rowId, weight)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try {
                repo.setControlWeight(id, weight)
                repo.setFound(id, true)
            } catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setControlWeight(rowId: String, weight: Double) {
        state.setControlWeight(rowId, weight)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try { repo.setControlWeight(id, weight) }
            catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setBlankWeightAndMarkFound(rowId: String, weight: Double) {
        state.setBlankWeightAndMarkFound(rowId, weight)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try {
                repo.setWeight(id, weight)
                repo.setFound(id, true)
            } catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setWeight(rowId: String, weight: Double) {
        state.setWeight(rowId, weight)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try { repo.setWeight(id, weight) }
            catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setPostponed(rowId: String, value: Boolean) {
        state.setPostponed(rowId, value)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try { repo.setPostponed(id, value) }
            catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun toggleWeightControl(rowId: String): Boolean {
        val ok = state.toggleWeightControl(rowId)
        if (ok) {
            val id = rowId.toLongOrNull() ?: return ok
            val flag = rowById(rowId)?.weightControl ?: return ok
            viewModelScope.launch {
                try { repo.setWeightControl(id, flag) }
                catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
            }
        }
        return ok
    }

    // ================================================================
    // Массовые действия — сохранение группы
    // ================================================================

    fun applyBulkMarkFound(
        groupId: String,
        weights: Map<String, Double>,
        postponedActions: Map<String, Boolean>
    ): Int {
        val marked = state.applyBulkMarkFound(groupId, weights, postponedActions)
        persistGroup(groupId)
        return marked
    }

    fun clearAllFound(groupId: String) {
        state.clearAllFound(groupId)
        persistGroup(groupId)
    }

    // ================================================================
    // Удаление
    // ================================================================

    fun deleteRow(rowId: String, recalc: Boolean) {
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try {
                repo.deleteSampleWithRenumber(id, recalc)
                state.deleteRow(rowId, recalc)
            } catch (e: Exception) {
                _message.value = "Ошибка удаления: ${e.message}"
            }
        }
    }

    // ================================================================
    // Undo / Redo
    // ================================================================

    fun undo() {
        state.undo()
        persistAll()
    }

    fun redo() {
        state.redo()
        persistAll()
    }

    // ================================================================
    // Настройки наряда
    // ================================================================

    fun applyBlankSettingsForOrder(
        orderTitle: String,
        settings: BlankWeightSettings,
        weightControlStep: Int
    ): Int {
        val changed = state.applyBlankSettingsForOrder(orderTitle, settings, weightControlStep)
        persistOrder(orderTitle)
        return changed
    }

    fun resetBlankSettingsToGlobal(orderTitle: String): Int {
        val changed = state.resetBlankSettingsToGlobal(orderTitle)
        persistOrder(orderTitle)
        return changed
    }

    fun resetBlankWeightsForOrder(orderTitle: String): Int {
        val changed = state.resetBlankWeightsForOrder(orderTitle)
        persistOrder(orderTitle)
        return changed
    }

    // ================================================================
    // Сохранение
    // ================================================================

    private fun persistGroup(groupId: String) {
        val group = state.groups.firstOrNull { it.id == groupId } ?: return
        viewModelScope.launch {
            try { repo.saveRows(group.rows) }
            catch (e: Exception) { _message.value = "Ошибка сохранения: ${e.message}" }
        }
    }

    private fun persistOrder(orderTitle: String) {
        val groups = state.groups.filter { it.orderTitle == orderTitle }
        viewModelScope.launch {
            try { groups.forEach { repo.saveRows(it.rows) } }
            catch (e: Exception) { _message.value = "Ошибка сохранения: ${e.message}" }
        }
    }

    private fun persistAll() {
        viewModelScope.launch {
            try { state.groups.forEach { repo.saveRows(it.rows) } }
            catch (e: Exception) { _message.value = "Ошибка сохранения: ${e.message}" }
        }
    }
}