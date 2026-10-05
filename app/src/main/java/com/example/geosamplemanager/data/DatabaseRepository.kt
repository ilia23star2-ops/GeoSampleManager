package com.example.geosamplemanager.data

import android.content.Context
import androidx.room.withTransaction
import com.example.geosamplemanager.data.diagnostics.DbDiagnosticsEngine
import com.example.geosamplemanager.data.diagnostics.DbIssue
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

/**
 * FIX 5.9-db-info: getDbInfo().
 * FIX 5.9-db-backup-v2: checkpointWal(), getPhotosDir(),
 *   DB_SCHEMA_VERSION.
 * FIX 5.9-db-rollback: getRollbackBackupsDir().
 * FIX 5.9-db-clean: clearAllData().
 * FIX 5.9-db-diagnostics: runDiagnostics(), applyDiagnosticsFixes().
 *
 * FIX 5.9-main-a:
 *  - countOrphanOrders() — количество нарядов без участка (быстрый
 *    SQL, без файловых проверок);
 *  - countOrphanSamples() — количество проб без наряда.
 *    Используется индикатором «База в порядке» на Главной.
 */
data class DbInfo(
    val dbPath: String,
    val dbSizeBytes: Long,
    val photosSizeBytes: Long,
    val lastModified: Long,
    val areasCount: Int,
    val ordersCount: Int,
    val samplesCount: Int,
    val notesCount: Int,
    val photosCount: Int,
    val wellsCount: Int
)

class DatabaseRepository(context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val areaDao = db.areaDao()
    private val orderDao = db.orderDao()
    private val sampleDao = db.sampleDao()
    private val orderWellDao = db.orderWellDao()
    private val sampleNoteDao = db.sampleNoteDao()
    private val sampleImageDao = db.sampleImageDao()

    companion object {
        const val DB_SCHEMA_VERSION = 2
    }

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

    fun getAllOrdersFlow(): Flow<List<OrderEntity>> = orderDao.getAllOrdersFlow()

    // ============ ПРОБЫ ============

    fun getSamplesForOrder(orderId: Long): Flow<List<SampleEntity>> =
        sampleDao.getSamplesForOrder(orderId)

    fun getAllSamplesFlow(): Flow<List<SampleEntity>> =
        sampleDao.getAllSamplesFlow()

    suspend fun getSamplesForOrderList(orderId: Long): List<SampleEntity> =
        sampleDao.getSamplesForOrderList(orderId)

    suspend fun getSamplesForOrders(orderIds: List<Long>): List<SampleEntity> =
        if (orderIds.isEmpty()) emptyList()
        else sampleDao.getSamplesForOrders(orderIds)

    suspend fun getSampleById(sampleId: Long): SampleEntity? =
        sampleDao.getSampleById(sampleId)

    suspend fun getSamplesByWell(wellNumber: String): List<SampleEntity> =
        sampleDao.getSamplesByWell(wellNumber)

    suspend fun findSampleByOrderAndNumber(
        orderId: Long,
        sampleNumber: String
    ): SampleEntity? = sampleDao.findByOrderAndNumber(orderId, sampleNumber)

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

    suspend fun addSampleWithShift(
        newSample: SampleEntity,
        shifts: List<Pair<Long, String>>
    ): Long {
        return db.withTransaction {
            shifts.reversed().forEach { (id, newNum) ->
                val existing = sampleDao.getSampleById(id) ?: return@forEach
                if (existing.sampleNumber != newNum) {
                    sampleDao.update(existing.copy(sampleNumber = newNum))
                }
            }
            sampleDao.insert(newSample)
        }
    }

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

    fun getPhotosDir(): File = File(appContext.filesDir, "sample_photos")

    fun getRollbackBackupsDir(): File = File(appContext.filesDir, "db_backups")

    fun checkpointWal() {
        try {
            db.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .use { it.moveToFirst() }
        } catch (_: Exception) {
        }
    }

    suspend fun getDbInfo(): DbInfo {
        val dbFile = getDatabaseFile()
        val photosDir = getPhotosDir()

        val photosSize = if (photosDir.exists() && photosDir.isDirectory) {
            photosDir.listFiles()?.sumOf { it.length() } ?: 0L
        } else 0L

        return DbInfo(
            dbPath = dbFile.absolutePath,
            dbSizeBytes = if (dbFile.exists()) dbFile.length() else 0L,
            photosSizeBytes = photosSize,
            lastModified = if (dbFile.exists()) dbFile.lastModified() else 0L,
            areasCount = getAreas().size,
            ordersCount = getAllOrders().size,
            samplesCount = getTotalCount(),
            notesCount = sampleNoteDao.countAll(),
            photosCount = sampleImageDao.countAll(),
            wellsCount = orderWellDao.countAll()
        )
    }

    // ============ ОЧИСТКА ============

    suspend fun clearAllData() {
        db.clearAllTables()

        val photosDir = getPhotosDir()
        if (photosDir.exists() && photosDir.isDirectory) {
            val files = photosDir.listFiles()
            if (files != null) {
                for (f in files) {
                    try {
                        if (f.isFile) f.delete()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    suspend fun clearOrder(orderId: Long) {
        val imagePaths = collectImagePathsForOrder(orderId)
        sampleDao.deleteAllForOrder(orderId)
        orderWellDao.deleteAllForOrder(orderId)
        PhotoStorage.deleteAll(imagePaths)
    }

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

    // ============ ДИАГНОСТИКА БД ============

    suspend fun runDiagnostics(): List<DbIssue> {
        val orphanOrders = orderDao.findOrphanOrders()
        val orphanSamples = sampleDao.findOrphanSamples()
        val allSamples = sampleDao.getAllSamplesList()
        val allImages = sampleImageDao.getAllImages()

        return DbDiagnosticsEngine.detect(
            orphanOrders = orphanOrders,
            orphanSamples = orphanSamples,
            allSamples = allSamples,
            allImages = allImages,
            fileExists = { path -> File(path).exists() }
        )
    }

    suspend fun applyDiagnosticsFixes(selected: List<DbIssue>): Int {
        if (selected.isEmpty()) return 0

        val filesToDelete = mutableListOf<String>()
        var fixedCount = 0

        db.withTransaction {
            for (issue in selected) {
                when (issue) {
                    is DbIssue.OrphanOrder -> {
                        orderDao.deleteById(issue.orderId)
                        fixedCount++
                    }
                    is DbIssue.OrphanSample -> {
                        filesToDelete +=
                            sampleImageDao.getImagePathsForSample(issue.sampleId)
                        sampleDao.deleteById(issue.sampleId)
                        fixedCount++
                    }
                    is DbIssue.BrokenPhotoLink -> {
                        sampleImageDao.deleteById(issue.imageId)
                        filesToDelete += issue.imagePath
                        val sample = sampleDao.getSampleById(issue.sampleId)
                        if (sample != null) {
                            val remaining =
                                sampleImageDao.countForSample(issue.sampleId)
                            sampleDao.update(
                                sample.copy(hasPhoto = remaining > 0)
                            )
                        }
                        fixedCount++
                    }
                    is DbIssue.PhotoFlagMismatch -> {
                        val sample = sampleDao.getSampleById(issue.sampleId)
                        if (sample != null && !sample.hasPhoto) {
                            sampleDao.update(sample.copy(hasPhoto = true))
                            fixedCount++
                        }
                    }
                }
            }
        }

        if (filesToDelete.isNotEmpty()) {
            PhotoStorage.deleteAll(filesToDelete)
        }
        return fixedCount
    }

    // ============ FIX 5.9-main-a: ЛЁГКАЯ ПРОВЕРКА ============

    /**
     * Лёгкая проверка «База в порядке» для Главной.
     * Только SQL-сироты. Без чтения файлов фото — это делает
     * полная диагностика по кнопке.
     */
    suspend fun countOrphanOrders(): Int = orderDao.findOrphanOrders().size

    suspend fun countOrphanSamples(): Int = sampleDao.findOrphanSamples().size
}

data class OrderStats(val total: Int, val found: Int)