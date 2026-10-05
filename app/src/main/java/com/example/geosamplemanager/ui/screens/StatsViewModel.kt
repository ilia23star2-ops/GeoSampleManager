package com.example.geosamplemanager.ui.screens

import android.app.Application
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geosamplemanager.GeoSampleApp
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import com.example.geosamplemanager.data.report.DecodedImage
import com.example.geosamplemanager.data.report.MultiHtmlReportGenerator
import com.example.geosamplemanager.data.report.ReportData
import com.example.geosamplemanager.data.report.ReportHtmlGenerator
import com.example.geosamplemanager.data.report.ReportNote
import com.example.geosamplemanager.data.report.ReportPhoto
import com.example.geosamplemanager.data.report.ReportSample
import com.example.geosamplemanager.data.report.XlsxMultiReportBuilder
import com.example.geosamplemanager.data.report.XlsxReportBuilder
import com.example.geosamplemanager.data.report.XlsxWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FIX 5.9-xlsx-ui: buildReportData, generateXlsxReport, decodeDataUri.
 *
 * FIX 5.9-multi-report-ui/1 (01.10.2026):
 *  - Мульти-отчёт: buildReportDataList, detectDuplicateSheetNames,
 *    generateMultiXlsxReport, generateMultiHtmlReport.
 *  - Вспомогательные: prepareOrdersForReport, DuplicateSheetGroup.
 */

/** Группа нарядов с одинаковым именем листа (для диалога дублей). */
data class DuplicateSheetGroup(
    val sheetName: String,
    val orderIds: List<Long>,
    val orderTitles: List<String>
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as GeoSampleApp).repository

    private var rawData: StatsData? = null
    private var currentFilter: StatsFilter = StatsFilter.ALL
    private var currentHideReady: Boolean = false

    private val _data = MutableStateFlow<StatsData?>(null)
    val data: StateFlow<StatsData?> = _data.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _hideReady = MutableStateFlow(false)
    val hideReady: StateFlow<Boolean> = _hideReady.asStateFlow()

    private val _expandedAreaIds = MutableStateFlow<Set<Long>>(emptySet())
    val expandedAreaIds: StateFlow<Set<Long>> = _expandedAreaIds.asStateFlow()

    private val _expandedOrderIds = MutableStateFlow<Set<Long>>(emptySet())
    val expandedOrderIds: StateFlow<Set<Long>> = _expandedOrderIds.asStateFlow()

    private val _selectedOrderId = MutableStateFlow<Long?>(null)
    val selectedOrderId: StateFlow<Long?> = _selectedOrderId.asStateFlow()

    private val _selectedAreaId = MutableStateFlow<Long?>(null)
    val selectedAreaId: StateFlow<Long?> = _selectedAreaId.asStateFlow()

    private val _drillStack = MutableStateFlow<List<DrillLevel>>(emptyList())
    val drillStack: StateFlow<List<DrillLevel>> = _drillStack.asStateFlow()

    private val _wellFilter = MutableStateFlow("")
    val wellFilter: StateFlow<String> = _wellFilter.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    private var searchJob: Job? = null

    init {
        subscribeToDb()
    }

    private fun subscribeToDb() {
        viewModelScope.launch {
            combine(
                repo.getAreasFlow(),
                repo.getAllOrdersFlow(),
                repo.getAllSamplesFlow()
            ) { areas, orders, samples ->
                withContext(Dispatchers.Default) {
                    buildTree(areas, orders, samples)
                }
            }.collect { raw ->
                rawData = raw
                applyFiltersAsync()
                pruneExpandedIds(raw)
                pruneSelected(raw)
            }
        }
    }

    private fun applyFiltersAsync() {
        viewModelScope.launch { applyFilters() }
    }

    private suspend fun applyFilters() {
        val raw = rawData ?: return
        val search = _searchQuery.value
        val filter = currentFilter
        val hideReady = currentHideReady

        val result = withContext(Dispatchers.Default) {
            raw
                .withFilter(filter)
                .withHideReady(hideReady)
                .withSearch(search)
                .copy(filter = filter)
        }
        _data.value = result
        if (search.isNotBlank()) expandAll()
    }

    private fun pruneExpandedIds(raw: StatsData) {
        val areaIds = raw.areas.map { it.areaId }.toSet()
        val orderIds = raw.areas.flatMap { it.orders.map { o -> o.orderId } }.toSet()
        _expandedAreaIds.value = _expandedAreaIds.value intersect areaIds
        _expandedOrderIds.value = _expandedOrderIds.value intersect orderIds
    }

    private fun pruneSelected(raw: StatsData) {
        val selOrder = _selectedOrderId.value
        if (selOrder != null) {
            val exists = raw.areas.any { area -> area.orders.any { it.orderId == selOrder } }
            if (!exists) {
                _selectedOrderId.value = null
                resetDrill()
            }
        }

        val selArea = _selectedAreaId.value
        if (selArea != null) {
            val exists = raw.areas.any { it.areaId == selArea }
            if (!exists) {
                _selectedAreaId.value = null
            }
        }
    }

    // ================================================================
    // Фильтры дерева
    // ================================================================

    fun setFilter(filter: StatsFilter) {
        currentFilter = filter
        applyFiltersAsync()
    }

    fun setHideReady(value: Boolean) {
        currentHideReady = value
        _hideReady.value = value
        applyFiltersAsync()
    }

    fun setSearchQuery(text: String) {
        _searchQuery.value = text
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            applyFilters()
        }
    }

    fun toggleArea(areaId: Long) {
        _expandedAreaIds.value = if (areaId in _expandedAreaIds.value)
            _expandedAreaIds.value - areaId
        else
            _expandedAreaIds.value + areaId
    }

    fun toggleOrder(orderId: Long) {
        _expandedOrderIds.value = if (orderId in _expandedOrderIds.value)
            _expandedOrderIds.value - orderId
        else
            _expandedOrderIds.value + orderId
    }

    fun expandAll() {
        val data = _data.value ?: return
        _expandedAreaIds.value = data.areas.map { it.areaId }.toSet()
        _expandedOrderIds.value = data.areas
            .flatMap { area -> area.orders.map { it.orderId } }
            .toSet()
    }

    fun collapseAll() {
        _expandedAreaIds.value = emptySet()
        _expandedOrderIds.value = emptySet()
    }

    fun selectOrder(orderId: Long) {
        _selectedOrderId.value = orderId
        _selectedAreaId.value = null
        resetDrill()
        val area = _data.value?.areas?.firstOrNull { a ->
            a.orders.any { it.orderId == orderId }
        } ?: return
        _expandedAreaIds.value = _expandedAreaIds.value + area.areaId
        _expandedOrderIds.value = _expandedOrderIds.value + orderId
    }

    fun selectArea(areaId: Long) {
        _selectedAreaId.value = areaId
        _selectedOrderId.value = null
        resetDrill()
        _expandedAreaIds.value = _expandedAreaIds.value + areaId
    }

    fun clearSelection() {
        _selectedOrderId.value = null
        _selectedAreaId.value = null
        resetDrill()
    }

    fun findOrder(orderId: Long): StatsOrderUi? {
        return rawData?.areas?.asSequence()
            ?.flatMap { it.orders.asSequence() }
            ?.firstOrNull { it.orderId == orderId }
    }

    fun findArea(areaId: Long): StatsAreaUi? {
        return rawData?.areas?.firstOrNull { it.areaId == areaId }
    }

    fun findAreaNameFor(orderId: Long): String? {
        return rawData?.areas?.firstOrNull { area ->
            area.orders.any { it.orderId == orderId }
        }?.areaName
    }

    // ================================================================
    // Drill-down
    // ================================================================

    fun pushDrill(level: DrillLevel) {
        _drillStack.value = _drillStack.value + level
        _wellFilter.value = ""
    }

    fun popDrill() {
        if (_drillStack.value.isNotEmpty()) {
            _drillStack.value = _drillStack.value.dropLast(1)
            _wellFilter.value = ""
        }
    }

    fun popToIndex(index: Int) {
        if (index < 0) return
        val size = index + 1
        if (size >= _drillStack.value.size) return
        _drillStack.value = _drillStack.value.take(size)
        _wellFilter.value = ""
    }

    fun resetDrill() {
        _drillStack.value = emptyList()
        _wellFilter.value = ""
    }

    fun setWellFilter(text: String) {
        _wellFilter.value = text
    }

    // ================================================================
    // Отчёты: одиночные (HTML и XLSX)
    // ================================================================

    suspend fun generateHtmlReport(orderId: Long, uri: Uri): Boolean {
        return try {
            val data = buildReportData(orderId) ?: return false

            val html = withContext(Dispatchers.IO) {
                ReportHtmlGenerator.generate(data)
            }

            withContext(Dispatchers.IO) {
                val bytes = html.toByteArray(Charsets.UTF_8)
                getApplication<Application>().contentResolver
                    .openOutputStream(uri, "w")
                    ?.use { out ->
                        out.write(bytes)
                        out.flush()
                    }
                    ?: run {
                        _message.value = "Не удалось открыть файл для записи"
                        return@withContext false
                    }
                Log.i(TAG, "HTML-отчёт записан: ${bytes.size} байт")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "generateHtmlReport failed", e)
            _message.value = "Ошибка отчёта: ${e.message}"
            false
        }
    }

    suspend fun generateXlsxReport(orderId: Long, uri: Uri): Boolean {
        return try {
            val data = buildReportData(orderId) ?: return false

            withContext(Dispatchers.IO) {
                val sheets = XlsxReportBuilder.build(
                    data = data,
                    imageDecoder = ::decodeDataUri
                )
                val bytes = XlsxWriter.toBytes(sheets)
                Log.i(TAG, "XLSX собран: ${bytes.size} байт, " +
                        "листов: ${sheets.size}, " +
                        "картинок: ${sheets.sumOf { it.images.size }}")

                getApplication<Application>().contentResolver
                    .openOutputStream(uri, "w")
                    ?.use { out ->
                        out.write(bytes)
                        out.flush()
                    }
                    ?: run {
                        _message.value = "Не удалось открыть файл для записи"
                        return@withContext false
                    }
                Log.i(TAG, "XLSX-отчёт записан: ${bytes.size} байт")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "generateXlsxReport failed", e)
            _message.value = "Ошибка отчёта: ${e.message}"
            false
        }
    }

    // ================================================================
    // Отчёты: мульти (N нарядов)
    // ================================================================

    /**
     * Проверить, есть ли среди выбранных нарядов совпадающие имена
     * листов. Возвращает только группы с count >= 2.
     */
    fun detectDuplicateSheetNames(orderIds: List<Long>): List<DuplicateSheetGroup> {
        val raw = rawData ?: return emptyList()
        val bySheet = mutableMapOf<String, MutableList<Pair<Long, String>>>()
        orderIds.forEach { orderId ->
            val area = raw.areas.firstOrNull { area ->
                area.orders.any { it.orderId == orderId }
            } ?: return@forEach
            val order = area.orders.firstOrNull { it.orderId == orderId }
                ?: return@forEach
            val name = XlsxMultiReportBuilder.previewSheetName(
                area.areaName, order.orderNumber
            )
            bySheet.getOrPut(name) { mutableListOf() }
                .add(orderId to "Наряд №${order.orderNumber}")
        }
        return bySheet
            .filter { it.value.size >= 2 }
            .map { (name, list) ->
                DuplicateSheetGroup(
                    sheetName = name,
                    orderIds = list.map { it.first },
                    orderTitles = list.map { it.second }
                )
            }
    }

    /**
     * Мульти-XLSX. Если [skipDuplicateNames] true — наряды с
     * дублирующимся именем листа исключаются (остаётся первый).
     */
    suspend fun generateMultiXlsxReport(
        orderIds: List<Long>,
        uri: Uri,
        skipDuplicateNames: Boolean
    ): Boolean {
        return try {
            val prepared = prepareOrdersForReport(orderIds, skipDuplicateNames)
            if (prepared.isEmpty()) {
                _message.value = "Нет нарядов для отчёта"
                return false
            }
            withContext(Dispatchers.IO) {
                val dataList = prepared.mapNotNull { buildReportData(it) }
                val sheets = XlsxMultiReportBuilder.build(
                    orders = dataList,
                    imageDecoder = ::decodeDataUri
                )
                val bytes = XlsxWriter.toBytes(sheets)
                Log.i(
                    TAG,
                    "XLSX multi: ${bytes.size} байт, листов: ${sheets.size}, " +
                            "картинок: ${sheets.sumOf { it.images.size }}"
                )

                getApplication<Application>().contentResolver
                    .openOutputStream(uri, "w")
                    ?.use { out ->
                        out.write(bytes)
                        out.flush()
                    }
                    ?: run {
                        _message.value = "Не удалось открыть файл для записи"
                        return@withContext false
                    }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "generateMultiXlsxReport failed", e)
            _message.value = "Ошибка отчёта: ${e.message}"
            false
        }
    }

    /**
     * Мульти-HTML. Логика дублей — как в XLSX.
     */
    suspend fun generateMultiHtmlReport(
        orderIds: List<Long>,
        uri: Uri,
        skipDuplicateNames: Boolean
    ): Boolean {
        return try {
            val prepared = prepareOrdersForReport(orderIds, skipDuplicateNames)
            if (prepared.isEmpty()) {
                _message.value = "Нет нарядов для отчёта"
                return false
            }
            withContext(Dispatchers.IO) {
                val dataList = prepared.mapNotNull { buildReportData(it) }
                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru", "RU"))
                val html = MultiHtmlReportGenerator.generate(
                    orders = dataList,
                    generatedAt = dateFormat.format(Date())
                )
                val bytes = html.toByteArray(Charsets.UTF_8)
                Log.i(TAG, "HTML multi: ${bytes.size} байт")

                getApplication<Application>().contentResolver
                    .openOutputStream(uri, "w")
                    ?.use { out ->
                        out.write(bytes)
                        out.flush()
                    }
                    ?: run {
                        _message.value = "Не удалось открыть файл для записи"
                        return@withContext false
                    }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "generateMultiHtmlReport failed", e)
            _message.value = "Ошибка отчёта: ${e.message}"
            false
        }
    }

    /**
     * Если skipDuplicates — оставляет из каждой группы дублей только
     * первый orderId (по порядку в переданном списке).
     */
    private fun prepareOrdersForReport(
        orderIds: List<Long>,
        skipDuplicates: Boolean
    ): List<Long> {
        if (!skipDuplicates) return orderIds
        val raw = rawData ?: return orderIds
        val seen = mutableSetOf<String>()
        val result = mutableListOf<Long>()
        orderIds.forEach { orderId ->
            val area = raw.areas.firstOrNull { area ->
                area.orders.any { it.orderId == orderId }
            } ?: return@forEach
            val order = area.orders.firstOrNull { it.orderId == orderId }
                ?: return@forEach
            val name = XlsxMultiReportBuilder.previewSheetName(
                area.areaName, order.orderNumber
            )
            if (name !in seen) {
                seen.add(name)
                result.add(orderId)
            }
        }
        return result
    }

    /**
     * data:image/jpeg;base64,AAA... → DecodedImage.
     * null — если формат не распознан.
     */
    private fun decodeDataUri(dataUri: String): DecodedImage? {
        return try {
            val commaIdx = dataUri.indexOf(',')
            if (commaIdx < 0) return null
            val meta = dataUri.substring(0, commaIdx)
            val b64 = dataUri.substring(commaIdx + 1)
            val ext = when {
                meta.contains("image/png", ignoreCase = true) -> "png"
                meta.contains("image/webp", ignoreCase = true) -> "webp"
                else -> "jpg"
            }
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            DecodedImage(bytes = bytes, extension = ext)
        } catch (e: Exception) {
            Log.w(TAG, "decodeDataUri failed: ${e.message}")
            null
        }
    }

    private suspend fun buildReportData(orderId: Long): ReportData? {
        val raw = rawData ?: run {
            _message.value = "Данные ещё не загружены"
            return null
        }

        val areaName = raw.areas.firstOrNull { area ->
            area.orders.any { it.orderId == orderId }
        }?.areaName ?: "Без участка"

        val order = raw.areas.asSequence()
            .flatMap { it.orders.asSequence() }
            .firstOrNull { it.orderId == orderId }
            ?: run {
                _message.value = "Наряд не найден"
                return null
            }

        val samples = buildReportSamples(order.group.rows)
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru", "RU"))
        return ReportData(
            areaName = areaName,
            orderNumber = order.orderNumber,
            generatedAt = dateFormat.format(Date()),
            stats = order.stats,
            samples = samples
        )
    }

    private suspend fun buildReportSamples(rows: List<SampleRow>): List<ReportSample> {
        return rows.map { row ->
            val sampleId = row.id.toLongOrNull()
            val note = if (sampleId != null) repo.getNote(sampleId) else null
            val photoEntities = if (sampleId != null) {
                repo.getPhotosForSample(sampleId)
            } else emptyList()

            val photos = photoEntities.mapNotNull { photo ->
                readImageAsDataUri(photo.imagePath)?.let { ReportPhoto(it) }
            }

            val reportNote = note?.noteText
                ?.takeIf { it.isNotBlank() }
                ?.let { ReportNote(it) }

            ReportSample(row = row, note = reportNote, photos = photos)
        }
    }

    private fun readImageAsDataUri(path: String): String? {
        return try {
            val f = File(path)
            if (!f.exists()) {
                Log.w(TAG, "readImageAsDataUri: файл не найден: $path")
                return null
            }
            val bytes = f.readBytes()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        } catch (e: Exception) {
            Log.e(TAG, "readImageAsDataUri: ошибка $path", e)
            null
        }
    }

    // ================================================================

    private fun buildTree(
        areas: List<AreaEntity>,
        orders: List<OrderEntity>,
        samples: List<SampleEntity>
    ): StatsData {
        val ordersByArea = orders.groupBy { it.areaId }
        val samplesByOrder = samples.groupBy { it.orderId }

        val areaUis = areas.map { area ->
            val areaOrders = ordersByArea[area.id].orEmpty()
            val orderUis = areaOrders.map { order ->
                val orderSamples = samplesByOrder[order.id].orEmpty()
                val group = buildSampleGroup(order, area, orderSamples)
                StatsOrderUi(
                    orderId = order.id,
                    orderNumber = order.orderNumber,
                    group = group,
                    stats = calculateGroupStats(group)
                )
            }
            StatsAreaUi(
                areaId = area.id,
                areaName = area.areaName,
                stats = combineStats(orderUis.map { it.stats }),
                orders = sortOrdersByStatus(orderUis)
            )
        }.sortedBy { it.areaName }

        val totals = combineStats(areaUis.map { it.stats })
        return StatsData(totals = totals, areas = areaUis, filter = StatsFilter.ALL)
    }

    companion object {
        private const val TAG = "StatsViewModel"
        private const val SEARCH_DEBOUNCE_MS = 250L
    }
}