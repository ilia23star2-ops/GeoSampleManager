package com.example.geosamplemanager.ui.screens

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geosamplemanager.GeoSampleApp
import com.example.geosamplemanager.data.entity.SampleImageEntity
import com.example.geosamplemanager.data.entity.SampleNoteEntity
import com.example.geosamplemanager.data.settings.ImportSettings
import com.example.geosamplemanager.data.util.PhotoStorage
import com.example.geosamplemanager.data.voice.VoiceCommand
import com.example.geosamplemanager.data.voice.VoiceCommandParser
import com.example.geosamplemanager.data.voice.VoiceExecResult
import com.example.geosamplemanager.data.voice.VoiceNumberParser
import com.example.geosamplemanager.data.voice.VoicePrefixResolver
import com.example.geosamplemanager.data.voice.VoiceSearch
import com.example.geosamplemanager.data.voice.VoiceSearchRepository
import com.example.geosamplemanager.data.voice.VoiceSearchResult
import com.example.geosamplemanager.data.voice.VoiceSession
import com.example.geosamplemanager.data.voice.VoiceStatus
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

class ReconciliationViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as GeoSampleApp).repository
    private val settingsRepo = (application as GeoSampleApp).settingsRepository
    private val voiceSettingsRepo = (application as GeoSampleApp).voiceSettingsRepository

    val state = ReconciliationState(emptyList())
    val voiceSession = VoiceSession()

    private val voiceParser = VoiceNumberParser()
    private val commandParser = VoiceCommandParser()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    private val loadedOrderIds = mutableSetOf<Long>()
    private var orderInfoById: Map<Long, OrderInfo> = emptyMap()
    private var orderInfoByTitle: Map<String, OrderInfo> = emptyMap()

    private var searchJob: Job? = null

    companion object {
        private const val TAG = "ReconciliationVM"
        private const val SEARCH_DEBOUNCE_MS = 500L
        private const val MAX_SEARCH_ORDERS = 20
        private const val MAX_QUERY_TOKENS = 5
    }

    init {
        subscribeToAreasAndOrders()
        checkOnboarding()
    }

    private fun checkOnboarding() {
        viewModelScope.launch {
            try {
                val vs = voiceSettingsRepo.load()
                if (vs.showOnboarding) {
                    withContext(Dispatchers.Main) {
                        state.voiceOnboardingVisible = true
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun dismissOnboarding() {
        state.voiceOnboardingVisible = false
        viewModelScope.launch {
            try {
                val vs = voiceSettingsRepo.load()
                voiceSettingsRepo.save(vs.copy(showOnboarding = false))
            } catch (_: Exception) {}
        }
    }

    private fun subscribeToAreasAndOrders() {
        viewModelScope.launch {
            try {
                combine(
                    repo.getAreasFlow(),
                    repo.getAllOrdersFlow()
                ) { areas, orders ->
                    val result = mutableListOf<OrderInfo>()
                    for (order in orders) {
                        val area = areas.firstOrNull { it.id == order.areaId } ?: continue
                        result.add(
                            OrderInfo(
                                areaId = area.id,
                                orderId = order.id,
                                areaTitle = area.areaName,
                                orderTitle = "Наряд №${order.orderNumber}"
                            )
                        )
                    }
                    val areaNames = areas.map { it.areaName }.distinct().sorted()
                    areaNames to result
                }.collect { (areaNames, orderInfos) ->
                    withContext(Dispatchers.Main) {
                        orderInfoById = orderInfos.associateBy { it.orderId }
                        orderInfoByTitle = orderInfos.associateBy { it.orderTitle }
                        state.allOrderTitles = orderInfos
                        state.allAreaNames = areaNames
                    }
                }
            } catch (e: Exception) {
                _message.value = "Ошибка загрузки: ${e.message}"
            }
        }
    }

    suspend fun ensureOrderSamplesLoaded(orderId: Long) {
        if (orderId in loadedOrderIds) return
        val info = orderInfoById[orderId] ?: return
        try {
            val group = withContext(Dispatchers.IO) {
                val samples = repo.getSamplesForOrderList(orderId)
                if (samples.isEmpty()) return@withContext null
                val order = repo.getAllOrders().firstOrNull { it.id == orderId }
                    ?: return@withContext null
                val area = repo.getAreas().firstOrNull { it.id == order.areaId }
                buildSampleGroup(order, area, samples)
            }
            if (group != null) {
                withContext(Dispatchers.Main) {
                    state.addGroup(group)
                    loadedOrderIds.add(orderId)
                }
            } else {
                loadedOrderIds.add(orderId)
            }
        } catch (e: Exception) {
            _message.value = "Ошибка загрузки наряда: ${e.message}"
        }
    }

    private suspend fun loadGroupsForQuery(query: String) {
        try {
            val allIds = withContext(Dispatchers.IO) { repo.findOrderIdsByQuery(query) }
            if (allIds.isEmpty()) return
            val newIds = allIds.filter { it !in loadedOrderIds }
            val toLoad = newIds.take(MAX_SEARCH_ORDERS)
            toLoad.forEach { ensureOrderSamplesLoaded(it) }
        } catch (e: Exception) {
            _message.value = "Ошибка поиска: ${e.message}"
        }
    }

    fun setSelectedArea(area: String?) {
        state.selectedArea = area
        state.selectedOrder = null
        refreshMultiQueryIfNeeded()
    }

    fun setSelectedOrder(orderTitle: String?) {
        state.selectedOrder = orderTitle
        val info = orderTitle?.let { orderInfoByTitle[it] }
        if (info != null) {
            viewModelScope.launch { ensureOrderSamplesLoaded(info.orderId) }
        }
        refreshMultiQueryIfNeeded()
    }

    private fun refreshMultiQueryIfNeeded() {
        if (!state.isMultiQuery) return
        val tokens = state.queryTokens
        searchJob?.cancel()
        searchJob = viewModelScope.launch { buildMultiQueryGroups(tokens) }
    }

    fun setQuery(query: String) {
        state.query = query
        searchJob?.cancel()
        if (query.isBlank()) {
            state.queryTokens = emptyList()
            state.clearQueryGroups()
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val tokens = query.trim().split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .take(MAX_QUERY_TOKENS)
            state.queryTokens = tokens
            when (tokens.size) {
                0 -> state.clearQueryGroups()
                1 -> {
                    state.clearQueryGroups()
                    loadGroupsForQuery(tokens[0])
                }
                else -> buildMultiQueryGroups(tokens)
            }
        }
    }

    private suspend fun buildMultiQueryGroups(tokens: List<String>) {
        try {
            val settings = withContext(Dispatchers.IO) { settingsRepo.load() }
            val queryGroups = mutableListOf<QueryGroup>()

            tokens.forEachIndexed { idx, token ->
                val (prefix, orderIds) = withContext(Dispatchers.IO) {
                    resolveTokenToOrderIds(token, settings)
                }
                orderIds.forEach { ensureOrderSamplesLoaded(it) }

                val variants = orderIds.mapNotNull { orderId ->
                    val info = orderInfoById[orderId] ?: return@mapNotNull null
                    val group = state.groupById(orderId.toString()) ?: return@mapNotNull null
                    val found = group.rows.count { it.found }
                    QueryVariant(
                        areaTitle = info.areaTitle,
                        orderTitle = info.orderTitle,
                        groupId = group.id,
                        foundCount = found,
                        totalCount = group.rows.size
                    )
                }

                val selectedArea = state.selectedArea
                val isForeign = selectedArea != null && variants.isNotEmpty() &&
                        variants.none { it.areaTitle == selectedArea }

                queryGroups.add(
                    QueryGroup(
                        id = "q${idx + 1}",
                        query = token,
                        prefix = prefix,
                        variants = variants,
                        isForeignArea = isForeign
                    )
                )
            }

            withContext(Dispatchers.Main) { state.setQueryGroups(queryGroups) }
        } catch (e: Exception) {
            _message.value = "Ошибка множественного поиска: ${e.message}"
        }
    }

    private suspend fun resolveTokenToOrderIds(
        token: String,
        settings: ImportSettings
    ): Pair<String?, List<Long>> {
        val prefix = extractLatinPrefix(token)
        val number = if (prefix != null) token.removePrefix(prefix).trim() else token
        val query = number.ifEmpty { token }
        val allIds = repo.findOrderIdsByQuery(query)
        if (prefix == null) return null to allIds

        val matchingAreas = settings.areaPrefixes
            .filterValues { prefixes -> prefixes.any { it.equals(prefix, ignoreCase = true) } }
            .keys
        val filtered = allIds.filter { orderId ->
            val info = orderInfoById[orderId] ?: return@filter false
            info.areaTitle in matchingAreas
        }
        return prefix to filtered
    }

    private fun extractLatinPrefix(token: String): String? {
        val letters = token.takeWhile { it.isLetter() && it.code < 128 }
        return letters.ifEmpty { null }?.uppercase()
    }

    // ================================================================
    // ГОЛОСОВОЙ ПОМОЩНИК
    // ================================================================

    fun setVoiceStatus(status: VoiceStatus) {
        state.voiceStatus = status
    }

    suspend fun voiceExecute(cmd: VoiceCommand): VoiceExecResult {
        Log.i(
            TAG,
            "voiceExecute: $cmd " +
                    "(awaitingWeight=${voiceSession.awaitingWeight}, " +
                    "awaitingContinue=${voiceSession.awaitingContinue})"
        )

        // ---- Ждём «продолжить» после «Найден в нескольких нарядах»? ----
        // Разрешаем только: продолжить / стоп / пауза.
        if (voiceSession.awaitingContinue) {
            return when (cmd) {
                VoiceCommand.Resume -> {
                    voiceSession.awaitingContinue = false
                    VoiceExecResult.Message("Продолжаю")
                }
                VoiceCommand.Stop -> {
                    voiceSession.awaitingContinue = false
                    VoiceExecResult.Stopped
                }
                VoiceCommand.Pause -> {
                    voiceSession.awaitingContinue = false
                    VoiceExecResult.Message("Пауза")
                }
                else -> VoiceExecResult.Message(
                    "Скажите «продолжить» или «стоп»."
                )
            }
        }

        // ---- Ждём вес? ----
        if (voiceSession.awaitingWeight) {
            // Пользователь сказал просто «два с половиной» — это Search,
            // но на самом деле — вес.
            if (cmd is VoiceCommand.Search) {
                val weight = commandParser.parseWeightAnswer(cmd.query)
                if (weight != null && weight > 0) {
                    voiceSession.awaitingWeight = false
                    return voiceSetWeight(weight)
                }
                return VoiceExecResult.Message("Не понял вес. Повторите.")
            }
            // Отмена — сбрасываем ожидание и обрабатываем команду.
            if (cmd is VoiceCommand.Undo || cmd is VoiceCommand.Stop) {
                voiceSession.awaitingWeight = false
            }
            // SetWeight идёт сам — не мешаем.
        }

        return when (cmd) {
            is VoiceCommand.Search -> voiceSearch(cmd.query)
            is VoiceCommand.MarkOrdinal -> voiceMarkOrdinal(cmd.ordinal)
            is VoiceCommand.SetWeight -> voiceSetWeight(cmd.value)
            is VoiceCommand.ClearOrdinal -> voiceClearOrdinal(cmd.ordinal)
            VoiceCommand.ClearLast -> voiceClearLast()
            VoiceCommand.ClearAll -> voiceClearAll()
            VoiceCommand.Unpostpone -> voiceUnpostpone()
            VoiceCommand.Next -> voiceNext()
            VoiceCommand.Undo -> { undo(); VoiceExecResult.Undone }
            VoiceCommand.Redo -> { redo(); VoiceExecResult.Redone }
            VoiceCommand.Stop -> VoiceExecResult.Stopped
            VoiceCommand.Pause -> { voiceSession.isPaused = true; VoiceExecResult.Message("Пауза") }
            VoiceCommand.Resume -> { voiceSession.isPaused = false; VoiceExecResult.Message("Продолжаю") }
            VoiceCommand.HowManyLeft -> voiceHowManyLeft()
            VoiceCommand.ShowPostponed -> voiceShowFilter(ResultFilter.POSTPONED, "Отложенные")
            VoiceCommand.ShowFound -> voiceShowFilter(ResultFilter.FOUND, "Найденные")
            VoiceCommand.Help -> VoiceExecResult.Message("Открываю справку")
            is VoiceCommand.Sort -> voiceSort(cmd.queries)
            VoiceCommand.Unknown -> VoiceExecResult.Message("Не понял команду")
        }
    }

    private suspend fun voiceSearch(query: String): VoiceExecResult {
        Log.i(TAG, "voiceSearch: query=«$query»")
        return try {
            val settings = withContext(Dispatchers.IO) { settingsRepo.load() }
            val voiceSettings = withContext(Dispatchers.IO) { voiceSettingsRepo.load() }
            val allPrefixes = settings.areaPrefixes.values.flatten().toSet()
            val prefixResolver = VoicePrefixResolver(
                allPrefixes, voiceSettings.customPrefixPronunciations
            )
            val extraction = prefixResolver.extract(query)
            val numberText = extraction.remainder.ifEmpty { query }

            val parsed = voiceParser.parse(numberText)
            val candidates = parsed.candidates.map { c ->
                val clean = c.replace("|", "")
                if (extraction.prefix != null) extraction.prefix + clean else clean
            }
            Log.i(TAG, "voiceSearch: candidates=$candidates")

            val source = VoiceSearchRepository(getApplication())
            val search = VoiceSearch(source)
            val result = search.search(candidates)
            Log.i(TAG, "voiceSearch: result=$result")

            when (result) {
                VoiceSearchResult.NotFound -> VoiceExecResult.NotFound
                is VoiceSearchResult.FoundOne -> {
                    val hit = result.hit
                    // Уровни:
                    //   1 — sample == (ПРОБА)
                    //   2 — well == (СКВАЖИНА)
                    //   3 — well.endsWith (СКВАЖИНА)
                    //   4 — sample.endsWith (ПРОБА)
                    //   5, 6 — по обстоятельствам
                    val isSample = when (result.level) {
                        1, 4 -> true
                        2, 3 -> false
                        else -> hit.sampleNumber != hit.wellNumber
                    }

                    voiceSession.currentOrderId = hit.orderId
                    voiceSession.currentOrderTitle = "Наряд №${hit.orderNumber}"
                    voiceSession.currentAreaTitle = hit.areaTitle
                    voiceSession.currentWellNumber = hit.wellNumber
                    voiceSession.isAutoMode = true
                    voiceSession.awaitingWeight = false
                    voiceSession.awaitingContinue = false

                    val displayQuery: String
                    val foundSamples: Int
                    val totalSamples: Int

                    if (isSample) {
                        displayQuery = hit.sampleNumber
                        ensureOrderSamplesLoaded(hit.orderId)
                        val group = state.groupById(hit.orderId.toString())
                        val sampleRow = group?.rows?.firstOrNull {
                            it.sampleNumber == hit.sampleNumber
                        }
                        totalSamples = 1
                        foundSamples = if (sampleRow?.found == true) 1 else 0
                    } else {
                        displayQuery = hit.wellNumber
                        ensureOrderSamplesLoaded(hit.orderId)
                        val group = state.groupById(hit.orderId.toString())
                        val wellRows = group?.rows?.filter { it.wellNumber == hit.wellNumber }
                            ?: emptyList()
                        totalSamples = wellRows.size
                        foundSamples = wellRows.count { it.found }
                    }

                    voiceSession.currentQuery = displayQuery

                    withContext(Dispatchers.Main) {
                        state.selectedArea = hit.areaTitle
                        state.selectedOrder = voiceSession.currentOrderTitle
                        state.query = displayQuery
                    }

                    VoiceExecResult.FoundOne(
                        query = displayQuery,
                        orderTitle = voiceSession.currentOrderTitle ?: "",
                        wellNumber = hit.wellNumber,
                        totalSamples = totalSamples,
                        foundSamples = foundSamples,
                        isSample = isSample
                    )
                }
                is VoiceSearchResult.FoundMany -> {
                    // FIX 5.8.8h: неоднозначный ответ — ждём «продолжить/стоп».
                    voiceSession.awaitingContinue = true
                    voiceSession.isAutoMode = false
                    VoiceExecResult.FoundMany(result.candidate, result.hits.size)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "voiceSearch: упал", e)
            VoiceExecResult.Message("Ошибка поиска: ${e.message}")
        }
    }

    private fun voiceMarkOrdinal(ordinal: Int): VoiceExecResult {
        val orderId = voiceSession.currentOrderId
            ?: return VoiceExecResult.Message("Сначала найдите скважину")
        val wellNumber = voiceSession.currentWellNumber
            ?: return VoiceExecResult.Message("Сначала найдите скважину")
        val group = state.groupById(orderId.toString())
            ?: return VoiceExecResult.Message("Наряд не загружен")
        val row = group.rows.firstOrNull {
            it.wellNumber == wellNumber && it.numberInWell == ordinal
        } ?: return VoiceExecResult.Message("Проба №$ordinal не найдена")
        if (row.found) return VoiceExecResult.Message("Проба ${row.sampleNumber} уже отмечена")

        setFound(row.id, true)
        voiceSession.lastMarkedRowId = row.id
        voiceSession.lastMarkedSampleNumber = row.sampleNumber

        val needsWeight = (row.isBlank && row.weight == null) ||
                (row.weightControl && row.controlWeight == null)
        if (needsWeight) voiceSession.awaitingWeight = true

        return VoiceExecResult.Marked(
            sampleNumber = row.sampleNumber,
            ordinal = ordinal,
            isWeightControl = row.weightControl,
            needsWeight = needsWeight
        )
    }

    private fun voiceSetWeight(value: Double): VoiceExecResult {
        val rowId = voiceSession.lastMarkedRowId
            ?: return VoiceExecResult.Message("Нет активной пробы")
        val row = state.rowById(rowId)
            ?: return VoiceExecResult.Message("Проба потеряна")
        setWeight(rowId, value)
        return VoiceExecResult.WeightSet(row.sampleNumber, value)
    }

    private fun voiceClearOrdinal(ordinal: Int): VoiceExecResult {
        val orderId = voiceSession.currentOrderId
            ?: return VoiceExecResult.Message("Сначала найдите скважину")
        val wellNumber = voiceSession.currentWellNumber
            ?: return VoiceExecResult.Message("Сначала найдите скважину")
        val group = state.groupById(orderId.toString())
            ?: return VoiceExecResult.Message("Наряд не загружен")
        val row = group.rows.firstOrNull {
            it.wellNumber == wellNumber && it.numberInWell == ordinal
        } ?: return VoiceExecResult.Message("Проба №$ordinal не найдена")
        if (!row.found) return VoiceExecResult.Message("Проба ${row.sampleNumber} не отмечена")
        setFound(row.id, false)
        return VoiceExecResult.Unmarked(row.sampleNumber)
    }

    private fun voiceClearLast(): VoiceExecResult {
        val rowId = voiceSession.lastMarkedRowId
            ?: return VoiceExecResult.Message("Нет активной пробы")
        val row = state.rowById(rowId)
            ?: return VoiceExecResult.Message("Проба потеряна")
        if (!row.found) return VoiceExecResult.Message("Проба не отмечена")
        setFound(rowId, false)
        voiceSession.lastMarkedRowId = null
        voiceSession.lastMarkedSampleNumber = null
        return VoiceExecResult.Unmarked(row.sampleNumber)
    }

    private fun voiceClearAll(): VoiceExecResult {
        val orderId = voiceSession.currentOrderId
            ?: return VoiceExecResult.Message("Сначала найдите скважину")
        val wellNumber = voiceSession.currentWellNumber
            ?: return VoiceExecResult.Message("Сначала найдите скважину")
        val group = state.groupById(orderId.toString())
            ?: return VoiceExecResult.Message("Наряд не загружен")
        val rows = group.rows.filter { it.wellNumber == wellNumber }
        var count = 0
        rows.forEach { row ->
            if (row.found) { setFound(row.id, false); count++ }
        }
        return VoiceExecResult.Message("Снято отметок: $count")
    }

    private fun voiceUnpostpone(): VoiceExecResult {
        val rowId = voiceSession.lastMarkedRowId
            ?: return VoiceExecResult.Message("Нет активной пробы")
        val row = state.rowById(rowId)
            ?: return VoiceExecResult.Message("Проба потеряна")
        if (!row.postponed) return VoiceExecResult.Message("Проба не отложена")
        setPostponed(rowId, false)
        return VoiceExecResult.Message("Отложенность снята")
    }

    private fun voiceNext(): VoiceExecResult {
        voiceSession.advanceToNext()
        state.query = ""
        state.selectedArea = null
        state.selectedOrder = null
        return VoiceExecResult.Next
    }

    private fun voiceHowManyLeft(): VoiceExecResult {
        val orderId = voiceSession.currentOrderId
            ?: return VoiceExecResult.Message("Сначала найдите скважину")
        val wellNumber = voiceSession.currentWellNumber
            ?: return VoiceExecResult.Message("Сначала найдите скважину")
        val group = state.groupById(orderId.toString())
            ?: return VoiceExecResult.Message("Наряд не загружен")
        val rows = group.rows.filter { it.wellNumber == wellNumber }
        val left = rows.count { !it.found }
        return VoiceExecResult.Message("Осталось отметить: $left")
    }

    private fun voiceShowFilter(filter: ResultFilter, label: String): VoiceExecResult {
        state.activeFilters = state.activeFilters + filter
        return VoiceExecResult.Message("Фильтр: $label")
    }

    private fun voiceSort(queries: List<String>): VoiceExecResult {
        voiceSession.isAutoMode = false
        voiceSession.awaitingContinue = false
        val joined = queries.joinToString(" ")
        setQuery(joined)
        return VoiceExecResult.Message("Сортировка: $joined")
    }

    // ================================================================
    // ДЕЙСТВИЯ НАД ПРОБАМИ
    // ================================================================

    private fun rowById(rowId: String): SampleRow? = state.rowById(rowId)

    fun toggleFound(rowId: String) {
        state.toggleFound(rowId)
        val id = rowId.toLongOrNull() ?: return
        val found = rowById(rowId)?.found ?: return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { repo.setFound(id, found) } }
            catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setFound(rowId: String, value: Boolean) {
        state.setFound(rowId, value)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { repo.setFound(id, value) } }
            catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setControlWeightAndFound(rowId: String, weight: Double) {
        state.setControlWeightAndFound(rowId, weight)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repo.setControlWeight(id, weight)
                    repo.setFound(id, true)
                }
            } catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setControlWeight(rowId: String, weight: Double) {
        state.setControlWeight(rowId, weight)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { repo.setControlWeight(id, weight) } }
            catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setBlankWeightAndMarkFound(rowId: String, weight: Double) {
        state.setBlankWeightAndMarkFound(rowId, weight)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repo.setWeight(id, weight)
                    repo.setFound(id, true)
                }
            } catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setWeight(rowId: String, weight: Double) {
        state.setWeight(rowId, weight)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { repo.setWeight(id, weight) } }
            catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setPostponed(rowId: String, value: Boolean) {
        state.setPostponed(rowId, value)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { repo.setPostponed(id, value) } }
            catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun toggleWeightControl(rowId: String): Boolean {
        val ok = state.toggleWeightControl(rowId)
        if (ok) {
            val id = rowId.toLongOrNull() ?: return ok
            val flag = rowById(rowId)?.weightControl ?: return ok
            viewModelScope.launch {
                try { withContext(Dispatchers.IO) { repo.setWeightControl(id, flag) } }
                catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
            }
        }
        return ok
    }

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

    fun deleteRow(rowId: String, recalc: Boolean) {
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.deleteSampleWithRenumber(id, recalc) }
                state.deleteRow(rowId, recalc)
            } catch (e: Exception) {
                _message.value = "Ошибка удаления: ${e.message}"
            }
        }
    }

    fun undo() {
        state.undo()
        persistAll()
    }

    fun redo() {
        state.redo()
        persistAll()
    }

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
    // ЗАМЕТКИ И ФОТО
    // ================================================================

    suspend fun loadNoteWithPhotos(
        sampleId: Long
    ): Pair<SampleNoteEntity?, List<SampleImageEntity>> {
        return try {
            withContext(Dispatchers.IO) { repo.getNoteWithPhotos(sampleId) }
        } catch (e: Exception) {
            _message.value = "Ошибка загрузки заметки: ${e.message}"
            null to emptyList()
        }
    }

    suspend fun saveNoteText(sampleId: Long, text: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val trimmed = text.trim()
                if (trimmed.isEmpty()) {
                    repo.deleteNote(sampleId)
                } else {
                    val existing = repo.getNote(sampleId)
                    val note = SampleNoteEntity(
                        id = existing?.id ?: 0,
                        sampleId = sampleId,
                        noteText = trimmed,
                        createdDate = existing?.createdDate ?: System.currentTimeMillis()
                    )
                    repo.upsertNote(note)
                }
                repo.syncHasNoteAndPhoto(sampleId)
                val s = repo.getSampleById(sampleId)
                if (s != null) {
                    withContext(Dispatchers.Main) {
                        state.updateRowFlags(sampleId.toString(), s.hasNote, s.hasPhoto)
                    }
                }
            }
            true
        } catch (e: Exception) {
            _message.value = "Ошибка сохранения заметки: ${e.message}"
            false
        }
    }

    suspend fun addPhoto(sampleId: Long, sourceUri: Uri): Boolean {
        return try {
            val ctx = getApplication<Application>()
            val path = withContext(Dispatchers.IO) {
                PhotoStorage.compressAndSave(ctx, sourceUri)
            }
            if (path == null) {
                _message.value = "Не удалось обработать фото"
                return false
            }
            withContext(Dispatchers.IO) {
                repo.addPhoto(sampleId, path)
                refreshSampleFlagsInternal(sampleId)
            }
            true
        } catch (e: Exception) {
            _message.value = "Ошибка добавления фото: ${e.message}"
            false
        }
    }

    suspend fun addPhotoFromFile(sampleId: Long, tempFile: File): Boolean {
        return try {
            val ctx = getApplication<Application>()
            val path = withContext(Dispatchers.IO) {
                PhotoStorage.compressAndSaveFromFile(ctx, tempFile)
            }
            if (path == null) {
                _message.value = "Не удалось обработать фото"
                return false
            }
            withContext(Dispatchers.IO) {
                repo.addPhoto(sampleId, path)
                refreshSampleFlagsInternal(sampleId)
            }
            true
        } catch (e: Exception) {
            _message.value = "Ошибка добавления фото: ${e.message}"
            false
        }
    }

    suspend fun deletePhoto(imageId: Long, sampleId: Long): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val ok = repo.deletePhoto(imageId, sampleId)
                if (ok) refreshSampleFlagsInternal(sampleId)
                ok
            }
        } catch (e: Exception) {
            _message.value = "Ошибка удаления фото: ${e.message}"
            false
        }
    }

    private suspend fun refreshSampleFlagsInternal(sampleId: Long) {
        val s = repo.getSampleById(sampleId) ?: return
        withContext(Dispatchers.Main) {
            state.updateRowFlags(sampleId.toString(), s.hasNote, s.hasPhoto)
        }
    }

    // ================================================================
    // СОХРАНЕНИЕ
    // ================================================================

    private fun persistGroup(groupId: String) {
        val group = state.groups.firstOrNull { it.id == groupId } ?: return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { repo.saveRows(group.rows) } }
            catch (e: Exception) { _message.value = "Ошибка сохранения: ${e.message}" }
        }
    }

    private fun persistOrder(orderTitle: String) {
        val groups = state.groups.filter { it.orderTitle == orderTitle }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    groups.forEach { repo.saveRows(it.rows) }
                }
            } catch (e: Exception) { _message.value = "Ошибка сохранения: ${e.message}" }
        }
    }

    private fun persistAll() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    state.groups.forEach { repo.saveRows(it.rows) }
                }
            } catch (e: Exception) { _message.value = "Ошибка сохранения: ${e.message}" }
        }
    }
}