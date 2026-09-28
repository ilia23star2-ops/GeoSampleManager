package com.example.geosamplemanager.ui.screens

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geosamplemanager.GeoSampleApp
import com.example.geosamplemanager.data.entity.SampleImageEntity
import com.example.geosamplemanager.data.entity.SampleNoteEntity
import com.example.geosamplemanager.data.reconciliation.MarkDecision
import com.example.geosamplemanager.data.reconciliation.WeightValidation
import com.example.geosamplemanager.data.reconciliation.analyzeMark
import com.example.geosamplemanager.data.reconciliation.validateWeight
import com.example.geosamplemanager.data.settings.ImportSettings
import com.example.geosamplemanager.data.util.PhotoStorage
import com.example.geosamplemanager.data.voice.AnswerReason
import com.example.geosamplemanager.data.voice.ConfirmedAction
import com.example.geosamplemanager.data.voice.MarkDecisionVoiceRenderer
import com.example.geosamplemanager.data.voice.PendingMarkChoiceType
import com.example.geosamplemanager.data.voice.PendingMarkIntentType
import com.example.geosamplemanager.data.voice.PinnedScope
import com.example.geosamplemanager.data.voice.UnifiedMatchKind
import com.example.geosamplemanager.data.voice.UnifiedSearch
import com.example.geosamplemanager.data.voice.UnifiedSearchResult
import com.example.geosamplemanager.data.voice.VoiceCommand
import com.example.geosamplemanager.data.voice.VoiceCommandParser
import com.example.geosamplemanager.data.voice.VoiceExecResult
import com.example.geosamplemanager.data.voice.VoiceMarkOrdinalFallback
import com.example.geosamplemanager.data.voice.VoiceNumberParser
import com.example.geosamplemanager.data.voice.VoiceOrdinals
import com.example.geosamplemanager.data.voice.VoicePrefixResolver
import com.example.geosamplemanager.data.voice.VoiceSearchRepository
import com.example.geosamplemanager.data.voice.VoiceSession
import com.example.geosamplemanager.data.voice.VoiceSessionMode
import com.example.geosamplemanager.data.voice.VoiceSpeaker
import com.example.geosamplemanager.data.voice.VoiceStatus
import com.example.geosamplemanager.data.voice.WeightQueueItem
import com.example.geosamplemanager.data.voice.WeightQueueKind
import com.example.geosamplemanager.data.voice.DigitGroup
import com.example.geosamplemanager.data.voice.DigitGrouper
import com.example.geosamplemanager.data.voice.GroupKind
import com.example.geosamplemanager.data.voice.GroupToCandidates
import com.example.geosamplemanager.data.voice.QueryNormalizer
import com.example.geosamplemanager.data.voice.QuerySplitter
import com.example.geosamplemanager.data.voice.QueryToken
import com.example.geosamplemanager.data.voice.QueryTokenizer
import com.example.geosamplemanager.data.voice.SearchResult
import com.example.geosamplemanager.data.voice.SearchService
import com.example.geosamplemanager.data.voice.VoiceSampleHit
import kotlinx.coroutines.CancellationException
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

    private val queryTokenizer = QueryTokenizer()
    private val searchService by lazy {
        SearchService(VoiceSearchRepository(getApplication()))
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    private val loadedOrderIds = mutableSetOf<Long>()
    private var orderInfoById: Map<Long, OrderInfo> = emptyMap()

    private var orderInfoByTitle: Map<String, OrderInfo> = emptyMap()
    private var orderInfoByComposite: Map<String, OrderInfo> = emptyMap()
    private var searchJob: Job? = null

    private val voiceCommandLikeWords = setOf(
        "стоп", "хватит", "пауза", "паузу", "продолжить", "продолжай",
        "отмена", "отменить", "верни", "назад", "повтори", "вперёд", "вперед",
        "следующая", "следующий", "следующую", "далее", "дальше",
        "помощь", "команда", "команды", "сколько", "осталось",
        "показать", "отложенные", "найденные"
    )

    companion object {
        private const val TAG = "ReconciliationVM"
        private const val SEARCH_DEBOUNCE_MS = 500L
        private const val MAX_SEARCH_ORDERS = 20
        private const val MAX_QUERY_TOKENS = 5
        private const val MAX_LEFT_LIST = 10
    }

    init {
        subscribeToAreasAndOrders()
        loadVoiceUiSettings()
    }

    private fun loadVoiceUiSettings() {
        viewModelScope.launch {
            try {
                val vs = voiceSettingsRepo.load()
                withContext(Dispatchers.Main) {
                    state.showCharacteristic = vs.showCharacteristic
                    if (vs.showOnboarding) {
                        state.voiceOnboardingVisible = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun dismissOnboarding() {
        state.voiceOnboardingVisible = false
        viewModelScope.launch {
            try {
                val vs = voiceSettingsRepo.load()
                voiceSettingsRepo.save(vs.copy(showOnboarding = false))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun setShowCharacteristic(value: Boolean) {
        state.showCharacteristic = value
        viewModelScope.launch {
            try {
                val vs = voiceSettingsRepo.load()
                voiceSettingsRepo.save(vs.copy(showCharacteristic = value))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
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
                    var orphaned = 0

                    for (order in orders) {
                        val area = areas.firstOrNull { it.id == order.areaId }

                        if (area == null) {
                            orphaned++
                            Log.w(TAG, "наряд id=${order.id} без участка")
                            result.add(
                                OrderInfo(
                                    areaId = -1L,
                                    orderId = order.id,
                                    areaTitle = "—",
                                    orderTitle = "Наряд №${order.orderNumber}"
                                )
                            )
                        } else {
                            result.add(
                                OrderInfo(
                                    areaId = area.id,
                                    orderId = order.id,
                                    areaTitle = area.areaName,
                                    orderTitle = "Наряд №${order.orderNumber}"
                                )
                            )
                        }
                    }

                    if (orphaned > 0) {
                        Log.w(TAG, "всего нарядов без участка: $orphaned")
                    }

                    val areaNames = areas.map { it.areaName }.distinct().sorted()
                    areaNames to result
                }.collect { (areaNames, orderInfos) ->
                    withContext(Dispatchers.Main) {
                        orderInfoById = orderInfos.associateBy { it.orderId }
                        orderInfoByTitle = orderInfos.associateBy { it.orderTitle }
                        orderInfoByComposite = orderInfos.associateBy {
                            compositeKey(it.areaTitle, it.orderTitle)
                        }
                        state.allOrderTitles = orderInfos
                        state.allAreaNames = areaNames
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = "Ошибка загрузки: ${e.message}"
            }
        }
    }

    private fun compositeKey(areaTitle: String, orderTitle: String): String =
        "$areaTitle|$orderTitle"

    suspend fun ensureOrderSamplesLoaded(orderId: Long) {
        if (orderId in loadedOrderIds) return
        orderInfoById[orderId] ?: return

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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _message.value = "Ошибка загрузки наряда: ${e.message}"
        }
    }

    private suspend fun loadGroupsForQuery(query: String) {
        try {
            val allIds = withContext(Dispatchers.IO) { repo.findOrderIdsByQuery(query) }
            if (allIds.isEmpty()) return
            val newIds = allIds.filter { it !in loadedOrderIds }
            newIds.take(MAX_SEARCH_ORDERS).forEach { ensureOrderSamplesLoaded(it) }
        } catch (e: CancellationException) {
            throw e
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
        val info = resolveOrderInfo(orderTitle)
        if (info != null) {
            viewModelScope.launch { ensureOrderSamplesLoaded(info.orderId) }
        } else if (orderTitle != null) {
            Log.w(TAG, "setSelectedOrder: нет orderInfo для «$orderTitle»")
        }
        refreshMultiQueryIfNeeded()
    }

    private fun resolveOrderInfo(orderTitle: String?): OrderInfo? {
        if (orderTitle == null) return null
        val area = state.selectedArea
        if (area != null) {
            orderInfoByComposite[compositeKey(area, orderTitle)]?.let { return it }
        }
        return orderInfoByTitle[orderTitle]
    }

    private fun refreshMultiQueryIfNeeded() {
        if (!state.isMultiQuery) return
        val tokens = state.queryTokens
        searchJob?.cancel()
        searchJob = viewModelScope.launch { buildMultiQueryGroups(tokens) }
    }

    fun setQuery(query: String) {
        if (voiceSession.isPinned || voiceSession.hasQueue) {
            voiceSession.unpin()
            voiceSession.clearQueue()
        }

        state.query = query
        searchJob?.cancel()

        if (query.isBlank()) {
            state.queryTokens = emptyList()
            state.clearQueryGroups()
            return
        }

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)

            val normalized = QueryNormalizer.normalize(query)
            val tokens = queryTokenizer.tokenize(normalized).take(MAX_QUERY_TOKENS)
            val requests = splitIntoRequests(tokens)

            when (requests.size) {
                0 -> {
                    state.queryTokens = emptyList()
                    state.clearQueryGroups()
                }
                1 -> {
                    val request = requests[0]
                    val requestStr = buildQueryString(request)
                    state.queryTokens = listOf(requestStr)
                    state.clearQueryGroups()
                    loadGroupsForQueryNew(request)
                }
                else -> {
                    val oldTokens = requests.map { req -> buildQueryString(req) }
                    state.queryTokens = oldTokens.take(MAX_QUERY_TOKENS)
                    buildMultiQueryGroups(oldTokens)
                }
            }
        }
    }

    private fun splitIntoRequests(tokens: List<QueryToken>): List<List<QueryToken>> =
        QuerySplitter.splitIntoRequests(tokens)

    private fun buildQueryString(tokens: List<QueryToken>): String {
        val parts = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val next = tokens.getOrNull(i + 1)
            if (t is QueryToken.Prefix && next is QueryToken.Number) {
                parts.add(t.value + next.value)
                i += 2
                continue
            }
            parts.add(tokenToString(t))
            i++
        }
        return parts.joinToString(" ")
    }

    private fun tokenToString(t: QueryToken): String = when (t) {
        is QueryToken.Prefix -> t.value
        is QueryToken.Number -> t.value
        is QueryToken.Ordinal -> t.value.toString()
        is QueryToken.CommandWord -> t.value
        is QueryToken.Separator -> t.value
        is QueryToken.Unknown -> t.raw
    }

    private suspend fun loadGroupsForQueryNew(tokens: List<QueryToken>) {
        try {
            val groups: List<DigitGroup> = DigitGrouper.group(tokens)
            if (groups.isEmpty()) return
            val candidates: List<String> = GroupToCandidates.toCandidates(groups)
            if (candidates.isEmpty()) return

            val result = searchService.search(
                candidates = candidates,
                groups = groups,
                queryTokens = tokens
            )

            if (result !is SearchResult.Found) return

            result.hits.map { it.orderId }.distinct().take(MAX_SEARCH_ORDERS)
                .forEach { ensureOrderSamplesLoaded(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _message.value = "Ошибка поиска: ${e.message}"
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

            withContext(Dispatchers.Main) {
                state.setQueryGroups(queryGroups)
            }
        } catch (e: CancellationException) {
            throw e
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

    fun setVoiceStatus(status: VoiceStatus) {
        state.voiceStatus = status
    }

    fun checkWaitTimeout(): Int {
        val now = System.currentTimeMillis()

        if (voiceSession.hasWeightQueue) {
            val elapsed = now - voiceSession.weightQueueStartedAt
            val warnMs = 20_000L
            val expireMs = 30_000L

            if (elapsed >= expireMs) {
                voiceSession.clearWeightQueue()
                return 2
            }

            if (elapsed >= warnMs && elapsed < warnMs + 500) {
                return 1
            }

            return 0
        }

        val startedAt = voiceSession.pendingMarkIntent?.startedAt
            ?: voiceSession.pendingConfirm?.startedAt

        if (startedAt == null) return 0

        val elapsed = now - startedAt
        val warnMs = 20_000L
        val expireMs = 30_000L

        if (elapsed >= expireMs) {
            voiceSession.clearMarkIntent()
            voiceSession.clearPendingConfirm()
            return 2
        }

        if (elapsed >= warnMs && elapsed < warnMs + 500) {
            return 1
        }

        return 0
    }

    suspend fun voiceExecute(cmd: VoiceCommand): VoiceExecResult {
        Log.i(
            TAG,
            "voiceExecute: $cmd (state=${voiceSession.state}, " +
                    "pinned=${voiceSession.isPinned}, queue=${voiceSession.queue.size}, " +
                    "weightQueue=${voiceSession.weightQueue.size}, " +
                    "mode=${voiceSession.mode})"
        )

        if (cmd is VoiceCommand.Stop) return VoiceExecResult.Stopped

        when (cmd) {
            VoiceCommand.ChoiceRemove -> return voiceChoiceRemove()
            VoiceCommand.ChoicePostpone -> return voiceChoicePostpone()
            VoiceCommand.ChoiceSkip -> return voiceChoiceSkip()
            else -> Unit
        }

        if (voiceSession.hasWeightQueue) {
            return handleWeightQueue(cmd)
        }

        if (voiceSession.pendingConfirm != null) {
            return handlePendingConfirm(cmd)
        }

        if (voiceSession.pendingMarkIntent != null) {
            return handlePendingMarkIntent(cmd)
        }

        if (cmd is VoiceCommand.MarkCurrent && voiceSession.pendingMarkChoice != null) {
            return voiceChoiceMarkCurrent()
        }

        if (voiceSession.pendingMarkChoice != null) {
            return handlePendingChoiceFallback(cmd)
        }

        if (voiceSession.isPaused) {
            return when (cmd) {
                VoiceCommand.Resume -> {
                    voiceSession.isPaused = false
                    VoiceExecResult.Message("Продолжаю")
                }
                VoiceCommand.Pause -> VoiceExecResult.Message("Пауза")
                else -> VoiceExecResult.Message(
                    "Пауза. Скажите «продолжить» или «стоп»."
                )
            }
        }

        if (voiceSession.awaitingContinue) {
            return when (cmd) {
                VoiceCommand.Resume -> {
                    voiceSession.awaitingContinue = false
                    VoiceExecResult.Message("Продолжаю")
                }
                VoiceCommand.Pause -> {
                    voiceSession.awaitingContinue = false
                    voiceSession.isPaused = true
                    VoiceExecResult.Message("Пауза")
                }
                is VoiceCommand.Search -> {
                    voiceSession.awaitingContinue = false
                    if (voiceSession.mode == VoiceSessionMode.SORT) {
                        voiceSort(listOf(cmd.query))
                    } else {
                        voiceSearch(cmd.query)
                    }
                }

                is VoiceCommand.Sort -> {
                    voiceSession.awaitingContinue = false
                    voiceSort(cmd.queries)
                }

                is VoiceCommand.Find -> {
                    voiceSession.awaitingContinue = false
                    if (cmd.query.isNullOrBlank()) {
                        if (voiceSession.mode != VoiceSessionMode.SORT) {
                            voiceSession.mode = VoiceSessionMode.SEARCH
                        }
                        VoiceExecResult.Message("Поиск. Скажите номер.")
                    } else {
                        if (voiceSession.mode == VoiceSessionMode.SORT) {
                            voiceSort(listOf(cmd.query))
                        } else {
                            voiceSession.mode = VoiceSessionMode.SEARCH
                            voiceSearch(cmd.query)
                        }
                    }
                }

                else -> VoiceExecResult.Message("Скажите «продолжить» или «стоп».")
            }
        }

        if (voiceSession.awaitingWeight) {
            if (cmd is VoiceCommand.Pause) {
                voiceSession.awaitingWeight = false
                voiceSession.isPaused = true
                return VoiceExecResult.Message("Пауза")
            }

            if (cmd is VoiceCommand.SetWeight) {
                return when (val v = validateWeight(cmd.value)) {
                    is WeightValidation.Ok -> {
                        voiceSession.awaitingWeight = false
                        voiceSetWeight(v.value)
                    }
                    is WeightValidation.Invalid -> {
                        VoiceExecResult.Message("Не понял вес. Повторите.")
                    }
                }
            }

            val weightText = when (cmd) {
                is VoiceCommand.Search -> cmd.query
                is VoiceCommand.Sort -> cmd.queries.joinToString(" ")
                else -> null
            }

            if (weightText != null) {
                val parsed = commandParser.parseWeightAnswer(weightText)
                when (val v = validateWeight(parsed)) {
                    is WeightValidation.Ok -> {
                        voiceSession.awaitingWeight = false
                        return voiceSetWeight(v.value)
                    }
                    is WeightValidation.Invalid -> {
                        return VoiceExecResult.Message("Не понял вес. Повторите.")
                    }
                }
            }

            if (cmd is VoiceCommand.Undo) {
                voiceSession.awaitingWeight = false
                return VoiceExecResult.Undone
            }

            return VoiceExecResult.Message("Сначала скажите вес или «отмена».")
        }

        return when (cmd) {
            is VoiceCommand.Search -> {
                if (voiceSession.mode == VoiceSessionMode.SORT) {
                    voiceSession.unpin()
                    voiceSession.clearQueue()
                    voiceSort(listOf(cmd.query))
                } else {
                    handleSearchInSession(cmd.query)
                }
            }

            is VoiceCommand.Find -> {
                voiceSession.unpin()
                voiceSession.clearQueue()
                if (cmd.query.isNullOrBlank()) {
                    if (voiceSession.mode != VoiceSessionMode.SORT) {
                        voiceSession.mode = VoiceSessionMode.SEARCH
                    }
                    VoiceExecResult.Message("Поиск. Скажите номер.")
                } else {
                    if (voiceSession.mode == VoiceSessionMode.SORT) {
                        voiceSort(listOf(cmd.query))
                    } else {
                        voiceSession.mode = VoiceSessionMode.SEARCH
                        handleSearchInSession(cmd.query)
                    }
                }
            }

            is VoiceCommand.MarkOrdinal -> markGuard { voiceMarkOrdinal(cmd.ordinal) }
            is VoiceCommand.MarkByNumbers -> markGuard { voiceMarkByNumbers(cmd.ordinals) }

            VoiceCommand.MarkAll -> markGuard {
                voiceRequestConfirm(ConfirmedAction.MARK_ALL)
            }
            VoiceCommand.ClearAll -> markGuard {
                voiceRequestConfirm(ConfirmedAction.CLEAR_ALL)
            }

            VoiceCommand.MarkCurrent -> markGuard { voiceMarkCurrent() }

            VoiceCommand.MarkIntent -> markGuard {
                voiceStartMarkIntent(PendingMarkIntentType.MARK)
            }
            VoiceCommand.ClearIntent -> markGuard {
                voiceStartMarkIntent(PendingMarkIntentType.CLEAR)
            }
            VoiceCommand.PostponeIntent -> markGuard {
                voiceStartMarkIntent(PendingMarkIntentType.POSTPONE)
            }

            VoiceCommand.Confirm -> VoiceExecResult.Message("Нечего подтверждать.")
            VoiceCommand.Decline -> VoiceExecResult.Message("Нечего отменять.")
            VoiceCommand.SkipWeightItem -> VoiceExecResult.Message("Нет очереди веса.")

            is VoiceCommand.SetWeight -> voiceSetWeight(cmd.value)

            is VoiceCommand.ClearOrdinal -> markGuard { voiceClearOrdinal(cmd.ordinal) }
            VoiceCommand.ClearLast -> markGuard { voiceClearLast() }

            is VoiceCommand.PostponeOrdinal -> markGuard { voicePostponeOrdinal(cmd.ordinal) }

            VoiceCommand.Unpostpone -> voiceUnpostpone()

            VoiceCommand.ChoiceRemove -> voiceChoiceRemove()
            VoiceCommand.ChoicePostpone -> voiceChoicePostpone()
            VoiceCommand.ChoiceSkip -> voiceChoiceSkip()

            // FIX 5.8.11-sort-fix-5: одна команда Next.
            VoiceCommand.Next -> voiceNext()

            VoiceCommand.Undo -> {
                voiceSession.unpin()
                voiceSession.clearQueue()
                undo()
                VoiceExecResult.Undone
            }

            VoiceCommand.Redo -> {
                redo()
                VoiceExecResult.Redone
            }

            VoiceCommand.Stop -> VoiceExecResult.Stopped

            VoiceCommand.Pause -> {
                voiceSession.isPaused = true
                VoiceExecResult.Message("Пауза")
            }

            VoiceCommand.Resume -> {
                voiceSession.isPaused = false
                VoiceExecResult.Message("Продолжаю")
            }

            VoiceCommand.HowManyLeft -> voiceHowManyLeft()
            VoiceCommand.ShowPostponed -> voiceShowFilter(ResultFilter.POSTPONED, "Отложенные")
            VoiceCommand.ShowFound -> voiceShowFilter(ResultFilter.FOUND, "Найденные")

            VoiceCommand.Help -> VoiceExecResult.Message(
                "Скажи номер, «отметь», «отметь все», «снять», «снять все», " +
                        "«отложить», «отложить вторую», «следующая», «стоп», «пауза»."
            )

            is VoiceCommand.Sort -> voiceSort(cmd.queries)
            is VoiceCommand.SetMode -> voiceSetMode(cmd.mode)

            VoiceCommand.Unknown -> VoiceExecResult.Message("Не понял команду")
        }
    }

    // ================================================================
    // Очередь веса
    // ================================================================

    private suspend fun handleWeightQueue(cmd: VoiceCommand): VoiceExecResult {
        val item = voiceSession.currentWeightItem
            ?: return finishWeightQueue()

        when (cmd) {
            is VoiceCommand.SetWeight -> {
                return when (val v = validateWeight(cmd.value)) {
                    is WeightValidation.Ok -> {
                        applyWeightToQueueItem(item, v.value)
                        voiceSession.weightQueueMarked++
                        advanceOrFinishWeightQueue()
                    }
                    is WeightValidation.Invalid -> {
                        VoiceExecResult.Message("Не понял вес. Повторите.")
                    }
                }
            }

            VoiceCommand.SkipWeightItem -> {
                voiceSession.weightQueueSkipped++
                return advanceOrFinishWeightQueue()
            }

            VoiceCommand.Stop -> return VoiceExecResult.Stopped

            VoiceCommand.Pause -> {
                voiceSession.isPaused = true
                return VoiceExecResult.Message("Пауза")
            }

            VoiceCommand.Undo -> {
                voiceSession.clearWeightQueue()
                return VoiceExecResult.Message("Отменено.")
            }

            else -> {
                val question = weightQuestion(item)
                return VoiceExecResult.Message(question)
            }
        }
    }

    private fun applyWeightToQueueItem(item: WeightQueueItem, weight: Double) {
        val row = state.groups
            .asSequence()
            .flatMap { it.rows.asSequence() }
            .firstOrNull { it.sampleNumber == item.sampleNumber }
            ?: return

        when (item.kind) {
            WeightQueueKind.BLANK -> setBlankWeightAndMarkFound(row.id, weight)
            WeightQueueKind.WEIGHT_CONTROL -> setControlWeightAndFound(row.id, weight)
        }
    }

    private fun advanceOrFinishWeightQueue(): VoiceExecResult {
        if (voiceSession.advanceWeightQueue()) {
            val item = voiceSession.currentWeightItem
                ?: return finishWeightQueue()
            return VoiceExecResult.WeightQueueAsked(
                item = item,
                index = voiceSession.weightQueuePosition,
                total = voiceSession.weightQueueTotal,
                marked = voiceSession.weightQueueMarked,
                skipped = voiceSession.weightQueueSkipped
            )
        }
        return finishWeightQueue()
    }

    private fun finishWeightQueue(): VoiceExecResult {
        val marked = voiceSession.weightQueueMarked
        val skipped = voiceSession.weightQueueSkipped
        voiceSession.clearWeightQueue()
        return VoiceExecResult.WeightQueueDone(marked = marked, skipped = skipped)
    }

    private fun weightQuestion(item: WeightQueueItem): String {
        val type = when (item.kind) {
            WeightQueueKind.BLANK -> "Холостая"
            WeightQueueKind.WEIGHT_CONTROL -> "Весовой контроль"
        }
        val word = VoiceOrdinals.word(item.ordinal) ?: "номер ${item.ordinal}"
        return "$type, $word. Вес?"
    }

    // ================================================================

    private fun voiceStartMarkIntent(type: PendingMarkIntentType): VoiceExecResult {
        val orderId = voiceSession.currentOrderId
            ?: return VoiceExecResult.Message("Сначала найдите скважину")
        val wellNumber = voiceSession.currentWellNumber
            ?: return VoiceExecResult.Message("Сначала найдите скважину")

        if (state.groupById(orderId.toString()) == null) {
            return VoiceExecResult.Message("Наряд не загружен")
        }

        voiceSession.startMarkIntent(type)

        val question = when (type) {
            PendingMarkIntentType.MARK -> "Какую пробу отметить?"
            PendingMarkIntentType.CLEAR -> "Какую снять?"
            PendingMarkIntentType.POSTPONE -> "Какую отложить?"
        }

        return VoiceExecResult.Message(question)
    }

    private fun handlePendingMarkIntent(cmd: VoiceCommand): VoiceExecResult {
        val intent = voiceSession.pendingMarkIntent
            ?: return VoiceExecResult.Message("Ошибка состояния")

        when (cmd) {
            is VoiceCommand.MarkOrdinal -> {
                voiceSession.clearMarkIntent()
                return when (intent.type) {
                    PendingMarkIntentType.MARK -> voiceMarkOrdinal(cmd.ordinal)
                    PendingMarkIntentType.CLEAR -> voiceClearOrdinal(cmd.ordinal)
                    PendingMarkIntentType.POSTPONE -> voicePostponeOrdinal(cmd.ordinal)
                }
            }

            is VoiceCommand.MarkByNumbers -> {
                voiceSession.clearMarkIntent()
                return when (intent.type) {
                    PendingMarkIntentType.MARK -> voiceMarkByNumbers(cmd.ordinals)
                    PendingMarkIntentType.CLEAR -> voiceClearByNumbers(cmd.ordinals)
                    PendingMarkIntentType.POSTPONE -> voicePostponeByNumbers(cmd.ordinals)
                }
            }

            VoiceCommand.Undo -> {
                voiceSession.clearMarkIntent()
                return VoiceExecResult.Message("Отменено.")
            }

            VoiceCommand.Pause -> {
                voiceSession.clearMarkIntent()
                voiceSession.isPaused = true
                return VoiceExecResult.Message("Пауза")
            }

            VoiceCommand.Stop -> return VoiceExecResult.Stopped

            else -> {
                val question = when (intent.type) {
                    PendingMarkIntentType.MARK -> "Скажите номер пробы."
                    PendingMarkIntentType.CLEAR -> "Скажите номер пробы для снятия."
                    PendingMarkIntentType.POSTPONE -> "Скажите номер для отложения."
                }
                return VoiceExecResult.Message(question)
            }
        }
    }

    private fun voiceRequestConfirm(action: ConfirmedAction): VoiceExecResult {
        val orderId = voiceSession.currentOrderId
            ?: return VoiceExecResult.Message("Сначала найдите скважину")
        val wellNumber = voiceSession.currentWellNumber
            ?: return VoiceExecResult.Message("Сначала найдите скважину")
        val group = state.groupById(orderId.toString())
            ?: return VoiceExecResult.Message("Наряд не загружен")

        val wellRows = group.rows.filter { it.wellNumber == wellNumber }

        val count = when (action) {
            ConfirmedAction.MARK_ALL -> wellRows.count { !it.found }
            ConfirmedAction.CLEAR_ALL -> wellRows.count { it.found }
        }

        if (count == 0) {
            return when (action) {
                ConfirmedAction.MARK_ALL -> VoiceExecResult.Message("Все пробы уже отмечены")
                ConfirmedAction.CLEAR_ALL -> VoiceExecResult.Message("Нет отмеченных проб")
            }
        }

        voiceSession.startPendingConfirm(action, count)

        val phrase = when (action) {
            ConfirmedAction.MARK_ALL ->
                "Отметить все ${count} ${samplesWord(count)}? " +
                        "Скажите «подтверждаю» или «отменяю»."
            ConfirmedAction.CLEAR_ALL ->
                "Снять отметки со всех ${count} ${samplesWord(count)}? " +
                        "Скажите «подтверждаю» или «отменяю»."
        }

        return VoiceExecResult.Message(phrase)
    }

    private fun samplesWord(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> "пробы"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "проб"
        else -> "проб"
    }

    private suspend fun handlePendingConfirm(cmd: VoiceCommand): VoiceExecResult {
        val pending = voiceSession.pendingConfirm
            ?: return VoiceExecResult.Message("Ошибка состояния")

        when (cmd) {
            VoiceCommand.Confirm -> {
                voiceSession.clearPendingConfirm()
                return when (pending.action) {
                    ConfirmedAction.MARK_ALL -> voiceMarkAll()
                    ConfirmedAction.CLEAR_ALL -> voiceClearAll()
                }
            }

            VoiceCommand.Decline -> {
                voiceSession.clearPendingConfirm()
                return VoiceExecResult.Message("Отменено.")
            }

            VoiceCommand.Stop -> return VoiceExecResult.Stopped

            VoiceCommand.Pause -> {
                voiceSession.clearPendingConfirm()
                voiceSession.isPaused = true
                return VoiceExecResult.Message("Пауза")
            }

            else -> return VoiceExecResult.Message(
                "Скажите «подтверждаю» или «отменяю»."
            )
        }
    }

    private inline fun markGuard(action: () -> VoiceExecResult): VoiceExecResult {
        if (voiceSession.mode == VoiceSessionMode.SORT) {
            return VoiceExecResult.Message("Режим сортировки — отметки недоступны.")
        }
        return action()
    }

    private fun voiceSetMode(mode: VoiceSessionMode): VoiceExecResult {
        voiceSession.mode = mode
        return VoiceExecResult.ModeChanged(mode)
    }

    private suspend fun handleSearchInSession(query: String): VoiceExecResult {
        if (!isLikelyVoiceSearchQuery(query)) {
            return VoiceExecResult.Message("Не понял команду")
        }
        return voiceSearch(query)
    }

    private suspend fun voiceSearch(query: String): VoiceExecResult {
        Log.i(TAG, "voiceSearch: query=«$query»")

        return try {
            val settings = withContext(Dispatchers.IO) { settingsRepo.load() }
            val voiceSettings = withContext(Dispatchers.IO) { voiceSettingsRepo.load() }

            val allPrefixes = settings.areaPrefixes.values.flatten().toSet()
            val prefixResolver = VoicePrefixResolver(
                allPrefixes,
                voiceSettings.customPrefixPronunciations
            )

            val extraction = prefixResolver.extract(query)
            val numberText = extraction.remainder.ifEmpty { query }

            val parsed = voiceParser.parse(numberText)
            val rawCandidates = parsed.candidates
            val groups = buildGroupsForVoice(extraction.prefix, rawCandidates)

            val candidates = rawCandidates.map { c ->
                val clean = c.replace("|", "")
                if (extraction.prefix != null) extraction.prefix + clean else clean
            }

            val result = searchService.search(
                candidates = candidates,
                groups = groups,
                queryTokens = emptyList()
            )

            when (result) {
                SearchResult.NotFound -> {
                    if (!voiceSession.isPinned) {
                        voiceSession.clear()
                    }
                    val displayQuery = candidates.firstOrNull() ?: query
                    if (isLikelyVoiceSearchQuery(displayQuery)) {
                        withContext(Dispatchers.Main) {
                            state.query = displayQuery
                        }
                    }
                    VoiceExecResult.NotFound
                }

                is SearchResult.Failed -> {
                    Log.e(TAG, "voiceSearch failed: ${result.error}")
                    VoiceExecResult.Message("Ошибка поиска: ${result.error}")
                }

                is SearchResult.Found -> {
                    val hit = result.hits.first()
                    val isSample = result.matchedKind == UnifiedMatchKind.SAMPLE

                    if (!result.isUnique) {
                        voiceSession.awaitingContinue = true
                        voiceSession.isAutoMode = false

                        withContext(Dispatchers.Main) {
                            state.query = result.matchedValue
                        }

                        return VoiceExecResult.FoundMany(
                            result.matchedValue,
                            result.hits.size
                        )
                    }

                    voiceSession.clearQueue()

                    val oldWell = voiceSession.currentWellNumber
                    val oldOrder = voiceSession.currentOrderId
                    val newWell = hit.wellNumber
                    val newOrder = hit.orderId

                    if (oldOrder != null && (oldOrder != newOrder || oldWell != newWell)) {
                        voiceSession.lastMarkedRowId = null
                        voiceSession.lastMarkedSampleNumber = null
                        voiceSession.awaitingWeight = false
                    }

                    voiceSession.currentOrderId = newOrder
                    voiceSession.currentOrderTitle = "Наряд №${hit.orderNumber}"
                    voiceSession.currentAreaTitle = hit.areaTitle
                    voiceSession.currentWellNumber = newWell
                    voiceSession.currentSampleNumber = null
                    voiceSession.currentSampleOrdinal = null

                    voiceSession.pin(
                        orderId = newOrder,
                        orderTitle = "Наряд №${hit.orderNumber}",
                        areaTitle = hit.areaTitle,
                        wellNumber = newWell
                    )

                    val selectedArea = state.selectedArea
                    val selectedOrder = state.selectedOrder

                    val attentionReason: AnswerReason? = when {
                        selectedArea != null && hit.areaTitle != selectedArea ->
                            AnswerReason.FOUND_OTHER_AREA
                        selectedOrder != null &&
                                "Наряд №${hit.orderNumber}" != selectedOrder ->
                            AnswerReason.FOUND_OTHER_ORDER
                        else -> null
                    }

                    voiceSession.isAutoMode = attentionReason == null &&
                            voiceSession.mode == VoiceSessionMode.SEARCH

                    voiceSession.awaitingWeight = false

                    val displayQuery: String
                    var totalSamples = 0
                    var foundSamples = 0
                    var blanks = 0
                    var weightControls = 0
                    var postponed = 0

                    if (isSample) {
                        displayQuery = result.matchedValue
                        ensureOrderSamplesLoaded(hit.orderId)
                        val group = state.groupById(hit.orderId.toString())
                        val sampleRow = group?.rows?.firstOrNull {
                            it.sampleNumber == hit.sampleNumber
                        }
                        if (sampleRow != null) {
                            voiceSession.currentSampleNumber = sampleRow.sampleNumber
                            voiceSession.currentSampleOrdinal = sampleRow.numberInWell
                            totalSamples = 1
                            foundSamples = if (sampleRow.found) 1 else 0
                            blanks = if (sampleRow.isBlank) 1 else 0
                            weightControls = if (sampleRow.weightControl) 1 else 0
                            postponed = if (sampleRow.postponed) 1 else 0
                        }
                    } else {
                        displayQuery = result.matchedValue
                        ensureOrderSamplesLoaded(hit.orderId)
                        val group = state.groupById(hit.orderId.toString())
                        val wellRows = group?.rows?.filter { it.wellNumber == hit.wellNumber }
                            ?: emptyList()
                        totalSamples = wellRows.size
                        foundSamples = wellRows.count { it.found }
                        blanks = wellRows.count { it.isBlank }
                        weightControls = wellRows.count { it.weightControl }
                        postponed = wellRows.count { it.postponed }
                    }

                    voiceSession.currentQuery = displayQuery
                    withContext(Dispatchers.Main) {
                        state.query = displayQuery
                    }

                    VoiceExecResult.FoundOne(
                        query = displayQuery,
                        orderTitle = voiceSession.currentOrderTitle ?: "",
                        wellNumber = hit.wellNumber,
                        totalSamples = totalSamples,
                        foundSamples = foundSamples,
                        isSample = isSample,
                        blanks = blanks,
                        weightControls = weightControls,
                        postponed = postponed,
                        attentionReason = attentionReason,
                        otherAreaTitle = hit.areaTitle,
                        otherOrderNumber = hit.orderNumber,
                        groups = result.groups,
                        queueSize = 0
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "voiceSearch: упал", e)
            VoiceExecResult.Message("Ошибка поиска: ${e.message}")
        }
    }

    private fun buildGroupsForVoice(
        prefix: String?,
        candidates: List<String>
    ): List<DigitGroup> {
        if (prefix == null && candidates.isEmpty()) return emptyList()

        val withSep = candidates.firstOrNull { it.contains("|") }
        val chosen = withSep ?: candidates.firstOrNull() ?: return emptyList()

        val result = mutableListOf<DigitGroup>()
        if (prefix != null) {
            result.add(DigitGroup(value = prefix, kind = GroupKind.PREFIX))
        }

        val parts = if (chosen.contains("|")) {
            chosen.split("|").filter { it.isNotBlank() }
        } else {
            listOf(chosen)
        }

        for (part in parts) {
            val kind = when {
                part.length >= 2 && part.all { it == '0' } -> GroupKind.LEADING_ZERO
                part.length == 1 -> GroupKind.SINGLE
                else -> GroupKind.PLAIN
            }
            result.add(DigitGroup(value = part, kind = kind))
        }
        return result
    }

    private suspend fun voiceNextInQueue(): VoiceExecResult {
        if (!voiceSession.hasQueue) {
            return VoiceExecResult.Message("Очередь пуста. Скажите «следующая» для нового запроса.")
        }

        val next = voiceSession.nextInQueue()
            ?: return VoiceExecResult.Message("Очередь пуста.")

        voiceSession.currentOrderId = next.orderId
        voiceSession.currentOrderTitle = next.orderTitle
        voiceSession.currentAreaTitle = next.areaTitle
        voiceSession.currentWellNumber = next.wellNumber
        voiceSession.currentSampleNumber = null
        voiceSession.currentSampleOrdinal = null
        voiceSession.lastMarkedRowId = null
        voiceSession.lastMarkedSampleNumber = null

        ensureOrderSamplesLoaded(next.orderId)

        val group = state.groupById(next.orderId.toString())
        val wellRows = group?.rows?.filter { it.wellNumber == next.wellNumber } ?: emptyList()

        val totalSamples = wellRows.size
        val foundSamples = wellRows.count { it.found }
        val blanks = wellRows.count { it.isBlank }
        val weightControls = wellRows.count { it.weightControl }
        val postponed = wellRows.count { it.postponed }

        withContext(Dispatchers.Main) {
            state.query = next.wellNumber
        }

        voiceSession.currentQuery = next.wellNumber

        val currentQueueSize = voiceSession.queue.size + 1

        val selectedArea = state.selectedArea
        val selectedOrder = state.selectedOrder
        val attentionReason: AnswerReason? = when {
            selectedArea != null && next.areaTitle != selectedArea ->
                AnswerReason.FOUND_OTHER_AREA
            selectedOrder != null && next.orderTitle != selectedOrder ->
                AnswerReason.FOUND_OTHER_ORDER
            else -> null
        }

        return VoiceExecResult.FoundOne(
            query = next.wellNumber,
            orderTitle = next.orderTitle,
            wellNumber = next.wellNumber,
            totalSamples = totalSamples,
            foundSamples = foundSamples,
            isSample = false,
            blanks = blanks,
            weightControls = weightControls,
            postponed = postponed,
            attentionReason = attentionReason,
            otherAreaTitle = next.areaTitle,
            otherOrderNumber = next.orderTitle.removePrefix("Наряд №").trim(),
            groups = emptyList(),
            queueSize = currentQueueSize
        )
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
        }

        if (row == null) {
            val hint = VoiceMarkOrdinalFallback.hintFor(ordinal)
            if (hint != null) {
                Log.i(TAG, "voiceMarkOrdinal: пробы №$ordinal нет, подсказка → $hint")
            }
            return VoiceExecResult.MarkOrdinalNotFound(ordinal, hint)
        }

        return applyMarkDecision(row)
    }

    private fun voiceMarkCurrent(): VoiceExecResult {
        val orderId = voiceSession.currentOrderId
            ?: return VoiceExecResult.Message("Сначала найдите пробу")
        val sampleNumber = voiceSession.currentSampleNumber
            ?: return VoiceExecResult.Message("Сначала найдите пробу")
        val group = state.groupById(orderId.toString())
            ?: return VoiceExecResult.Message("Наряд не загружен")
        val row = group.rows.firstOrNull { it.sampleNumber == sampleNumber }
            ?: return VoiceExecResult.Message("Проба не найдена в наряде")
        return applyMarkDecision(row)
    }

    private fun applyMarkDecision(row: SampleRow): VoiceExecResult {
        val decision = analyzeMark(toMarkContext(state, row))
        Log.i(TAG, "applyMarkDecision: row=${row.sampleNumber} decision=$decision")

        return when (decision) {
            is MarkDecision.CanMark -> {
                setFound(row.id, true)
                voiceSession.lastMarkedRowId = row.id
                voiceSession.lastMarkedSampleNumber = row.sampleNumber
                VoiceExecResult.Marked(
                    sampleNumber = row.sampleNumber,
                    ordinal = row.numberInWell,
                    isWeightControl = row.weightControl,
                    needsWeight = false
                )
            }
            is MarkDecision.MarkWithWeight -> {
                setBlankWeightAndMarkFound(row.id, decision.weight)
                voiceSession.lastMarkedRowId = row.id
                voiceSession.lastMarkedSampleNumber = row.sampleNumber
                VoiceExecResult.Marked(
                    sampleNumber = row.sampleNumber,
                    ordinal = row.numberInWell,
                    isWeightControl = false,
                    needsWeight = false
                )
            }
            is MarkDecision.NeedsControlWeight -> {
                voiceSession.lastMarkedRowId = row.id
                voiceSession.lastMarkedSampleNumber = row.sampleNumber
                voiceSession.awaitingWeight = true
                VoiceExecResult.Message(MarkDecisionVoiceRenderer.render(decision))
            }
            is MarkDecision.NeedsBlankWeight -> {
                voiceSession.lastMarkedRowId = row.id
                voiceSession.lastMarkedSampleNumber = row.sampleNumber
                voiceSession.awaitingWeight = true
                VoiceExecResult.Message(MarkDecisionVoiceRenderer.render(decision))
            }
            is MarkDecision.AlreadyFound -> {
                voiceSession.lastMarkedRowId = row.id
                voiceSession.lastMarkedSampleNumber = row.sampleNumber
                voiceSession.startPendingMarkChoice(
                    type = PendingMarkChoiceType.ALREADY_FOUND,
                    ordinal = decision.ordinal,
                    sampleNumber = decision.sampleNumber
                )
                VoiceExecResult.Message(MarkDecisionVoiceRenderer.render(decision))
            }
            is MarkDecision.Postponed -> {
                voiceSession.lastMarkedRowId = row.id
                voiceSession.lastMarkedSampleNumber = row.sampleNumber
                voiceSession.startPendingMarkChoice(
                    type = PendingMarkChoiceType.POSTPONED,
                    ordinal = decision.ordinal,
                    sampleNumber = decision.sampleNumber
                )
                VoiceExecResult.Message(MarkDecisionVoiceRenderer.render(decision))
            }
            is MarkDecision.ImportError -> {
                VoiceExecResult.Message(MarkDecisionVoiceRenderer.render(decision))
            }
        }
    }

    private fun voiceMarkByNumbers(ordinals: List<Int>): VoiceExecResult {
        val orderId = voiceSession.currentOrderId
            ?: return VoiceExecResult.Message("Сначала найдите скважину")
        val wellNumber = voiceSession.currentWellNumber
            ?: return VoiceExecResult.Message("Сначала найдите скважину")
        val group = state.groupById(orderId.toString())
            ?: return VoiceExecResult.Message("Наряд не загружен")

        val markedNumbers = mutableListOf<String>()
        var lastId: String? = null
        var lastNumber: String? = null

        for (ord in ordinals) {
            val row = group.rows.firstOrNull {
                it.wellNumber == wellNumber && it.numberInWell == ord
            } ?: continue
            if (row.found) continue
            val decision = analyzeMark(toMarkContext(state, row))
            when (decision) {
                is MarkDecision.CanMark -> {
                    setFound(row.id, true)
                    markedNumbers.add(row.sampleNumber)
                    lastId = row.id
                    lastNumber = row.sampleNumber
                }
                is MarkDecision.MarkWithWeight -> {
                    setBlankWeightAndMarkFound(row.id, decision.weight)
                    markedNumbers.add(row.sampleNumber)
                    lastId = row.id
                    lastNumber = row.sampleNumber
                }
                else -> Unit
            }
        }

        voiceSession.lastMarkedRowId = lastId
        voiceSession.lastMarkedSampleNumber = lastNumber

        if (markedNumbers.isEmpty()) {
            return VoiceExecResult.Message("Пробы не найдены или уже отмечены")
        }
        return VoiceExecResult.MarkedMultiple(markedNumbers)
    }

    private fun voiceClearByNumbers(ordinals: List<Int>): VoiceExecResult {
        val orderId = voiceSession.currentOrderId
            ?: return VoiceExecResult.Message("Сначала найдите скважину")
        val wellNumber = voiceSession.currentWellNumber
            ?: return VoiceExecResult.Message("Сначала найдите скважину")
        val group = state.groupById(orderId.toString())
            ?: return VoiceExecResult.Message("Наряд не загружен")

        var count = 0
        for (ord in ordinals) {
            val row = group.rows.firstOrNull {
                it.wellNumber == wellNumber && it.numberInWell == ord
            } ?: continue
            if (row.found) {
                setFound(row.id, false)
                count++
            }
        }

        if (count == 0) return VoiceExecResult.Message("Нечего снимать")
        return VoiceExecResult.Message("Снято отметок: $count")
    }

    private fun voicePostponeOrdinal(ordinal: Int): VoiceExecResult {
        val orderId = voiceSession.currentOrderId
            ?: return VoiceExecResult.Message("Сначала найдите скважину")
        val wellNumber = voiceSession.currentWellNumber
            ?: return VoiceExecResult.Message("Сначала найдите скважину")
        val group = state.groupById(orderId.toString())
            ?: return VoiceExecResult.Message("Наряд не загружен")
        val row = group.rows.firstOrNull {
            it.wellNumber == wellNumber && it.numberInWell == ordinal
        } ?: return VoiceExecResult.Message("Проба №$ordinal не найдена")

        if (row.postponed) return VoiceExecResult.Message("Проба уже отложена")

        setPostponed(row.id, true)

        val spoken = VoiceSpeaker.spellMimicry(row.sampleNumber)
        return VoiceExecResult.Message("Отложена: $spoken")
    }

    private fun voicePostponeByNumbers(ordinals: List<Int>): VoiceExecResult {
        val orderId = voiceSession.currentOrderId
            ?: return VoiceExecResult.Message("Сначала найдите скважину")
        val wellNumber = voiceSession.currentWellNumber
            ?: return VoiceExecResult.Message("Сначала найдите скважину")
        val group = state.groupById(orderId.toString())
            ?: return VoiceExecResult.Message("Наряд не загружен")

        var count = 0
        for (ord in ordinals) {
            val row = group.rows.firstOrNull {
                it.wellNumber == wellNumber && it.numberInWell == ord
            } ?: continue
            if (!row.postponed) {
                setPostponed(row.id, true)
                count++
            }
        }

        if (count == 0) return VoiceExecResult.Message("Нечего откладывать")
        return VoiceExecResult.Message("Отложено проб: $count")
    }

    private fun voiceMarkAll(): VoiceExecResult {
        val orderId = voiceSession.currentOrderId
            ?: return VoiceExecResult.Message("Сначала найдите скважину")
        val wellNumber = voiceSession.currentWellNumber
            ?: return VoiceExecResult.Message("Сначала найдите скважину")
        val group = state.groupById(orderId.toString())
            ?: return VoiceExecResult.Message("Наряд не загружен")

        val wellRows = group.rows.filter { it.wellNumber == wellNumber }
        val toMark = wellRows.filter { !it.found }

        if (toMark.isEmpty()) return VoiceExecResult.Message("Все пробы уже отмечены")

        val queue = buildWeightQueue(toMark)

        var marked = 0
        var lastId: String? = null
        var lastNumber: String? = null

        for (row in toMark) {
            if (row.hasImportError) continue
            val decision = analyzeMark(toMarkContext(state, row))
            when (decision) {
                is MarkDecision.CanMark -> {
                    setFound(row.id, true)
                    marked++
                    lastId = row.id
                    lastNumber = row.sampleNumber
                }
                is MarkDecision.MarkWithWeight -> {
                    setBlankWeightAndMarkFound(row.id, decision.weight)
                    marked++
                    lastId = row.id
                    lastNumber = row.sampleNumber
                }
                else -> Unit
            }
        }

        voiceSession.lastMarkedRowId = lastId
        voiceSession.lastMarkedSampleNumber = lastNumber

        if (queue.isEmpty()) {
            if (marked == 0) return VoiceExecResult.Message("Нечего отмечать")
            return VoiceExecResult.MarkedAll(marked)
        }

        voiceSession.startWeightQueue(queue)
        val first = queue.first()
        return VoiceExecResult.WeightQueueAsked(
            item = first,
            index = 1,
            total = queue.size,
            marked = marked,
            skipped = 0
        )
    }

    private fun voiceSetWeight(value: Double): VoiceExecResult {
        val rowId = voiceSession.lastMarkedRowId
            ?: return VoiceExecResult.Message("Нет активной пробы")
        val row = state.rowById(rowId)
            ?: return VoiceExecResult.Message("Проба потеряна")

        when {
            row.weightControl -> setControlWeightAndFound(rowId, value)
            row.isBlank -> setBlankWeightAndMarkFound(rowId, value)
            else -> setWeight(rowId, value)
        }
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
        }

        if (row == null) {
            val hint = VoiceMarkOrdinalFallback.hintFor(ordinal)
            return VoiceExecResult.MarkOrdinalNotFound(ordinal, hint)
        }

        if (!row.found) {
            val spoken = VoiceSpeaker.spellMimicry(row.sampleNumber)
            return VoiceExecResult.Message("Проба $spoken не отмечена")
        }
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

    private fun voiceChoiceRemove(): VoiceExecResult {
        val pending = voiceSession.pendingMarkChoice
            ?: return VoiceExecResult.Message("Нет ожидаемого выбора")
        val rowId = voiceSession.lastMarkedRowId
        if (rowId == null) { finishPendingChoice(); return VoiceExecResult.Message("Нет активной пробы") }
        val row = state.rowById(rowId)
        if (row == null) { finishPendingChoice(); return VoiceExecResult.Message("Проба потеряна") }
        return when (pending.type) {
            PendingMarkChoiceType.ALREADY_FOUND -> {
                if (!row.found) { finishPendingChoice(); return VoiceExecResult.Message("Проба не отмечена") }
                setFound(row.id, false); finishPendingChoice()
                VoiceExecResult.Unmarked(row.sampleNumber)
            }
            PendingMarkChoiceType.POSTPONED -> {
                if (!row.postponed) { finishPendingChoice(); return VoiceExecResult.Message("Проба не отложена") }
                setPostponed(row.id, false); finishPendingChoice()
                VoiceExecResult.Message("Отложенность снята")
            }
        }
    }

    private fun voiceChoicePostpone(): VoiceExecResult {
        val pending = voiceSession.pendingMarkChoice
            ?: return VoiceExecResult.Message("Нет ожидаемого выбора")
        val rowId = voiceSession.lastMarkedRowId
        if (rowId == null) { finishPendingChoice(); return VoiceExecResult.Message("Нет активной пробы") }
        val row = state.rowById(rowId)
        if (row == null) { finishPendingChoice(); return VoiceExecResult.Message("Проба потеряна") }
        return when (pending.type) {
            PendingMarkChoiceType.ALREADY_FOUND -> {
                if (row.found) setFound(row.id, false)
                setPostponed(row.id, true); finishPendingChoice()
                VoiceExecResult.Message("Отложена.")
            }
            PendingMarkChoiceType.POSTPONED -> {
                if (row.postponed) { finishPendingChoice(); return VoiceExecResult.Message("Проба уже отложена") }
                setPostponed(row.id, true); finishPendingChoice()
                VoiceExecResult.Message("Отложена.")
            }
        }
    }

    private fun voiceChoiceSkip(): VoiceExecResult {
        if (voiceSession.pendingMarkChoice == null) {
            return VoiceExecResult.Message("Нет ожидаемого выбора")
        }
        finishPendingChoice()
        return VoiceExecResult.Message("Пропущено.")
    }

    private fun voiceChoiceMarkCurrent(): VoiceExecResult {
        val pending = voiceSession.pendingMarkChoice
            ?: return VoiceExecResult.Message("Нет ожидаемого выбора")
        val rowId = voiceSession.lastMarkedRowId
        if (rowId == null) { finishPendingChoice(); return VoiceExecResult.Message("Нет активной пробы") }
        val row = state.rowById(rowId)
        if (row == null) { finishPendingChoice(); return VoiceExecResult.Message("Проба потеряна") }
        return when (pending.type) {
            PendingMarkChoiceType.POSTPONED -> {
                if (row.found) { finishPendingChoice(); return VoiceExecResult.Message("Уже отмечена.") }
                setPostponed(row.id, false)
                val updatedRow = state.rowById(row.id) ?: row
                voiceSession.clearPendingMarkChoice()
                voiceSession.awaitingWeight = false
                voiceSession.awaitingContinue = false
                applyMarkDecision(updatedRow)
            }
            PendingMarkChoiceType.ALREADY_FOUND -> {
                finishPendingChoice()
                VoiceExecResult.Message("Уже отмечена.")
            }
        }
    }

    private fun handlePendingChoiceFallback(cmd: VoiceCommand): VoiceExecResult {
        if (cmd is VoiceCommand.Undo) {
            finishPendingChoice()
            return VoiceExecResult.Message("Пропущено.")
        }
        return VoiceExecResult.Message("Скажите: снять, отложить или пропустить.")
    }

    private fun finishPendingChoice() {
        voiceSession.clearPendingMarkChoice()
        voiceSession.awaitingWeight = false
        voiceSession.awaitingContinue = false
        voiceSession.lastMarkedRowId = null
        voiceSession.lastMarkedSampleNumber = null
    }

    /**
     * FIX 5.8.11-sort-fix-5:
     * Единая команда Next. Если есть очередь — идём по ней
     * (voiceNextInQueue). Если нет — полный сброс контекста.
     * mode SORT — команда неприменима.
     */
    private suspend fun voiceNext(): VoiceExecResult {
        if (voiceSession.mode == VoiceSessionMode.SORT) {
            return VoiceExecResult.Message("В режиме сортировки не используется.")
        }
        if (voiceSession.hasQueue) {
            return voiceNextInQueue()
        }
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
        val rows = group.rows.filter { it.wellNumber == wellNumber }.sortedBy { it.numberInWell }
        val leftRows = rows.filter { !it.found }
        if (leftRows.isEmpty()) return VoiceExecResult.Message("Все пробы отмечены.")
        val ordinals = leftRows.mapNotNull { VoiceOrdinals.word(it.numberInWell) }
        val shown = ordinals.take(MAX_LEFT_LIST)
        val tail = if (ordinals.size > MAX_LEFT_LIST)
            " и ещё ${ordinals.size - MAX_LEFT_LIST}" else ""
        val list = shown.joinToString(", ") + tail
        return if (leftRows.size == 1) VoiceExecResult.Message("Осталась одна: $list.")
        else VoiceExecResult.Message("Осталось ${leftRows.size}: $list.")
    }

    private fun voiceShowFilter(filter: ResultFilter, label: String): VoiceExecResult {
        state.activeFilters = state.activeFilters + filter
        return VoiceExecResult.Message("Фильтр: $label")
    }

    private suspend fun voiceSort(queries: List<String>): VoiceExecResult {
        if (voiceSession.mode == VoiceSessionMode.SORT) {
            return voiceSortFlat(queries)
        }

        voiceSession.isAutoMode = false
        voiceSession.awaitingContinue = false

        voiceSession.clearQueue()

        val source = VoiceSearchRepository(getApplication())
        val all = source.loadAll()

        val pinnedList = mutableListOf<PinnedScope>()
        var allUnique = true

        for (q in queries) {
            val clean = q.trim()
            if (clean.isEmpty()) continue

            val candidates: List<String> = if (clean.all { it.isDigit() }) listOf(clean)
            else voiceParser.parse(clean).candidates.map { it.replace("|", "") }
                .filter { it.isNotBlank() }

            if (candidates.isEmpty()) {
                allUnique = false
                break
            }

            when (val r = UnifiedSearch.search(all, candidates, filterMode = false)) {
                is UnifiedSearchResult.Found -> {
                    if (!r.isUnique) {
                        allUnique = false
                        break
                    }
                    val hit = r.hits.first()
                    pinnedList.add(
                        PinnedScope(
                            orderId = hit.orderId,
                            orderTitle = "Наряд №${hit.orderNumber}",
                            areaTitle = hit.areaTitle,
                            wellNumber = hit.wellNumber
                        )
                    )
                }
                UnifiedSearchResult.NotFound -> {
                    allUnique = false
                    break
                }
            }
        }

        if (!allUnique || pinnedList.isEmpty()) {
            return oldSortBehaviour(queries)
        }

        voiceSession.enqueue(pinnedList)

        val first = pinnedList.first()
        voiceSession.currentOrderId = first.orderId
        voiceSession.currentOrderTitle = first.orderTitle
        voiceSession.currentAreaTitle = first.areaTitle
        voiceSession.currentWellNumber = first.wellNumber
        voiceSession.currentSampleNumber = null
        voiceSession.currentSampleOrdinal = null
        voiceSession.currentQuery = first.wellNumber

        ensureOrderSamplesLoaded(first.orderId)

        val group = state.groupById(first.orderId.toString())
        val wellRows = group?.rows?.filter { it.wellNumber == first.wellNumber } ?: emptyList()

        val totalSamples = wellRows.size
        val foundSamples = wellRows.count { it.found }
        val blanks = wellRows.count { it.isBlank }
        val weightControls = wellRows.count { it.weightControl }
        val postponed = wellRows.count { it.postponed }

        withContext(Dispatchers.Main) {
            state.query = first.wellNumber
        }

        val selectedArea = state.selectedArea
        val selectedOrder = state.selectedOrder
        val attentionReason: AnswerReason? = when {
            selectedArea != null && first.areaTitle != selectedArea ->
                AnswerReason.FOUND_OTHER_AREA
            selectedOrder != null && first.orderTitle != selectedOrder ->
                AnswerReason.FOUND_OTHER_ORDER
            else -> null
        }

        return VoiceExecResult.FoundOne(
            query = first.wellNumber,
            orderTitle = first.orderTitle,
            wellNumber = first.wellNumber,
            totalSamples = totalSamples,
            foundSamples = foundSamples,
            isSample = false,
            blanks = blanks,
            weightControls = weightControls,
            postponed = postponed,
            attentionReason = attentionReason,
            otherAreaTitle = first.areaTitle,
            otherOrderNumber = first.orderTitle.removePrefix("Наряд №").trim(),
            groups = emptyList(),
            queueSize = pinnedList.size
        )
    }

    /**
     * FIX 5.8.11-sort-ui:
     * Раньше метод возвращал только Message для TTS, не трогая state.query.
     *
     * FIX 5.8.11-sort-fix-4:
     * Возвращаем Message с display — каноническим номером для UI-поля
     * «Распознано».
     *
     * FIX 5.8.11-sort-fix-5:
     * Формируем text (канонический, для UI-поля «Результат») и spoken
     * (фонетический, для TTS) параллельно. UI теперь видит
     * «Скважина NV1366, Наряд №1.», а ухо слышит «Скважина эн вэ
     * тринадцать шестьдесят шесть, Наряд №1.».
     */
    private suspend fun voiceSortFlat(queries: List<String>): VoiceExecResult {
        voiceSession.isAutoMode = false
        voiceSession.awaitingContinue = false
        voiceSession.clearQueue()
        voiceSession.unpin()

        val source = VoiceSearchRepository(getApplication())
        val all = source.loadAll()

        val descriptionsUi = mutableListOf<String>()
        val descriptionsSpoken = mutableListOf<String>()
        val uiTokens = mutableListOf<String>()

        val selectedArea = state.selectedArea
        val selectedOrder = state.selectedOrder

        for (q in queries) {
            val clean = q.trim()
            if (clean.isEmpty()) continue

            val candidates: List<String> = if (clean.all { it.isDigit() }) listOf(clean)
            else voiceParser.parse(clean).candidates.map { it.replace("|", "") }
                .filter { it.isNotBlank() }

            if (candidates.isEmpty()) {
                descriptionsUi.add("$clean — не найдено")
                descriptionsSpoken.add("${VoiceSpeaker.spellMimicry(clean)} — не найдено")
                uiTokens.add(clean)
                continue
            }

            try {
                when (val r = UnifiedSearch.search(all, candidates, filterMode = false)) {
                    is UnifiedSearchResult.Found -> {
                        val hit = r.hits.first()

                        val subjectUi = when (r.matchedKind) {
                            UnifiedMatchKind.WELL -> "Скважина ${hit.wellNumber}"
                            UnifiedMatchKind.SAMPLE -> "Проба ${hit.sampleNumber}"
                            UnifiedMatchKind.NONE -> hit.wellNumber
                        }
                        val subjectSpoken = when (r.matchedKind) {
                            UnifiedMatchKind.WELL ->
                                "Скважина ${VoiceSpeaker.spellMimicry(hit.wellNumber)}"
                            UnifiedMatchKind.SAMPLE ->
                                "Проба ${VoiceSpeaker.spellMimicry(hit.sampleNumber)}"
                            UnifiedMatchKind.NONE ->
                                VoiceSpeaker.spellMimicry(hit.wellNumber)
                        }

                        val orderSpoken = VoiceSpeaker.spellNumber(
                            hit.orderNumber.toIntOrNull() ?: 0
                        )

                        val conflict = when {
                            selectedArea != null && hit.areaTitle != selectedArea ->
                                "другой участок — $selectedArea"
                            selectedOrder != null &&
                                    "Наряд №${hit.orderNumber}" != selectedOrder ->
                                "другой наряд"
                            else -> null
                        }

                        when {
                            !r.isUnique -> {
                                descriptionsUi.add("$subjectUi — найден в нескольких нарядах")
                                descriptionsSpoken.add(
                                    "$subjectSpoken — найден в нескольких нарядах"
                                )
                                uiTokens.add(clean)
                            }
                            conflict != null -> {
                                descriptionsUi.add("$subjectUi — $conflict")
                                descriptionsSpoken.add("$subjectSpoken — $conflict")
                                uiTokens.add(clean)
                            }
                            else -> {
                                descriptionsUi.add("$subjectUi, Наряд №${hit.orderNumber}")
                                descriptionsSpoken.add("$subjectSpoken, Наряд №$orderSpoken")
                                val canonical = when (r.matchedKind) {
                                    UnifiedMatchKind.WELL -> hit.wellNumber
                                    UnifiedMatchKind.SAMPLE -> hit.sampleNumber
                                    UnifiedMatchKind.NONE -> hit.wellNumber
                                }
                                uiTokens.add(canonical)
                            }
                        }
                    }
                    UnifiedSearchResult.NotFound -> {
                        descriptionsUi.add("$clean — не найдено")
                        descriptionsSpoken.add(
                            "${VoiceSpeaker.spellMimicry(clean)} — не найдено"
                        )
                        uiTokens.add(clean)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "voiceSortFlat: проверка «$clean» упала", e)
                descriptionsUi.add("$clean — ошибка")
                descriptionsSpoken.add("${VoiceSpeaker.spellMimicry(clean)} — ошибка")
                uiTokens.add(clean)
            }
        }

        val uiQuery = uiTokens.joinToString(" ").ifEmpty { null }
        if (uiQuery != null) {
            setQuery(uiQuery)
        }

        val textUi = if (descriptionsUi.isEmpty()) "Не понял."
        else descriptionsUi.joinToString(". ") + "."
        val textSpoken = if (descriptionsSpoken.isEmpty()) "Не понял."
        else descriptionsSpoken.joinToString(". ") + "."

        return VoiceExecResult.Message(
            text = textUi,
            spoken = textSpoken,
            display = uiQuery
        )
    }

    private suspend fun oldSortBehaviour(queries: List<String>): VoiceExecResult {
        val source = VoiceSearchRepository(getApplication())
        val all = source.loadAll()

        val descriptions = mutableListOf<String>()
        val ambiguousQueries = mutableListOf<String>()

        for (q in queries) {
            val clean = q.trim()
            if (clean.isEmpty()) continue

            val candidates: List<String> = if (clean.all { it.isDigit() }) listOf(clean)
            else voiceParser.parse(clean).candidates.map { it.replace("|", "") }
                .filter { it.isNotBlank() }

            if (candidates.isEmpty()) {
                descriptions.add("${VoiceSpeaker.spellMimicry(clean)} — не найдено")
                continue
            }

            try {
                when (val r = UnifiedSearch.search(all, candidates, filterMode = false)) {
                    is UnifiedSearchResult.Found -> {
                        val hit = r.hits.first()
                        val subject = when (r.matchedKind) {
                            UnifiedMatchKind.WELL ->
                                "Скважина ${VoiceSpeaker.spellMimicry(hit.wellNumber)}"
                            UnifiedMatchKind.SAMPLE ->
                                "Проба ${VoiceSpeaker.spellMimicry(hit.sampleNumber)}"
                            UnifiedMatchKind.NONE ->
                                VoiceSpeaker.spellMimicry(hit.wellNumber)
                        }
                        val orderSpoken = VoiceSpeaker.spellNumber(hit.orderNumber.toIntOrNull() ?: 0)
                        if (r.isUnique) descriptions.add("$subject, Наряд №$orderSpoken")
                        else {
                            ambiguousQueries.add(clean)
                            descriptions.add("$subject — найден в нескольких нарядах")
                        }
                    }
                    UnifiedSearchResult.NotFound -> {
                        descriptions.add("${VoiceSpeaker.spellMimicry(clean)} — не найдено")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "voiceSort: проверка «$clean» упала", e)
                descriptions.add("${VoiceSpeaker.spellMimicry(clean)} — ошибка")
            }
        }

        val hasAmbiguous = ambiguousQueries.isNotEmpty()
        val text = if (hasAmbiguous) {
            voiceSession.awaitingContinue = true
            descriptions.joinToString(". ") + ". Выберите на экране."
        } else descriptions.joinToString(". ") + "."
        return VoiceExecResult.Message(text)
    }

    private fun isLikelyVoiceSearchQuery(query: String): Boolean {
        val norm = query.lowercase().replace('ё', 'е')
            .trim('.', ',', '!', '?', ';', ':')
        if (norm.isEmpty()) return false
        if (norm.split(Regex("\\s+")).any { it in voiceCommandLikeWords }) return false
        if (norm.any { it.isDigit() }) return true
        if (norm.any { it.isLetter() && it.code < 128 }) return true
        return voiceParser.parse(norm).candidates.isNotEmpty()
    }

    private fun rowById(rowId: String): SampleRow? = state.rowById(rowId)

    fun toggleFound(rowId: String) {
        state.toggleFound(rowId)
        val id = rowId.toLongOrNull() ?: return
        val found = rowById(rowId)?.found ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.setFound(id, found) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setFound(rowId: String, value: Boolean) {
        state.setFound(rowId, value)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.setFound(id, value) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
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
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setControlWeight(rowId: String, weight: Double) {
        state.setControlWeight(rowId, weight)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { repo.setControlWeight(id, weight) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
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
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setWeight(rowId: String, weight: Double) {
        state.setWeight(rowId, weight)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { repo.setWeight(id, weight) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun setPostponed(rowId: String, value: Boolean) {
        state.setPostponed(rowId, value)
        val id = rowId.toLongOrNull() ?: return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { repo.setPostponed(id, value) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
        }
    }

    fun toggleWeightControl(rowId: String): Boolean {
        val ok = state.toggleWeightControl(rowId)
        if (ok) {
            val id = rowId.toLongOrNull() ?: return ok
            val flag = rowById(rowId)?.weightControl ?: return ok
            viewModelScope.launch {
                try { withContext(Dispatchers.IO) { repo.setWeightControl(id, flag) }
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) { _message.value = "Ошибка: ${e.message}" }
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
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _message.value = "Ошибка удаления: ${e.message}" }
        }
    }

    fun undo() { state.undo(); persistAll() }
    fun redo() { state.redo(); persistAll() }

    fun applyBlankSettingsForOrder(orderTitle: String, settings: BlankWeightSettings): Int {
        val changed = state.applyBlankSettingsForOrder(orderTitle, settings)
        persistOrder(orderTitle)
        return changed
    }

    fun applyWeightControlForOrder(orderTitle: String, weightControlStep: Int): Int {
        val changed = state.applyWeightControlForOrder(orderTitle, weightControlStep)
        persistOrder(orderTitle)
        return changed
    }

    fun resetWeightControlForOrder(orderTitle: String): Int {
        val changed = state.resetWeightControlForOrder(orderTitle)
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

    suspend fun loadNoteWithPhotos(sampleId: Long): Pair<SampleNoteEntity?, List<SampleImageEntity>> {
        return try {
            withContext(Dispatchers.IO) { repo.getNoteWithPhotos(sampleId) }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            _message.value = "Ошибка загрузки заметки: ${e.message}"
            null to emptyList()
        }
    }

    suspend fun saveNoteText(sampleId: Long, text: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val trimmed = text.trim()
                if (trimmed.isEmpty()) repo.deleteNote(sampleId)
                else {
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
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            _message.value = "Ошибка сохранения заметки: ${e.message}"
            false
        }
    }

    suspend fun addPhoto(sampleId: Long, sourceUri: Uri): Boolean {
        return try {
            val ctx = getApplication<Application>()
            val path = withContext(Dispatchers.IO) { PhotoStorage.compressAndSave(ctx, sourceUri) }
            if (path == null) { _message.value = "Не удалось обработать фото"; return false }
            withContext(Dispatchers.IO) {
                repo.addPhoto(sampleId, path)
                refreshSampleFlagsInternal(sampleId)
            }
            true
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            _message.value = "Ошибка добавления фото: ${e.message}"
            false
        }
    }

    suspend fun addPhotoFromFile(sampleId: Long, tempFile: File): Boolean {
        return try {
            val ctx = getApplication<Application>()
            val path = withContext(Dispatchers.IO) { PhotoStorage.compressAndSaveFromFile(ctx, tempFile) }
            if (path == null) { _message.value = "Не удалось обработать фото"; return false }
            withContext(Dispatchers.IO) {
                repo.addPhoto(sampleId, path)
                refreshSampleFlagsInternal(sampleId)
            }
            true
        } catch (e: CancellationException) { throw e
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
        } catch (e: CancellationException) { throw e
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

    private fun persistGroup(groupId: String) {
        val group = state.groups.firstOrNull { it.id == groupId } ?: return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { repo.saveRows(group.rows) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _message.value = "Ошибка сохранения: ${e.message}" }
        }
    }

    private fun persistOrder(orderTitle: String) {
        val groups = state.groups.filter { it.orderTitle == orderTitle }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { groups.forEach { repo.saveRows(it.rows) } }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _message.value = "Ошибка сохранения: ${e.message}" }
        }
    }

    private fun persistAll() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { state.groups.forEach { repo.saveRows(it.rows) } }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _message.value = "Ошибка сохранения: ${e.message}" }
        }
    }
}