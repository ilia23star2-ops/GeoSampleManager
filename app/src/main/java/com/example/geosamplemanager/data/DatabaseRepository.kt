package com.example.geosamplemanager.data

import android.content.Context
import androidx.room.withTransaction
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.OrderWellEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import com.example.geosamplemanager.data.entity.SampleImageEntity
import com.example.geosamplemanager.data.entity.SampleNoteEntity
import com.example.geosamplemanager.data.util.PhotoStorage
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
    private val sampleImageDao = db.sampleImageDao()

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

    /** Flow всех нарядов. Для реактивного обновления списка в сверке. */
    fun getAllOrdersFlow(): Flow<List<OrderEntity>> = orderDao.getAllOrdersFlow()

    // ============ ПРОБЫ ============

    fun getSamplesForOrder(orderId: Long): Flow<List<SampleEntity>> =
        sampleDao.getSamplesForOrder(orderId)

    suspend fun getSamplesForOrderList(orderId: Long): List<SampleEntity> =
        sampleDao.getSamplesForOrderList(orderId)

    suspend fun getSamplesForOrders(orderIds: List<Long>): List<SampleEntity> =
        if (orderIds.isEmpty()) emptyList()
        else sampleDao.getSamplesForOrders(orderIds)

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

    suspend fun findOrderIdsByQuery(query: String): List<Long> =
        sampleDao.findOrderIdsByQuery(query)

    suspend fun getOrderIdsWithSamples(): List<Long> =
        sampleDao.getOrderIdsWithSamples()

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

    suspend fun getNote(sampleId: Long): SampleNoteEntity? = sampleNoteDao.getNote(sampleId)
    suspend fun upsertNote(note: SampleNoteEntity): Long = sampleNoteDao.insert(note)
    suspend fun deleteNote(sampleId: Long) = sampleNoteDao.deleteBySampleId(sampleId)

    // ============ ФОТО ============

    suspend fun getPhotosForSample(sampleId: Long): List<SampleImageEntity> =
        sampleImageDao.getPhotosForSample(sampleId)

    suspend fun getImagePathsForSample(sampleId: Long): List<String> =
        sampleImageDao.getImagePathsForSample(sampleId)

    suspend fun addPhoto(sampleId: Long, imagePath: String): Long {
        val id = sampleImageDao.insert(
            SampleImageEntity(sampleId = sampleId, imagePath = imagePath)
        )
        syncHasPhoto(sampleId)
        return id
    }

    suspend fun deletePhoto(imageId: Long, sampleId: Long): Boolean {
        val photo = sampleImageDao.getPhotosForSample(sampleId)
            .firstOrNull { it.id == imageId } ?: return false
        sampleImageDao.deleteById(imageId)
        PhotoStorage.delete(photo.imagePath)
        syncHasPhoto(sampleId)
        return true
    }

    suspend fun getNoteWithPhotos(
        sampleId: Long
    ): Pair<SampleNoteEntity?, List<SampleImageEntity>> {
        val note = sampleNoteDao.getNote(sampleId)
        val photos = sampleImageDao.getPhotosForSample(sampleId)
        return note to photos
    }

    suspend fun syncHasNoteAndPhoto(sampleId: Long) {
        val note = sampleNoteDao.getNote(sampleId)
        val hasText = !note?.noteText.isNullOrBlank()
        val photosCount = sampleImageDao.countForSample(sampleId)
        val sample = sampleDao.getSampleById(sampleId) ?: return
        sampleDao.update(
            sample.copy(hasNote = hasText, hasPhoto = photosCount > 0)
        )
    }

    private suspend fun syncHasPhoto(sampleId: Long) {
        val photosCount = sampleImageDao.countForSample(sampleId)
        val sample = sampleDao.getSampleById(sampleId) ?: return
        sampleDao.update(sample.copy(hasPhoto = photosCount > 0))
    }

    private suspend fun collectImagePathsForOrder(orderId: Long): List<String> =
        sampleImageDao.getImagePathsForOrder(orderId)

    // ============ ФАЙЛ БД ============

    fun getDatabaseFile(): File = appContext.getDatabasePath("geosamples.db")

    // ============ ОЧИСТКА НАРЯДА ============

    suspend fun clearOrder(orderId: Long) {
        val imagePaths = collectImagePathsForOrder(orderId)
        sampleDao.deleteAllForOrder(orderId)
        orderWellDao.deleteAllForOrder(orderId)
        PhotoStorage.deleteAll(imagePaths)
    }

    /**
     * FIX 5.8.10-d:
     * Раньше метод шёл через areaDao.getAreaId(areaName) с LIMIT 1.
     * Если в БД когда-либо появились два участка с одинаковым
     * area_name (без UNIQUE на areas.area_name это возможно),
     * возвращался только один — и наряд, записанный в «другой»
     * дубликат, не находился.
     *
     * Теперь ищем наряды по ИМЕНИ участка напрямую через JOIN и
     * складываем статистику по всем совпадениям. Обычно это один
     * наряд; в патологическом случае — сумма по дубликатам.
     */
    suspend fun getExistingOrderStats(areaName: String, orderNumber: String): OrderStats? {
        val orderIds = orderDao.getOrderIdsByName(areaName, orderNumber)
        if (orderIds.isEmpty()) return null

        var total = 0
        var found = 0
        for (id in orderIds) {
            total += sampleDao.getCountForOrder(id)
            found += sampleDao.getFoundCountForOrder(id)
        }
        return OrderStats(total = total, found = found)
    }

    // ============ ДЛЯ СВЕРКИ ============

    suspend fun setSampleStatus(sampleId: Long, status: String) {
        sampleDao.setStatus(sampleId, status)
    }

    suspend fun setHasNote(sampleId: Long, hasNote: Boolean) {
        sampleDao.setHasNote(sampleId, hasNote)
    }

    suspend fun saveRows(rows: List<SampleRow>) {
        if (rows.isEmpty()) return
        val ids = rows.mapNotNull { it.id.toLongOrNull() }
        if (ids.isEmpty()) return

        db.withTransaction {
            val existingSamples = sampleDao.getSamplesByIds(ids)
            val byId = existingSamples.associateBy { it.id }

            val entities = rows.mapNotNull { row ->
                val id = row.id.toLongOrNull() ?: return@mapNotNull null
                val s = byId[id] ?: return@mapNotNull null
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
                    hasNote = row.hasNote,
                    hasPhoto = row.hasPhoto
                )
            }
            if (entities.isNotEmpty()) sampleDao.updateAll(entities)
        }
    }

    suspend fun deleteSampleWithRenumber(sampleId: Long, recalc: Boolean) {
        val imagePaths = getImagePathsForSample(sampleId)

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
                    sample.copy(sampleNumber = newSampleNumber, serialNumber = newOrderNum)
                )
            }
        }
        PhotoStorage.deleteAll(imagePaths)
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
