package com.example.geosamplemanager.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geosamplemanager.GeoSampleApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FIX 5.9-stats-screen:
 * ViewModel экрана «Статистика».
 *
 * Грузит дерево участок → наряд → проба из БД.
 * Держит текущий фильтр и множества раскрытых id.
 * Дерево кэшируется в rawData — при смене фильтра не перезагружаем БД.
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

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            try {
                val raw = withContext(Dispatchers.IO) { loadTree() }
                rawData = raw
                val filter = _data.value?.filter ?: StatsFilter.ALL
                _data.value = raw.withFilter(filter)
            } catch (e: Exception) {
                _message.value = "Ошибка загрузки: ${e.message}"
            }
        }
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

    private suspend fun loadTree(): StatsData {
        val areas = repo.getAreas()
        val areaUis = areas.map { area ->
            val orders = repo.getOrdersForAreaList(area.id)
            val orderUis = orders.map { order ->
                val samples = repo.getSamplesForOrderList(order.id)
                val group = buildSampleGroup(order, area, samples)
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
