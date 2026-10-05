package com.example.geosamplemanager.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geosamplemanager.GeoSampleApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FIX 5.9-main-a:
 * ViewModel вкладки «Главная».
 *
 * Сводка по базе, блок «Продолжить работу» (последний наряд
 * из SessionState), список незавершённых нарядов, проблемы БД
 * (лёгкая проверка — сироты).
 *
 * Полная диагностика (фото, ссылки) — по кнопке в БД.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GeoSampleApp
    private val repo = app.repository
    private val sessionRepo = app.sessionStateRepository

    private val _info = MutableStateFlow<MainInfo?>(null)
    val info: StateFlow<MainInfo?> = _info.asStateFlow()

    private val _unfinished = MutableStateFlow<List<UnfinishedOrder>>(emptyList())
    val unfinished: StateFlow<List<UnfinishedOrder>> = _unfinished.asStateFlow()

    private val _continueInfo = MutableStateFlow<ContinueInfo?>(null)
    val continueInfo: StateFlow<ContinueInfo?> = _continueInfo.asStateFlow()

    private val _problems = MutableStateFlow<DbProblems?>(null)
    val problems: StateFlow<DbProblems?> = _problems.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    fun reload() {
        if (_loading.value) return
        _loading.value = true

        viewModelScope.launch {
            try {
                loadAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = "Ошибка загрузки: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun loadAll() {
        val dbInfo = withContext(Dispatchers.IO) { repo.getDbInfo() }
        val foundTotal = withContext(Dispatchers.IO) { repo.getFoundCount() }

        val allOrders = withContext(Dispatchers.IO) { repo.getAllOrders() }
        val allAreas = withContext(Dispatchers.IO) { repo.getAreas() }
        val areaNameById = allAreas.associate { it.id to it.areaName }

        val orderIds = allOrders.map { it.id }
        val allSamples = if (orderIds.isEmpty()) emptyList()
        else withContext(Dispatchers.IO) { repo.getSamplesForOrders(orderIds) }
        val samplesByOrder = allSamples.groupBy { it.orderId }

        val unfinishedList = mutableListOf<UnfinishedOrder>()
        var readyCount = 0

        allOrders.forEach { order ->
            val samples = samplesByOrder[order.id].orEmpty()
            val total = samples.size
            val found = samples.count { it.found }
            if (total == 0) return@forEach  // пустой наряд — не показываем
            if (found == total) {
                readyCount++
            } else {
                unfinishedList.add(
                    UnfinishedOrder(
                        orderId = order.id,
                        orderTitle = "Наряд №${order.orderNumber}",
                        areaTitle = areaNameById[order.areaId] ?: "—",
                        total = total,
                        found = found
                    )
                )
            }
        }

        // Сортировка: по % (сначала близкие к готовности), потом по названию.
        val sorted = unfinishedList.sortedWith(
            compareByDescending<UnfinishedOrder> { it.percent }
                .thenBy { it.orderTitle }
        )

        // Continue
        val session = sessionRepo.load()
        val continueInfo = session.lastOrderId?.let { id ->
            allOrders.firstOrNull { it.id == id }?.let { order ->
                val samples = samplesByOrder[order.id].orEmpty()
                val total = samples.size
                val found = samples.count { it.found }
                if (total == 0) null
                else ContinueInfo(
                    orderId = order.id,
                    orderTitle = "Наряд №${order.orderNumber}",
                    areaTitle = areaNameById[order.areaId] ?: "—",
                    total = total,
                    found = found,
                    lastActionAt = session.lastActionAt
                )
            }
        }

        // Лёгкая проверка сирот
        val orphanOrders = withContext(Dispatchers.IO) { repo.countOrphanOrders() }
        val orphanSamples = withContext(Dispatchers.IO) { repo.countOrphanSamples() }
        val problems = if (orphanOrders > 0 || orphanSamples > 0) {
            DbProblems(
                orphanOrders = orphanOrders,
                orphanSamples = orphanSamples,
                total = orphanOrders + orphanSamples
            )
        } else null

        val freeBytes = try {
            getApplication<Application>().filesDir.usableSpace
        } catch (_: Exception) { 0L }

        _info.value = MainInfo(
            areas = dbInfo.areasCount,
            orders = dbInfo.ordersCount,
            wells = dbInfo.wellsCount,
            samples = dbInfo.samplesCount,
            found = foundTotal,
            photos = dbInfo.photosCount,
            notes = dbInfo.notesCount,
            readyOrders = readyCount,
            dbSizeBytes = dbInfo.dbSizeBytes,
            lastModified = dbInfo.lastModified,
            freeBytes = freeBytes
        )
        _unfinished.value = sorted
        _continueInfo.value = continueInfo
        _problems.value = problems
    }
}

/**
 * FIX 5.9-main-a:
 * Сводка для Главной. Чистая модель — тестируется в JVM.
 */
data class MainInfo(
    val areas: Int,
    val orders: Int,
    val wells: Int,
    val samples: Int,
    val found: Int,
    val photos: Int,
    val notes: Int,
    val readyOrders: Int,
    val dbSizeBytes: Long,
    val lastModified: Long,
    val freeBytes: Long
) {
    val notFound: Int get() = (samples - found).coerceAtLeast(0)

    val inProgressOrders: Int
        get() = (orders - readyOrders).coerceAtLeast(0)

    val progressPercent: Int
        get() = if (samples <= 0) 0 else (found * 100 / samples).coerceIn(0, 100)

    val progressFraction: Float
        get() = if (samples <= 0) 0f else (found.toFloat() / samples).coerceIn(0f, 1f)

    val isEmpty: Boolean get() = areas == 0 && orders == 0 && samples == 0
}

/**
 * FIX 5.9-main-a:
 * Один незавершённый наряд для списка на Главной.
 */
data class UnfinishedOrder(
    val orderId: Long,
    val orderTitle: String,
    val areaTitle: String,
    val total: Int,
    val found: Int
) {
    val percent: Int
        get() = if (total <= 0) 0 else (found * 100 / total).coerceIn(0, 100)

    val progressFraction: Float
        get() = if (total <= 0) 0f else (found.toFloat() / total).coerceIn(0f, 1f)
}

/**
 * FIX 5.9-main-a:
 * Данные блока «Продолжить работу».
 */
data class ContinueInfo(
    val orderId: Long,
    val orderTitle: String,
    val areaTitle: String,
    val total: Int,
    val found: Int,
    val lastActionAt: Long
) {
    val percent: Int
        get() = if (total <= 0) 0 else (found * 100 / total).coerceIn(0, 100)

    val progressFraction: Float
        get() = if (total <= 0) 0f else (found.toFloat() / total).coerceIn(0f, 1f)
}

/**
 * FIX 5.9-main-a:
 * Проблемы БД. Лёгкая проверка — только SQL-сироты.
 * Полная — по кнопке в БД.
 */
data class DbProblems(
    val orphanOrders: Int,
    val orphanSamples: Int,
    val total: Int
)