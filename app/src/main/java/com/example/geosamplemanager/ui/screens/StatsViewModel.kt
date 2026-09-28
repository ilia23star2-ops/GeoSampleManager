package com.example.geosamplemanager.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geosamplemanager.GeoSampleApp
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * FIX 5.9-stats-screen: ViewModel экрана «Статистика».
 *
 * FIX 5.9-stats-reactive:
 * Room-Flow: getAreasFlow + getAllOrdersFlow + getAllSamplesFlow.
 *
 * FIX 5.9-stats-layout:
 * Добавлен selectedOrderId — выбранный наряд для правой панели
 * (master-detail). Если выбранный наряд исчез из БД — сбрасываем.
 */
class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as GeoSampleApp).repository

    /** Сырое дерево без фильтра. Кэш для быстрой смены фильтров. */
    private var rawData: StatsData? = null

    private val _data = MutableStateFlow<StatsData?>(null)
    val data: StateFlow<StatsData?> = _data.asStateFlow()

    private val _expandedAreaIds = MutableStateFlow<Set<Long>>(emptySet())
    val expandedAreaIds: StateFlow<Set<Long>> = _expandedAreaIds.asStateFlow()

    private val _expandedOrderIds = MutableStateFlow<Set<Long>>(emptySet())
    val expandedOrderIds: StateFlow<Set<Long>> = _expandedOrderIds.asStateFlow()

    /**
     * FIX 5.9-stats-layout: выбранный наряд для правой панели.
     * null = ничего не выбрано (правая панель показывает подсказку).
     */
    private val _selectedOrderId = MutableStateFlow<Long?>(null)
    val selectedOrderId: StateFlow<Long?> = _selectedOrderId.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

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
                buildTree(areas, orders, samples)
            }.collect { raw ->
                rawData = raw
                val filter = _data.value?.filter ?: StatsFilter.ALL
                _data.value = raw.withFilter(filter)
                pruneExpandedIds(raw)
                pruneSelected(raw)
            }
        }
    }

    private fun pruneExpandedIds(raw: StatsData) {
        val areaIds = raw.areas.map { it.areaId }.toSet()
        val orderIds = raw.areas.flatMap { it.orders.map { o -> o.orderId } }.toSet()
        _expandedAreaIds.value = _expandedAreaIds.value intersect areaIds
        _expandedOrderIds.value = _expandedOrderIds.value intersect orderIds
    }

    /**
     * FIX 5.9-stats-layout: если выбранный наряд удалён из БД —
     * сбрасываем выбор, чтобы правая панель не показывала пустоту.
     */
    private fun pruneSelected(raw: StatsData) {
        val sel = _selectedOrderId.value ?: return
        val exists = raw.areas.any { area -> area.orders.any { it.orderId == sel } }
        if (!exists) _selectedOrderId.value = null
    }

    fun setFilter(filter: StatsFilter) {
        val raw = rawData ?: return
        _data.value = raw.withFilter(filter)
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

    /**
     * FIX 5.9-stats-layout: выбрать наряд (для правой панели).
     * Одновременно раскрывает его в дереве.
     */
    fun selectOrder(orderId: Long) {
        _selectedOrderId.value = orderId
        _expandedAreaIds.value = _expandedAreaIds.value // без изменений
        val area = _data.value?.areas?.firstOrNull { a ->
            a.orders.any { it.orderId == orderId }
        } ?: return
        // Раскрываем родительский участок.
        _expandedAreaIds.value = _expandedAreaIds.value + area.areaId
        // Раскрываем сам наряд.
        _expandedOrderIds.value = _expandedOrderIds.value + orderId
    }

    fun clearSelection() {
        _selectedOrderId.value = null
    }

    /**
     * FIX 5.9-stats-layout: найти StatsOrderUi по id (для правой панели).
     * Используем актуальные данные (с учётом фильтра).
     */
    fun findOrder(orderId: Long): StatsOrderUi? {
        return _data.value?.areas?.asSequence()
            ?.flatMap { it.orders.asSequence() }
            ?.firstOrNull { it.orderId == orderId }
    }

    /**
     * FIX 5.9-stats-layout: найти имя участка для наряда.
     */
    fun findAreaNameFor(orderId: Long): String? {
        return _data.value?.areas?.firstOrNull { area ->
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
            }.sortedBy { it.orderNumber }

            StatsAreaUi(
                areaId = area.id,
                areaName = area.areaName,
                stats = combineStats(orderUis.map { it.stats }),
                orders = orderUis
            )
        }.sortedBy { it.areaName }

        val totals = combineStats(areaUis.map { it.stats })
        return StatsData(totals = totals, areas = areaUis, filter = StatsFilter.ALL)
    }
}
