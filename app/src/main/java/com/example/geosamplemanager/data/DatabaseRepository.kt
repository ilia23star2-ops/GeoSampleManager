package com.example.geosamplemanager.data

import android.content.Context
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.OrderWellEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import kotlinx.coroutines.flow.Flow
import java.io.File

class DatabaseRepository(context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val areaDao = db.areaDao()
    private val orderDao = db.orderDao()
    private val sampleDao = db.sampleDao()
    private val orderWellDao = db.orderWellDao()
    private val sampleNoteDao = db.sampleNoteDao()

    // ============ УЧАСТКИ ============

    fun getAreasFlow(): Flow<List<AreaEntity>> = areaDao.getAllAreas()
    suspend fun getAreas(): List<AreaEntity> = areaDao.getAreasList()
    suspend fun getAreaId(name: String): Long? = areaDao.getAreaId(name)
    suspend fun addArea(name: String): Long = areaDao.insert(AreaEntity(areaName = name))
    suspend fun deleteArea(name: String) = areaDao.deleteByName(name)
    suspend fun deleteAreaById(areaId: Long) = areaDao.deleteById(areaId)

    // ============ НАРЯДЫ ============

    fun getOrdersForArea(areaId: Long): Flow<List<OrderEntity>> =
        orderDao.getOrdersForAreaId(areaId)
    suspend fun getOrdersForAreaList(areaId: Long): List<OrderEntity> =
        orderDao.getOrdersForAreaIdList(areaId)
    suspend fun getOrderId(areaId: Long, orderNumber: String): Long? =
        orderDao.getOrderId(areaId, orderNumber)
    suspend fun addOrder(areaId: Long, orderNumber: String): Long =
        orderDao.insert(OrderEntity(areaId = areaId, orderNumber = orderNumber))
    suspend fun deleteOrder(orderId: Long) = orderDao.deleteById(orderId)
    suspend fun getAllOrders(): List<OrderEntity> = orderDao.getAllOrders()

    // ============ ПРОБЫ ============

    fun getSamplesForOrder(orderId: Long): Flow<List<SampleEntity>> =
        sampleDao.getSamplesForOrder(orderId)
    suspend fun getSamplesForOrderList(orderId: Long): List<SampleEntity> =
        sampleDao.getSamplesForOrderList(orderId)
    suspend fun getSampleById(sampleId: Long): SampleEntity? =
        sampleDao.getSampleById(sampleId)
    suspend fun getSamplesByWell(wellNumber: String): List<SampleEntity> =
        sampleDao.getSamplesByWell(wellNumber)
    suspend fun addSample(sample: SampleEntity): Long = sampleDao.insert(sample)
    suspend fun updateSample(sample: SampleEntity) = sampleDao.update(sample)
    suspend fun deleteSample(sampleId: Long) = sampleDao.deleteById(sampleId)
    suspend fun toggleFound(sampleId: Long) = sampleDao.toggleFound(sampleId)
    suspend fun setFound(sampleId: Long, found: Boolean) = sampleDao.setFound(sampleId, found)
    suspend fun setPostponed(sampleId: Long, postponed: Boolean) =
        sampleDao.setPostponed(sampleId, postponed)
    suspend fun setControlWeight(sampleId: Long, weight: Double?) =
        sampleDao.setControlWeight(sampleId, weight)
    suspend fun searchSamples(query: String?): List<SampleEntity> =
        sampleDao.searchSamples(query)

    // ============ СТАТИСТИКА ============

    suspend fun getTotalCount(): Int = sampleDao.getTotalCount()
    suspend fun getFoundCount(): Int = sampleDao.getFoundCount()
    suspend fun getBlankCount(): Int = sampleDao.getBlankCount()
    suspend fun getControlCount(): Int = sampleDao.getControlCount()

    suspend fun getOrderStats(orderId: Long): OrderStats {
        val total = sampleDao.getCountForOrder(orderId)
        val found = sampleDao.getFoundCountForOrder(orderId)
        return OrderStats(total = total, found = found)
    }

    // ============ СКВАЖИНЫ ============

    suspend fun getWellsForOrder(orderId: Long): List<String> =
        orderWellDao.getWellsForOrder(orderId)
    suspend fun addWell(orderId: Long, wellNumber: String): Long =
        orderWellDao.insert(OrderWellEntity(orderId = orderId, wellNumber = wellNumber))

    // ============ ЗАМЕТКИ ============

    suspend fun getNote(sampleId: Long) = sampleNoteDao.getNote(sampleId)
    suspend fun deleteNote(sampleId: Long) = sampleNoteDao.deleteBySampleId(sampleId)

    // ============ ФАЙЛ БД ============

    fun getDatabaseFile(): File = appContext.getDatabasePath("geosamples.db")

    // ============ ОЧИСТКА НАРЯДА ============

    /** Удалить все пробы и скважины наряда. Сам наряд остаётся. */
    suspend fun clearOrder(orderId: Long) {
        sampleDao.deleteAllForOrder(orderId)
        orderWellDao.deleteAllForOrder(orderId)
    }

    /**
     * Проверить существование наряда и вернуть его статистику.
     * @return null если наряда нет; иначе OrderStats (total проб в наряде).
     */
    suspend fun getExistingOrderStats(areaName: String, orderNumber: String): OrderStats? {
        val areaId = areaDao.getAreaId(areaName) ?: return null
        val orderId = orderDao.getOrderId(areaId, orderNumber) ?: return null
        return getOrderStats(orderId)
    }
}

data class OrderStats(val total: Int, val found: Int)