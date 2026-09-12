package com.example.geosamplemanager.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geosamplemanager.GeoSampleApp
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import com.example.geosamplemanager.data.excel.AreaResolver
import com.example.geosamplemanager.data.excel.ExcelAnalyzer
import com.example.geosamplemanager.data.excel.ExcelImporter
import com.example.geosamplemanager.data.excel.ParsedOrder
import com.example.geosamplemanager.data.excel.SheetAnalysis
import com.example.geosamplemanager.data.excel.SheetMeta
import com.example.geosamplemanager.data.excel.XlsxReader
import com.example.geosamplemanager.data.history.ImportHistoryEntry
import com.example.geosamplemanager.data.history.ImportHistoryItem
import com.example.geosamplemanager.data.settings.ImportSettings
import com.example.geosamplemanager.data.settings.normalizeHeaderWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ProcessedStatus { IMPORTED, REPLACED, SKIPPED, GENERAL_LIST, ERROR, EXISTING_SKIPPED }

enum class ConflictAction { ADD, SKIP, REPLACE }

data class PendingConflict(
    val areaName: String,
    val orderNumber: String,
    val existingSamples: Int,
    val newSamples: Int
)

data class ProcessedItem(
    val sheetName: String,
    val areaName: String,
    val orderNumber: String,
    val sampleCount: Int,
    val status: ProcessedStatus,
    val reason: String? = null
)

data class ImportPreview(
    val fileName: String,
    val sheetName: String,
    val sheetPath: String,
    val sheetIndex: Int,
    val totalSheets: Int,
    val headerRowIndex: Int,
    val twoRowHeader: Boolean,
    val mapping: Map<String, Int?>,
    val autoMapping: Map<String, Int?>,
    val headers: List<String>,
    val rows: List<List<String>>,
    val order: ParsedOrder,
    val areaAutoDetected: Boolean,
    val orderAutoDetected: Boolean,
    val skippedBlanks: Int,
    val skippedEmpty: Int,
    val queuePosition: Int,
    val queueTotal: Int,
    val importedCount: Int,
    val skippedCount: Int,
    val existingSkippedCount: Int,
    val isGeneralList: Boolean,
    val sheetSampleCount: Int,
    val existingConflict: Boolean,
    val existingSamples: Int
) {
    fun mappingWasEdited(): Boolean = mapping != autoMapping
}

data class QueueState(
    val isFinished: Boolean = false,
    val isCancelled: Boolean = false,
    val totalSheets: Int = 0,
    val currentIndex: Int = 0,
    val importedCount: Int = 0,
    val replacedCount: Int = 0,
    val skippedCount: Int = 0,
    val existingSkippedCount: Int = 0,
    val generalListCount: Int = 0,
    val errorCount: Int = 0,
    val processedItems: List<ProcessedItem> = emptyList()
) {
    val progress: Float
        get() = if (totalSheets <= 0) 0f
        else (currentIndex.toFloat() / totalSheets.toFloat()).coerceIn(0f, 1f)

    val totalSamplesImported: Int
        get() = processedItems
            .filter { it.status == ProcessedStatus.IMPORTED || it.status == ProcessedStatus.REPLACED }
            .sumOf { it.sampleCount }
}

class AddViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GeoSampleApp
    private val repo = app.repository
    private val settingsRepo = app.settingsRepository
    private val historyRepo = app.importHistoryRepository

    private val _areas = MutableStateFlow<List<AreaEntity>>(emptyList())
    val areas: StateFlow<List<AreaEntity>> = _areas.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _preview = MutableStateFlow<ImportPreview?>(null)
    val preview: StateFlow<ImportPreview?> = _preview.asStateFlow()

    private val _queueState = MutableStateFlow(QueueState())
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    private val _history = MutableStateFlow<List<ImportHistoryEntry>>(emptyList())
    val history: StateFlow<List<ImportHistoryEntry>> = _history.asStateFlow()

    private val _pendingConflict = MutableStateFlow<PendingConflict?>(null)
    val pendingConflict: StateFlow<PendingConflict?> = _pendingConflict.asStateFlow()

    private var currentAnalysis: SheetAnalysis? = null
    private var currentSheetMeta: SheetMeta? = null
    private var currentFileName: String = ""
    private var currentFileNameWithExt: String = ""
    private var currentTotalSheets: Int = 1
    private var currentFallbackOrder: String = ""

    private var queueMetas: List<SheetMeta> = emptyList()
    private var queueIndex: Int = 0
    private var importedCount: Int = 0
    private var replacedCount: Int = 0
    private var skippedCount: Int = 0
    private var existingSkippedCount: Int = 0
    private var generalListCount: Int = 0
    private var errorCount: Int = 0
    private var processedItems: MutableList<ProcessedItem> = mutableListOf()
    private var openStreamFactory: (() -> InputStream)? = null

    private var autoJob: Job? = null

    private var defaultConflictAction: ConflictAction? = null

    init {
        viewModelScope.launch {
            _areas.value = repo.getAreas()
            _history.value = historyRepo.load()
        }
    }

    fun clearMessage() { _message.value = null }

    fun refreshAreas() {
        viewModelScope.launch { _areas.value = repo.getAreas() }
    }

    // ============================================================
    // СТАРТ ИМПОРТА
    // ============================================================

    fun importFromUri(uri: Uri, fileName: String) {
        _busy.value = true
        viewModelScope.launch {
            try {
                val openStream: () -> InputStream = {
                    getApplication<Application>().contentResolver
                        .openInputStream(uri)
                        ?: throw IllegalStateException("Не удалось открыть файл")
                }
                openStreamFactory = openStream

                val metas = withContext(Dispatchers.IO) {
                    XlsxReader.readMetadata(openStream)
                }
                if (metas.isEmpty()) {
                    _message.value = "В файле нет листов"
                    return@launch
                }

                val candidates = metas.filter { it.rowCount >= 5 }
                if (candidates.isEmpty()) {
                    _message.value = "Нет листов с данными"
                    return@launch
                }

                queueMetas = candidates
                queueIndex = 0
                importedCount = 0
                replacedCount = 0
                skippedCount = 0
                existingSkippedCount = 0
                generalListCount = 0
                errorCount = 0
                processedItems = mutableListOf()
                defaultConflictAction = null
                currentFileName = fileName.substringBeforeLast('.')
                currentFileNameWithExt = fileName
                currentTotalSheets = metas.size
                currentFallbackOrder = "Импорт от " + SimpleDateFormat(
                    "dd.MM.yyyy HH:mm", Locale.getDefault()
                ).format(Date())

                _queueState.value = QueueState(
                    totalSheets = candidates.size,
                    currentIndex = 0,
                    processedItems = emptyList()
                )

                processNextSheet()
            } catch (e: Exception) {
                _message.value = "Ошибка чтения: ${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    // ============================================================
    // ОЧЕРЕДЬ
    // ============================================================

    private suspend fun processNextSheet() {
        val openStream = openStreamFactory
        if (openStream == null) {
            finishQueue(cancelled = false)
            return
        }
        val settings = settingsRepo.load()

        while (queueIndex < queueMetas.size) {
            val meta = queueMetas[queueIndex]
            val sheet = withContext(Dispatchers.IO) {
                XlsxReader.readSheet(openStream, meta.path, meta.name)
            }
            if (sheet == null) { queueIndex++; continue }

            val analysis = withContext(Dispatchers.Default) {
                ExcelAnalyzer.analyzeSheet(sheet, settings)
            }
            if (analysis == null) { queueIndex++; continue }

            val context = withContext(Dispatchers.Default) {
                ExcelImporter.buildOrder(
                    analysis = analysis,
                    fileName = currentFileName,
                    totalSheets = currentTotalSheets,
                    settings = settings,
                    fallbackOrderNumber = currentFallbackOrder
                )
            }
            if (context.order.samples.isEmpty()) { queueIndex++; continue }

            currentAnalysis = analysis
            currentSheetMeta = meta

            val sheetIdx = queueMetas.indexOfFirst { it.name == meta.name }
                .takeIf { it >= 0 }?.plus(1) ?: 1
            val sampleCount = context.order.samples.size
            val isGeneralList = sampleCount > 200

            // Проверяем — есть ли уже такой наряд в базе?
            var existingConflict = false
            var existingSamples = 0
            val autoArea = context.order.areaName
            val autoOrder = context.order.orderNumber
            if (!autoArea.isNullOrBlank() && autoOrder.isNotBlank()) {
                val stats = repo.getExistingOrderStats(autoArea, autoOrder)
                if (stats != null && stats.total > 0) {
                    existingConflict = true
                    existingSamples = stats.total
                }
            }

            _preview.value = ImportPreview(
                fileName = currentFileName + ".xlsx",
                sheetName = meta.name,
                sheetPath = meta.path,
                sheetIndex = sheetIdx,
                totalSheets = currentTotalSheets,
                headerRowIndex = analysis.headerRowIndex,
                twoRowHeader = analysis.headerRowCount > 1,
                mapping = analysis.mapping,
                autoMapping = analysis.mapping,
                headers = analysis.headerText(),
                rows = analysis.rows,
                order = context.order,
                areaAutoDetected = context.areaAutoDetected,
                orderAutoDetected = context.orderAutoDetected,
                skippedBlanks = context.skippedBlanks,
                skippedEmpty = context.skippedEmpty,
                queuePosition = queueIndex + 1,
                queueTotal = queueMetas.size,
                importedCount = importedCount,
                skippedCount = skippedCount,
                existingSkippedCount = existingSkippedCount,
                isGeneralList = isGeneralList,
                sheetSampleCount = sampleCount,
                existingConflict = existingConflict,
                existingSamples = existingSamples
            )
            return
        }

        finishQueue(cancelled = false)
    }

    private fun finishQueue(cancelled: Boolean) {
        _preview.value = null
        _queueState.value = _queueState.value.copy(
            isFinished = true,
            isCancelled = cancelled,
            currentIndex = queueMetas.size,
            importedCount = importedCount,
            replacedCount = replacedCount,
            skippedCount = skippedCount,
            existingSkippedCount = existingSkippedCount,
            generalListCount = generalListCount,
            errorCount = errorCount,
            processedItems = processedItems.toList()
        )

        if (processedItems.isNotEmpty()) {
            val entry = ImportHistoryEntry(
                timestamp = System.currentTimeMillis(),
                fileName = currentFileNameWithExt,
                totalSheets = queueMetas.size,
                importedCount = importedCount,
                replacedCount = replacedCount,
                skippedCount = skippedCount + existingSkippedCount,
                generalListCount = generalListCount,
                errorCount = errorCount,
                items = processedItems.map { item ->
                    ImportHistoryItem(
                        sheetName = item.sheetName,
                        areaName = item.areaName,
                        orderNumber = item.orderNumber,
                        sampleCount = item.sampleCount,
                        status = item.status.name.lowercase(),
                        reason = item.reason
                    )
                }
            )
            historyRepo.add(entry)
            _history.value = historyRepo.load()
        }

        val msg = if (cancelled) {
            "Импорт прерван. Импортировано: $importedCount, заменено: $replacedCount, пропущено: ${skippedCount + existingSkippedCount}"
        } else {
            "Импорт завершён. Импортировано: $importedCount, заменено: $replacedCount, пропущено: ${skippedCount + existingSkippedCount}"
        }
        _message.value = msg
    }

    // ============================================================
    // ДЕЙСТВИЯ
    // ============================================================

    fun skipCurrent() {
        viewModelScope.launch {
            _busy.value = true
            try {
                val meta = currentSheetMeta
                val preview = _preview.value
                if (meta != null && preview != null) {
                    processedItems.add(
                        ProcessedItem(
                            sheetName = meta.name,
                            areaName = preview.order.areaName ?: "—",
                            orderNumber = preview.order.orderNumber,
                            sampleCount = preview.order.samples.size,
                            status = ProcessedStatus.SKIPPED,
                            reason = "Пропущен вручную"
                        )
                    )
                    skippedCount++
                }
                queueIndex++
                updateQueueCounters()
                _preview.value = null
                processNextSheet()
            } finally {
                _busy.value = false
            }
        }
    }

    fun cancelAll() {
        autoJob?.cancel()
        autoJob = null
        finishQueue(cancelled = true)
    }

    private fun updateQueueCounters() {
        _queueState.value = _queueState.value.copy(
            currentIndex = queueIndex,
            importedCount = importedCount,
            replacedCount = replacedCount,
            skippedCount = skippedCount,
            existingSkippedCount = existingSkippedCount,
            generalListCount = generalListCount,
            errorCount = errorCount,
            processedItems = processedItems.toList()
        )
    }

    // ============================================================
    // ПРОВЕРКА КОНФЛИКТА + ИМПОРТ
    // ============================================================

    private suspend fun startImportWithConflictCheck(
        areaName: String,
        orderNumber: String,
        remember: RememberChanges?
    ) {
        val p = _preview.value ?: return

        if (remember != null && !remember.isEmpty) {
            var settings = settingsRepo.load()
            remember.newHeaderKeywords.forEach { (word, role) ->
                settings = settings.addUserHeaderKeyword(role, word)
            }
            remember.newAreaPrefix?.let { (prefix, area) ->
                val current = settings.areaPrefixes[area].orEmpty()
                if (!current.contains(prefix)) {
                    val updated = settings.areaPrefixes.toMutableMap()
                    updated[area] = current + prefix
                    settings = settings.copy(areaPrefixes = updated)
                }
            }
            settingsRepo.save(settings)
        }

        val existingStats = repo.getExistingOrderStats(areaName, orderNumber)
        val hasConflict = existingStats != null && existingStats.total > 0

        if (!hasConflict) {
            performImport(areaName, orderNumber, ConflictAction.ADD)
            return
        }

        val defaultAction = defaultConflictAction
        if (defaultAction != null) {
            performImport(areaName, orderNumber, defaultAction)
            return
        }

        _pendingConflict.value = PendingConflict(
            areaName = areaName,
            orderNumber = orderNumber,
            existingSamples = existingStats!!.total,
            newSamples = p.order.samples.size
        )
    }

    fun resolveConflict(action: ConflictAction, rememberForAll: Boolean) {
        val conflict = _pendingConflict.value ?: return
        if (rememberForAll) {
            defaultConflictAction = action
        }
        _pendingConflict.value = null

        viewModelScope.launch {
            _busy.value = true
            try {
                performImport(conflict.areaName, conflict.orderNumber, action)
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun performImport(
        areaName: String,
        orderNumber: String,
        action: ConflictAction
    ) {
        val p = _preview.value ?: return
        val meta = currentSheetMeta
        val settings = settingsRepo.load()
        val existingStats = repo.getExistingOrderStats(areaName, orderNumber)
        val wasExisting = existingStats != null && existingStats.total > 0
        val existingTotal = existingStats?.total ?: 0

        when (action) {
            ConflictAction.SKIP -> {
                if (meta != null) {
                    processedItems.add(
                        ProcessedItem(
                            sheetName = meta.name,
                            areaName = areaName,
                            orderNumber = orderNumber,
                            sampleCount = p.order.samples.size,
                            status = ProcessedStatus.EXISTING_SKIPPED,
                            reason = "Наряд уже есть в базе ($existingTotal проб)"
                        )
                    )
                    existingSkippedCount++
                }
            }

            ConflictAction.ADD -> {
                val ok = try {
                    doImportToDb(p.order, areaName, orderNumber, settings)
                    true
                } catch (e: Exception) {
                    _message.value = "Ошибка импорта: ${e.message}"
                    false
                }
                if (meta != null) {
                    val reason = if (wasExisting)
                        "Добавлено к существующему наряду (было $existingTotal проб)"
                    else null
                    processedItems.add(
                        ProcessedItem(
                            sheetName = meta.name,
                            areaName = areaName,
                            orderNumber = orderNumber,
                            sampleCount = p.order.samples.size,
                            status = if (ok) ProcessedStatus.IMPORTED else ProcessedStatus.ERROR,
                            reason = reason
                        )
                    )
                    if (ok) importedCount++ else errorCount++
                }
            }

            ConflictAction.REPLACE -> {
                val areaId = repo.getAreaId(areaName)
                val orderId = areaId?.let { repo.getOrderId(it, orderNumber) }
                val ok = try {
                    if (orderId != null) {
                        repo.clearOrder(orderId)
                    }
                    doImportToDb(p.order, areaName, orderNumber, settings)
                    true
                } catch (e: Exception) {
                    _message.value = "Ошибка импорта: ${e.message}"
                    false
                }
                if (meta != null) {
                    val reason = if (wasExisting)
                        "Заменён старый наряд (было $existingTotal проб)"
                    else "Импортирован"
                    processedItems.add(
                        ProcessedItem(
                            sheetName = meta.name,
                            areaName = areaName,
                            orderNumber = orderNumber,
                            sampleCount = p.order.samples.size,
                            status = if (ok) ProcessedStatus.REPLACED else ProcessedStatus.ERROR,
                            reason = reason
                        )
                    )
                    if (ok) replacedCount++ else errorCount++
                }
            }
        }

        queueIndex++
        updateQueueCounters()
        _preview.value = null
        processNextSheet()
    }

    // ============================================================
    // ПУБЛИЧНЫЕ ВЫЗОВЫ
    // ============================================================

    fun confirmImport(areaName: String, orderNumber: String) {
        if (areaName.isBlank() || orderNumber.isBlank()) {
            _message.value = "Укажите участок и номер наряда"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                startImportWithConflictCheck(areaName, orderNumber, remember = null)
            } finally {
                _busy.value = false
            }
        }
    }

    fun rememberAndImport(
        areaName: String,
        orderNumber: String,
        changes: RememberChanges
    ) {
        if (areaName.isBlank() || orderNumber.isBlank()) {
            _message.value = "Укажите участок и номер наряда"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                startImportWithConflictCheck(areaName, orderNumber, remember = changes)
            } finally {
                _busy.value = false
            }
        }
    }

    // ============================================================
    // АВТО
    // ============================================================

    fun autoImportRest() {
        autoJob = viewModelScope.launch {
            _busy.value = true
            try {
                val openStream = openStreamFactory
                if (openStream == null) {
                    finishQueue(cancelled = true)
                    return@launch
                }
                val settings = settingsRepo.load()

                val current = _preview.value
                val currentMeta = currentSheetMeta
                if (current != null && currentMeta != null) {
                    processAutoSheet(current, currentMeta, settings, fromPreview = true)
                    _preview.value = null
                    queueIndex++
                    updateQueueCounters()
                }

                while (queueIndex < queueMetas.size) {
                    val meta = queueMetas[queueIndex]

                    val sheet = withContext(Dispatchers.IO) {
                        XlsxReader.readSheet(openStream, meta.path, meta.name)
                    }
                    if (sheet == null) { queueIndex++; updateQueueCounters(); continue }

                    val analysis = withContext(Dispatchers.Default) {
                        ExcelAnalyzer.analyzeSheet(sheet, settings)
                    }
                    if (analysis == null) { queueIndex++; updateQueueCounters(); continue }

                    val context = withContext(Dispatchers.Default) {
                        ExcelImporter.buildOrder(
                            analysis = analysis,
                            fileName = currentFileName,
                            totalSheets = currentTotalSheets,
                            settings = settings,
                            fallbackOrderNumber = currentFallbackOrder
                        )
                    }
                    if (context.order.samples.isEmpty()) {
                        queueIndex++
                        updateQueueCounters()
                        continue
                    }

                    val sampleCount = context.order.samples.size
                    val preview = ImportPreview(
                        fileName = currentFileName + ".xlsx",
                        sheetName = meta.name,
                        sheetPath = meta.path,
                        sheetIndex = queueIndex + 1,
                        totalSheets = currentTotalSheets,
                        headerRowIndex = analysis.headerRowIndex,
                        twoRowHeader = analysis.headerRowCount > 1,
                        mapping = analysis.mapping,
                        autoMapping = analysis.mapping,
                        headers = analysis.headerText(),
                        rows = analysis.rows,
                        order = context.order,
                        areaAutoDetected = context.areaAutoDetected,
                        orderAutoDetected = context.orderAutoDetected,
                        skippedBlanks = context.skippedBlanks,
                        skippedEmpty = context.skippedEmpty,
                        queuePosition = queueIndex + 1,
                        queueTotal = queueMetas.size,
                        importedCount = importedCount,
                        skippedCount = skippedCount,
                        existingSkippedCount = existingSkippedCount,
                        isGeneralList = sampleCount > 200,
                        sheetSampleCount = sampleCount,
                        existingConflict = false,
                        existingSamples = 0
                    )
                    processAutoSheet(preview, meta, settings, fromPreview = false)

                    queueIndex++
                    updateQueueCounters()
                }

                finishQueue(cancelled = false)
            } catch (e: Exception) {
                _message.value = "Ошибка авто-импорта: ${e.message}"
                finishQueue(cancelled = true)
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * Обработка одного листа в авто-режиме: применение дефолтного действия при конфликте.
     */
    private suspend fun processAutoSheet(
        preview: ImportPreview,
        meta: SheetMeta,
        settings: ImportSettings,
        fromPreview: Boolean
    ) {
        val area = preview.order.areaName ?: "Неизвестный"
        val order = preview.order.orderNumber
        val sampleCount = preview.order.samples.size

        if (sampleCount > 200) {
            processedItems.add(
                ProcessedItem(
                    sheetName = meta.name,
                    areaName = area,
                    orderNumber = order,
                    sampleCount = sampleCount,
                    status = ProcessedStatus.GENERAL_LIST,
                    reason = "Общий список (${sampleCount} проб)"
                )
            )
            generalListCount++
            return
        }

        val existingStats = repo.getExistingOrderStats(area, order)
        val conflict = existingStats != null && existingStats.total > 0
        val existingTotal = existingStats?.total ?: 0
        val action = if (conflict) {
            defaultConflictAction ?: ConflictAction.SKIP
        } else ConflictAction.ADD

        if (action == ConflictAction.SKIP && conflict) {
            processedItems.add(
                ProcessedItem(
                    sheetName = meta.name,
                    areaName = area,
                    orderNumber = order,
                    sampleCount = sampleCount,
                    status = ProcessedStatus.EXISTING_SKIPPED,
                    reason = "Наряд уже есть в базе ($existingTotal проб)"
                )
            )
            existingSkippedCount++
            return
        }

        val isReplace = action == ConflictAction.REPLACE && conflict
        val ok = try {
            if (isReplace) {
                val areaId = repo.getAreaId(area)
                val orderId = areaId?.let { repo.getOrderId(it, order) }
                if (orderId != null) repo.clearOrder(orderId)
            }
            doImportToDb(preview.order, area, order, settings)
            true
        } catch (e: Exception) { false }

        val status = if (!ok) ProcessedStatus.ERROR
        else if (isReplace) ProcessedStatus.REPLACED
        else ProcessedStatus.IMPORTED

        val reason = when {
            !ok -> "Ошибка записи"
            isReplace -> "Заменён старый наряд (было $existingTotal проб)"
            conflict -> "Добавлено к существующему наряду (было $existingTotal проб)"
            else -> null
        }

        processedItems.add(
            ProcessedItem(
                sheetName = meta.name,
                areaName = area,
                orderNumber = order,
                sampleCount = sampleCount,
                status = status,
                reason = reason
            )
        )
        when (status) {
            ProcessedStatus.IMPORTED -> importedCount++
            ProcessedStatus.REPLACED -> replacedCount++
            ProcessedStatus.ERROR -> errorCount++
            else -> {}
        }
    }

    // ============================================================
    // ЗАПИСЬ
    // ============================================================

    private suspend fun doImportToDb(
        order: ParsedOrder,
        areaName: String,
        orderNumber: String,
        settings: ImportSettings
    ) {
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
        for (s in order.samples) {
            val entity = SampleEntity(
                orderId = orderId,
                serialNumber = s.serialNumber,
                sampleNumber = s.sampleNumber,
                wellNumber = s.wellNumber,
                workings = s.workings,
                materialDesc = s.materialDesc,
                intervalFrom = s.intervalFrom,
                intervalTo = s.intervalTo,
                weight = s.weight,
                sampleType = s.sampleType,
                status = s.status,
                found = false
            )
            repo.addSample(entity)
            if (s.wellNumber.isNotEmpty()) wellsSet.add(s.wellNumber)
        }

        for (w in wellsSet) {
            try { repo.addWell(orderId, w) } catch (_: Exception) {}
        }
    }

    // ============================================================
    // СЛУЖЕБНЫЕ
    // ============================================================

    fun updateMapping(newMapping: Map<String, Int?>) {
        val analysis = currentAnalysis ?: return
        val preview = _preview.value ?: return

        viewModelScope.launch {
            try {
                val settings = settingsRepo.load()
                val newAnalysis = analysis.copy(mapping = newMapping)
                val context = withContext(Dispatchers.Default) {
                    ExcelImporter.buildOrder(
                        analysis = newAnalysis,
                        fileName = currentFileName,
                        totalSheets = currentTotalSheets,
                        settings = settings,
                        fallbackOrderNumber = currentFallbackOrder
                    )
                }

                // Пересчитываем конфликт с новым areaName
                var conflict = false
                var conflictSamples = 0
                val autoArea = context.order.areaName
                val autoOrder = context.order.orderNumber
                if (!autoArea.isNullOrBlank() && autoOrder.isNotBlank()) {
                    val stats = repo.getExistingOrderStats(autoArea, autoOrder)
                    if (stats != null && stats.total > 0) {
                        conflict = true
                        conflictSamples = stats.total
                    }
                }

                _preview.value = preview.copy(
                    mapping = newMapping,
                    order = context.order,
                    areaAutoDetected = context.areaAutoDetected,
                    orderAutoDetected = context.orderAutoDetected,
                    skippedBlanks = context.skippedBlanks,
                    skippedEmpty = context.skippedEmpty,
                    sheetSampleCount = context.order.samples.size,
                    isGeneralList = context.order.samples.size > 200,
                    existingConflict = conflict,
                    existingSamples = conflictSamples
                )
            } catch (e: Exception) {
                _message.value = "Ошибка применения маппинга: ${e.message}"
            }
        }
    }

    fun computeRememberChanges(enteredAreaName: String): RememberChanges {
        val p = _preview.value ?: return RememberChanges()
        val settings = settingsRepo.load()

        val newHeaderKeywords = mutableMapOf<String, String>()
        for ((role, idx) in p.mapping) {
            val autoIdx = p.autoMapping[role]
            if (idx == null) continue
            if (idx == autoIdx) continue
            val headerRaw = p.headers.getOrNull(idx) ?: continue
            val word = normalizeHeaderWord(headerRaw)
            if (word.isEmpty()) continue
            val alreadyKnown = settings.effectiveHeaderKeywords(role).contains(word)
            if (!alreadyKnown) {
                newHeaderKeywords[word] = role
            }
        }

        var newAreaPrefix: Pair<String, String>? = null
        if (!p.areaAutoDetected && enteredAreaName.isNotBlank()) {
            val firstWell = p.order.samples.firstOrNull { it.wellNumber.isNotBlank() }?.wellNumber
            val prefix = firstWell?.let { AreaResolver.extractPrefix(it) }
            if (prefix != null) {
                val existing = settings.areaPrefixes[enteredAreaName].orEmpty()
                if (!existing.contains(prefix)) {
                    newAreaPrefix = prefix to enteredAreaName
                }
            }
        }

        return RememberChanges(
            newHeaderKeywords = newHeaderKeywords,
            newAreaPrefix = newAreaPrefix
        )
    }

    fun resetQueueState() {
        if (_queueState.value.isFinished) {
            _queueState.value = QueueState()
        }
    }

    fun reloadHistory() {
        _history.value = historyRepo.load()
    }

    fun clearHistory() {
        historyRepo.clear()
        _history.value = emptyList()
    }
}