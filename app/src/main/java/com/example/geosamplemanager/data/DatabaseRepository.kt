package com.example.geosamplemanager.data

import android.content.Context
import androidx.room.withTransaction
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.OrderWellEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import com.example.geosamplemanager.data.entity.SampleNoteEntity
import com.example.geosamplemanager.ui.screens.SampleRow
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
    suspend fun setWeight(sampleId: Long, weight: Double?) =
        sampleDao.setWeight(sampleId, weight)
    suspend fun setWeightControl(sampleId: Long, flag: Boolean) =
        sampleDao.setWeightControl(sampleId, flag)
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
    suspend fun deleteWellsForOrder(orderId: Long) = orderWellDao.deleteAllForOrder(orderId)

    // ============ ЗАМЕТКИ ============

    suspend fun getNote(sampleId: Long) = sampleNoteDao.getNote(sampleId)
    suspend fun upsertNote(note: SampleNoteEntity): Long = sampleNoteDao.insert(note)
    suspend fun deleteNote(sampleId: Long) = sampleNoteDao.deleteBySampleId(sampleId)

    // ============ ФАЙЛ БД ============

    fun getDatabaseFile(): File = appContext.getDatabasePath("geosamples.db")

    // ============ ОЧИСТКА НАРЯДА ============

    suspend fun clearOrder(orderId: Long) {
        sampleDao.deleteAllForOrder(orderId)
        orderWellDao.deleteAllForOrder(orderId)
    }

    suspend fun getExistingOrderStats(areaName: String, orderNumber: String): OrderStats? {
        val areaId = areaDao.getAreaId(areaName) ?: return null
        val orderId = orderDao.getOrderId(areaId, orderNumber) ?: return null
        return getOrderStats(orderId)
    }

    // ================================================================
    // ДЛЯ ЭКРАНА СВЕРКИ — атомарные UPDATE без чтения
    // ================================================================

    /**
     * Устанавливает статус пробы (normal / blank / control).
     */
    suspend fun setSampleStatus(sampleId: Long, status: String) {
        sampleDao.setStatus(sampleId, status)
    }

    /**
     * Устанавливает флаг hasNote.
     */
    suspend fun setHasNote(sampleId: Long, hasNote: Boolean) {
        sampleDao.setHasNote(sampleId, hasNote)
    }

    /**
     * Сохраняет пачку строк одной транзакцией через батч-Update.
     * Используется для undo/redo и массовых операций.
     *
     * ВАЖНО: перед вызовом строки уже должны существовать в БД.
     */
    suspend fun saveRows(rows: List<SampleRow>) {
        if (rows.isEmpty()) return
        db.withTransaction {
            val entities = rows.mapNotNull { row ->
                val id = row.id.toLongOrNull() ?: return@mapNotNull null
                val s = sampleDao.getSampleById(id) ?: return@mapNotNull null
                s.copy(
                    sampleNumber = row.sampleNumber,
                    wellNumber = row.wellNumber,
                    intervalFrom = row.intervalFrom.toDoubleOrNull(),
                    intervalTo = row.intervalTo.toDoubleOrNull(),
                    weight = row.weight,
                    controlWeight = row.controlWeight,
                    sampleType = row.type.dbCode,
                    status = row.status.dbCode,
                    materialDesc = row.characteristic.takeIf { it != "—" },
                    found = row.found,
                    postponed = row.postponed,
                    weightControl = row.weightControl,
                    hasNote = row.hasNote
                )
            }
            if (entities.isNotEmpty()) {
                sampleDao.updateAll(entities)
            }
        }
    }

    /**
     * Удаление пробы с опциональным пересчётом sample_number и serial_number
     * у всех последующих в этой же скважине (в этом же наряде).
     *
     * Вся работа — в одной транзакции.
     */
    suspend fun deleteSampleWithRenumber(sampleId: Long, recalc: Boolean) {
        db.withTransaction {
            val target = sampleDao.getSampleById(sampleId) ?: return@withTransaction

            sampleDao.deleteById(sampleId)

            if (!recalc) return@withTransaction

            val remaining = sampleDao
                .getSamplesForOrderList(target.orderId)
                .filter { it.wellNumber == target.wellNumber }
                .sortedBy { it.serialNumber }

            if (remaining.isEmpty()) return@withTransaction

            val first = remaining.first()
            val suffixLen = detectSuffixLength(first.sampleNumber, first.wellNumber)

            remaining.forEachIndexed { index, sample ->
                val newOrderNum = index + 1
                val newSampleNumber = sample.wellNumber +
                        newOrderNum.toString().padStart(suffixLen, '0')

                sampleDao.update(
                    sample.copy(
                        sampleNumber = newSampleNumber,
                        serialNumber = newOrderNum
                    )
                )
            }
        }
    }

    private fun detectSuffixLength(sampleNumber: String, wellNumber: String): Int {
        if (wellNumber.isNotEmpty() && sampleNumber.startsWith(wellNumber)) {
            val len = sampleNumber.length - wellNumber.length
            if (len > 0) return len
        }
        var i = sampleNumber.length
        while (i > 0 && sampleNumber[i - 1].isDigit()) i--
        return (sampleNumber.length - i).coerceAtLeast(1)
    }
}

data class OrderStats(val total: Int, val found: Int)