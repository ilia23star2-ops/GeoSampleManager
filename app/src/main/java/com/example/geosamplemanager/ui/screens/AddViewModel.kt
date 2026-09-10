package com.example.geosamplemanager.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geosamplemanager.GeoSampleApp
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import com.example.geosamplemanager.data.excel.ExcelAnalyzer
import com.example.geosamplemanager.data.excel.ExcelImporter
import com.example.geosamplemanager.data.excel.ParsedOrder
import com.example.geosamplemanager.data.excel.SheetAnalysis
import com.example.geosamplemanager.data.excel.XlsxReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ImportPreview(
    val fileName: String,
    val sheetName: String,
    val headerRowIndex: Int,
    val mapping: Map<String, Int?>,
    val headers: List<String>,
    val rows: List<List<String>>,
    val order: ParsedOrder
)

class AddViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as GeoSampleApp).repository

    private val _areas = MutableStateFlow<List<AreaEntity>>(emptyList())
    val areas: StateFlow<List<AreaEntity>> = _areas.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _preview = MutableStateFlow<ImportPreview?>(null)
    val preview: StateFlow<ImportPreview?> = _preview.asStateFlow()

    init {
        viewModelScope.launch {
            _areas.value = repo.getAreas()
        }
    }

    fun clearMessage() { _message.value = null }
    fun clearPreview() { _preview.value = null }

    fun refreshAreas() {
        viewModelScope.launch { _areas.value = repo.getAreas() }
    }

    fun importFromUri(uri: Uri, fileName: String) {
        _busy.value = true
        viewModelScope.launch {
            try {
                val sheets = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver
                        .openInputStream(uri)?.use { XlsxReader.read(it) }
                        ?: throw IllegalStateException("Не удалось открыть файл")
                }

                if (sheets.isEmpty()) {
                    _message.value = "В файле нет листов"
                    return@launch
                }

                val analysis: SheetAnalysis? = withContext(Dispatchers.Default) {
                    ExcelAnalyzer.analyze(sheets)
                }

                if (analysis == null) {
                    _message.value = "Не найдена строка заголовков. Проверьте файл."
                    return@launch
                }

                val order = withContext(Dispatchers.Default) {
                    ExcelImporter.buildOrder(
                        rows = analysis.rows,
                        headerRowIdx = analysis.headerRowIndex,
                        mapping = analysis.mapping,
                        areaName = null,
                        orderNumber = "Импорт от " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
                    )
                }

                _preview.value = ImportPreview(
                    fileName = fileName,
                    sheetName = analysis.sheetName,
                    headerRowIndex = analysis.headerRowIndex,
                    mapping = analysis.mapping,
                    headers = analysis.rows.getOrNull(analysis.headerRowIndex) ?: emptyList(),
                    rows = analysis.rows,
                    order = order
                )
            } catch (e: Exception) {
                _message.value = "Ошибка чтения: ${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun confirmImport(areaName: String, orderNumber: String) {
        val p = _preview.value ?: return
        if (areaName.isBlank() || orderNumber.isBlank()) {
            _message.value = "Укажите участок и номер наряда"
            return
        }

        _busy.value = true
        viewModelScope.launch {
            try {
                var areaId = repo.getAreaId(areaName)
                if (areaId == null) {
                    areaId = repo.addArea(areaName)
                    if (areaId <= 0) {
                        areaId = repo.getAreaId(areaName)
                            ?: throw IllegalStateException("Не удалось создать участок")
                    }
                }

                var orderId = repo.getOrderId(areaId, orderNumber)
                if (orderId == null) {
                    orderId = repo.addOrder(areaId, orderNumber)
                    if (orderId <= 0) {
                        orderId = repo.getOrderId(areaId, orderNumber)
                            ?: throw IllegalStateException("Не удалось создать наряд")
                    }
                }

                val wellsSet = mutableSetOf<String>()
                var added = 0
                for (s in p.order.samples) {
                    val entity = SampleEntity(
                        orderId = orderId,
                        serialNumber = s.serialNumber,
                        sampleNumber = s.sampleNumber,
                        wellNumber = s.wellNumber,
                        workings = s.workings,
                        intervalFrom = s.intervalFrom,
                        intervalTo = s.intervalTo,
                        weight = s.weight,
                        sampleType = s.sampleType,
                        status = s.status,
                        found = false
                    )
                    val res = repo.addSample(entity)
                    if (res > 0) added++
                    if (s.wellNumber.isNotEmpty()) wellsSet.add(s.wellNumber)
                }

                for (w in wellsSet) {
                    try { repo.addWell(orderId, w) } catch (_: Exception) {}
                }

                _preview.value = null
                _message.value = "Импортировано $added проб, скважин: ${wellsSet.size}"
            } catch (e: Exception) {
                _message.value = "Ошибка импорта: ${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }
}