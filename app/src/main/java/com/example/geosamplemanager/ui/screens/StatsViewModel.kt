package com.example.geosamplemanager.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geosamplemanager.GeoSampleApp
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FIX 5.9-stats-search-2: дебаунс 250 мс + фон.
 *
 * FIX 5.9-stats-order-status:
 *  - hideReady flag — скрыть готовые наряды.
 *  - Сортировка нарядов по статусу в buildTree.
 */
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

        if (search.isNotBlank()) {
            expandAll()
        }
    }

    private fun pruneExpandedIds(raw: StatsData) {
        val areaIds = raw.areas.map { it.areaId }.toSet()
        val orderIds = raw.areas.flatMap { it.orders.map { o -> o.orderId } }.toSet()
        _expandedAreaIds.value = _expandedAreaIds.value intersect areaIds
        _expandedOrderIds.value = _expandedOrderIds.value intersect orderIds
    }

    private fun pruneSelected(raw: StatsData) {
        val sel = _selectedOrderId.value ?: return
        val exists = raw.areas.any { area -> area.orders.any { it.orderId == sel } }
        if (!exists) _selectedOrderId.value = null
    }

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
        val area = _data.value?.areas?.firstOrNull { a ->
            a.orders.any { it.orderId == orderId }
        } ?: return
        _expandedAreaIds.value = _expandedAreaIds.value + area.areaId
        _expandedOrderIds.value = _expandedOrderIds.value + orderId
    }

    fun clearSelection() {
        _selectedOrderId.value = null
    }

    fun findOrder(orderId: Long): StatsOrderUi? {
        return rawData?.areas?.asSequence()
            ?.flatMap { it.orders.asSequence() }
            ?.firstOrNull { it.orderId == orderId }
    }

    fun findAreaNameFor(orderId: Long): String? {
        return rawData?.areas?.firstOrNull { area ->
            area.orders.any { it.orderId == orderId }
        }?.areaName
    }

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
        private const val SEARCH_DEBOUNCE_MS = 250L
    }
}
