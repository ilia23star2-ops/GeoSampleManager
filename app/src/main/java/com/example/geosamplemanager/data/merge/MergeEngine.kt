package com.example.geosamplemanager.data.merge

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.example.geosamplemanager.data.AppDatabase
import com.example.geosamplemanager.data.DatabaseRepository
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.OrderWellEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import com.example.geosamplemanager.data.entity.SampleImageEntity
import com.example.geosamplemanager.data.entity.SampleNoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * FIX 5.9-db-merge-v2:
 * Движок слияния текущей БД с выбранным .gsmbackup.
 *
 * /1 — участки, наряды.
 * /2 — пробы, скважины, заметки.
 * /3 — фото (физическое копирование файлов).
 */
object MergeEngine {

    const val SUPPORTED_SCHEMA_VERSION = 2
    const val DB_ENTRY = "geosamples.db"
    const val PHOTOS_PREFIX = "sample_photos/"

    private const val PHOTOS_DIR_NAME = "sample_photos"

    // ================================================================
    // Открытие / закрытие архива
    // ================================================================

    /**
     * FIX 5.9-db-merge-v2/3:
     * Распаковывает и geosamples.db, и sample_photos/ во временные
     * файлы в cacheDir. Проверяет версию схемы. Открывает Room
     * на temp-БД.
     */
    suspend fun openArchive(
        context: Context,
        uri: Uri,
        cacheDir: File
    ): Result<TempDatabaseHandle> = withContext(Dispatchers.IO) {
        var tempDbFile: File? = null
        var tempPhotosDir: File? = null
        try {
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val token = UUID.randomUUID().toString()
            tempDbFile = File(cacheDir, "merge_temp_$token.db")
            tempPhotosDir = File(cacheDir, "merge_photos_$token")
            if (!tempPhotosDir.exists()) tempPhotosDir.mkdirs()

            val extracted = extractFromUri(context, uri, tempDbFile, tempPhotosDir)
            if (!extracted) {
                cleanTemp(tempDbFile, tempPhotosDir)
                return@withContext Result.failure(
                    IllegalStateException("В архиве нет geosamples.db")
                )
            }

            val version = readSchemaVersion(tempDbFile)
            if (version != SUPPORTED_SCHEMA_VERSION) {
                cleanTemp(tempDbFile, tempPhotosDir)
                return@withContext Result.failure(
                    IllegalStateException(
                        "Схема БД в архиве: $version, " +
                                "ожидается $SUPPORTED_SCHEMA_VERSION"
                    )
                )
            }

            val db = AppDatabase.buildTemp(context, tempDbFile)
            Result.success(
                TempDatabaseHandle(
                    db = db,
                    file = tempDbFile,
                    photosDir = tempPhotosDir
                )
            )
        } catch (e: Exception) {
            cleanTemp(tempDbFile, tempPhotosDir)
            Result.failure(e)
        }
    }

    fun closeAndClean(handle: TempDatabaseHandle) {
        try { handle.db.close() } catch (_: Exception) {}
        cleanTemp(handle.file, handle.photosDir)
    }

    private fun cleanTemp(dbFile: File?, photosDir: File?) {
        try { if (dbFile != null && dbFile.exists()) dbFile.delete() } catch (_: Exception) {}
        try { if (photosDir != null && photosDir.exists()) photosDir.deleteRecursively() } catch (_: Exception) {}
    }

    // ================================================================
    // /1: участки
    // ================================================================

    fun planAreas(
        myAreas: List<AreaEntity>,
        theirAreas: List<AreaEntity>
    ): AreaPlan {
        val myByNameMinId = mutableMapOf<String, Long>()
        val myNameCount = mutableMapOf<String, Int>()

        for (a in myAreas) {
            val cur = myByNameMinId[a.areaName]
            if (cur == null || a.id < cur) myByNameMinId[a.areaName] = a.id
            myNameCount[a.areaName] = (myNameCount[a.areaName] ?: 0) + 1
        }

        val existing = mutableMapOf<Long, Long>()
        val toAdd = mutableListOf<AreaToAdd>()

        for (their in theirAreas) {
            val myId = myByNameMinId[their.areaName]
            if (myId != null) {
                existing[their.id] = myId
            } else {
                toAdd += AreaToAdd(
                    theirId = their.id,
                    entity = AreaEntity(
                        id = 0,
                        areaName = their.areaName,
                        createdDate = their.createdDate
                    )
                )
            }
        }

        val duplicates = myNameCount.filter { it.value > 1 }.keys.toList()

        return AreaPlan(
            existing = existing,
            toAdd = toAdd,
            duplicatesInMine = duplicates
        )
    }

    suspend fun applyAreaPlan(
        repo: DatabaseRepository,
        plan: AreaPlan
    ): Map<Long, Long> {
        val result = HashMap<Long, Long>(plan.existing)
        for (add in plan.toAdd) {
            val newId = repo.addArea(add.entity.areaName)
            if (newId > 0) result[add.theirId] = newId
        }
        return result
    }

    // ================================================================
    // /1: наряды
    // ================================================================

    fun planOrders(
        myOrders: List<OrderEntity>,
        theirOrders: List<OrderEntity>,
        areaIdMap: Map<Long, Long>,
        newlyAddedAreaIds: Map<Long, Long> = emptyMap()
    ): OrderPlan {
        val combined = HashMap<Long, Long>(areaIdMap)
        combined.putAll(newlyAddedAreaIds)

        val myByKey = myOrders.associate {
            (it.areaId to it.orderNumber) to it.id
        }

        val existing = mutableMapOf<Long, Long>()
        val toAdd = mutableListOf<OrderToAdd>()
        var skipped = 0

        for (their in theirOrders) {
            val myAreaId = combined[their.areaId]
            if (myAreaId == null) {
                skipped++
                continue
            }

            val existingMyId = myByKey[myAreaId to their.orderNumber]
            if (existingMyId != null) {
                existing[their.id] = existingMyId
                continue
            }

            toAdd += OrderToAdd(
                theirId = their.id,
                entity = OrderEntity(
                    id = 0,
                    areaId = myAreaId,
                    orderNumber = their.orderNumber,
                    createdDate = their.createdDate
                )
            )
        }

        return OrderPlan(
            existing = existing,
            toAdd = toAdd,
            skippedOrphans = skipped
        )
    }

    suspend fun applyOrderPlan(
        repo: DatabaseRepository,
        plan: OrderPlan
    ): Map<Long, Long> {
        val result = HashMap<Long, Long>(plan.existing)
        for (add in plan.toAdd) {
            val newId = repo.addOrder(
                areaId = add.entity.areaId,
                orderNumber = add.entity.orderNumber
            )
            if (newId > 0) result[add.theirId] = newId
        }
        return result
    }

    // ================================================================
    // /2: пробы
    // ================================================================

    fun planSamples(
        mySamples: List<SampleEntity>,
        theirSamples: List<SampleEntity>,
        orderIdMap: Map<Long, Long>,
        newlyAddedOrderIds: Map<Long, Long> = emptyMap()
    ): SamplePlan {
        val combined = HashMap<Long, Long>(orderIdMap)
        combined.putAll(newlyAddedOrderIds)

        val myByKey = mySamples.associate {
            (it.orderId to it.sampleNumber) to it
        }

        val toAdd = mutableListOf<SampleToAdd>()
        val conflicts = mutableListOf<SampleConflict>()
        var skipped = 0

        for (their in theirSamples) {
            val myOrderId = combined[their.orderId]
            if (myOrderId == null) {
                skipped++
                continue
            }

            val myExisting = myByKey[myOrderId to their.sampleNumber]
            if (myExisting != null) {
                conflicts += SampleConflict(
                    theirId = their.id,
                    myId = myExisting.id,
                    sampleNumber = their.sampleNumber,
                    myEntity = myExisting,
                    theirEntity = their
                )
                continue
            }

            toAdd += SampleToAdd(
                theirId = their.id,
                entity = their.copy(id = 0, orderId = myOrderId)
            )
        }

        return SamplePlan(
            toAdd = toAdd,
            conflicts = conflicts,
            skippedOrphans = skipped
        )
    }

    fun resolveSample(
        my: SampleEntity,
        their: SampleEntity,
        resolution: ConflictResolution
    ): SampleEntity? {
        if (resolution == ConflictResolution.KEEP_MINE) return null

        return my.copy(
            serialNumber = their.serialNumber,
            wellNumber = their.wellNumber,
            workings = their.workings,
            intervalFrom = their.intervalFrom,
            intervalTo = their.intervalTo,
            weight = their.weight,
            controlWeight = their.controlWeight,
            actualWeight = their.actualWeight,
            sampleType = their.sampleType,
            status = their.status,
            reservedType = their.reservedType,
            materialDesc = their.materialDesc,
            found = their.found,
            weightControl = their.weightControl,
            postponed = their.postponed
            // hasNote / hasPhoto — не из архива, пересчитаются.
        )
    }

    suspend fun applySamplePlan(
        repo: DatabaseRepository,
        plan: SamplePlan,
        resolutions: Map<Long, ConflictResolution>
    ): Map<Long, Long> {
        val result = HashMap<Long, Long>()

        for (add in plan.toAdd) {
            val newId = repo.addSample(add.entity)
            if (newId > 0) result[add.theirId] = newId
        }

        for (conflict in plan.conflicts) {
            result[conflict.theirId] = conflict.myId
            val resolution = resolutions[conflict.theirId]
                ?: ConflictResolution.KEEP_MINE
            val updated = resolveSample(
                my = conflict.myEntity,
                their = conflict.theirEntity,
                resolution = resolution
            )
            if (updated != null) {
                repo.updateSample(updated)
            }
        }

        return result
    }

    // ================================================================
    // /2: скважины
    // ================================================================

    fun planWells(
        myWells: Map<Long, List<String>>,
        theirWells: Map<Long, List<String>>,
        orderIdMap: Map<Long, Long>,
        newlyAddedOrderIds: Map<Long, Long> = emptyMap()
    ): WellPlan {
        val combined = HashMap<Long, Long>(orderIdMap)
        combined.putAll(newlyAddedOrderIds)

        val toAdd = mutableListOf<OrderWellToAdd>()

        for ((theirOrderId, theirList) in theirWells) {
            val myOrderId = combined[theirOrderId] ?: continue
            val mine = myWells[myOrderId]?.toHashSet() ?: HashSet()
            val seen = HashSet<String>()

            for (well in theirList) {
                if (well.isBlank()) continue
                if (well in mine) continue
                if (!seen.add(well)) continue
                toAdd += OrderWellToAdd(myOrderId = myOrderId, wellNumber = well)
            }
        }

        return WellPlan(toAdd = toAdd)
    }

    suspend fun applyWellPlan(
        repo: DatabaseRepository,
        plan: WellPlan
    ) {
        for (add in plan.toAdd) {
            repo.addWell(orderId = add.myOrderId, wellNumber = add.wellNumber)
        }
    }

    // ================================================================
    // /2: заметки
    // ================================================================

    fun planNotes(
        myNotes: Map<Long, String?>,
        theirNotes: Map<Long, String?>,
        sampleIdMap: Map<Long, Long>
    ): NotePlan {
        val toAdd = mutableListOf<NoteToAdd>()
        val conflicts = mutableListOf<NoteConflict>()

        for ((theirSampleId, theirText) in theirNotes) {
            val mySampleId = sampleIdMap[theirSampleId] ?: continue
            val myText = myNotes[mySampleId]

            val myHas = !myText.isNullOrBlank()
            val theirHas = !theirText.isNullOrBlank()

            when {
                myHas && theirHas -> conflicts += NoteConflict(
                    theirSampleId = theirSampleId,
                    mySampleId = mySampleId,
                    myText = myText,
                    theirText = theirText
                )
                !myHas && theirHas -> toAdd += NoteToAdd(
                    mySampleId = mySampleId,
                    text = theirText
                )
            }
        }

        return NotePlan(toAdd = toAdd, conflicts = conflicts)
    }

    suspend fun applyNotePlan(
        repo: DatabaseRepository,
        plan: NotePlan,
        resolutions: Map<Long, ConflictResolution>
    ) {
        for (add in plan.toAdd) {
            repo.upsertNote(
                SampleNoteEntity(
                    id = 0,
                    sampleId = add.mySampleId,
                    noteText = add.text,
                    createdDate = System.currentTimeMillis()
                )
            )
        }

        for (conflict in plan.conflicts) {
            val resolution = resolutions[conflict.theirSampleId]
                ?: ConflictResolution.KEEP_MINE
            if (resolution == ConflictResolution.TAKE_THEIRS) {
                repo.upsertNote(
                    SampleNoteEntity(
                        id = 0,
                        sampleId = conflict.mySampleId,
                        noteText = conflict.theirText,
                        createdDate = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    // ================================================================
    // /3: фото
    // ================================================================

    /**
     * FIX 5.9-db-merge-v2/3:
     * Из image_path архива вытащить имя файла.
     * Например:
     *   "/data/.../sample_photos/photo_abc.jpg" → "photo_abc.jpg"
     *   "photo_abc.jpg"                          → "photo_abc.jpg"
     *   ""                                       → null
     */
    fun extractArchivePhotoName(imagePath: String): String? {
        if (imagePath.isBlank()) return null
        val name = imagePath.substringAfterLast('/')
        return name.takeIf { it.isNotBlank() }
    }

    /**
     * FIX 5.9-db-merge-v2/3:
     * План слияния фото.
     *
     * Правила:
     *  - Новая проба → все её фото из архива добавляем.
     *  - Конфликт + TAKE_THEIRS → фото архива добавляем
     *    (к существующим у меня, ничего не удаляем).
     *  - Конфликт + KEEP_MINE → фото архива НЕ добавляем.
     *
     * @param theirImages все фото из архива.
     * @param sampleIdMap Map<theirSampleId, mySampleId>.
     * @param conflictSampleIds Set<theirSampleId> — какие были
     *        конфликтами (from SamplePlan.conflicts).
     * @param resolutions Map<theirSampleId, Resolution>.
     */
    fun planPhotos(
        theirImages: List<SampleImageEntity>,
        sampleIdMap: Map<Long, Long>,
        conflictSampleIds: Set<Long>,
        resolutions: Map<Long, ConflictResolution>
    ): PhotoPlan {
        val toAdd = mutableListOf<PhotoToAdd>()

        for (img in theirImages) {
            val mySampleId = sampleIdMap[img.sampleId] ?: continue

            if (img.sampleId in conflictSampleIds) {
                val r = resolutions[img.sampleId]
                    ?: ConflictResolution.KEEP_MINE
                if (r == ConflictResolution.KEEP_MINE) continue
            }

            val name = extractArchivePhotoName(img.imagePath) ?: continue
            toAdd += PhotoToAdd(mySampleId = mySampleId, archiveFileName = name)
        }

        return PhotoPlan(toAdd = toAdd)
    }

    /**
     * FIX 5.9-db-merge-v2/3:
     * Скопировать файлы из archivePhotosDir в
     * filesDir/sample_photos/ под UUID-именем и записать
     * в sample_images через addPhoto.
     *
     * Если файла в архиве нет — пропускаем (архив мог быть без
     * части фото). Ошибки отдельного файла не валят весь merge.
     */
    suspend fun applyPhotoPlan(
        context: Context,
        repo: DatabaseRepository,
        plan: PhotoPlan,
        archivePhotosDir: File
    ) {
        if (plan.toAdd.isEmpty()) return

        val targetDir = File(context.filesDir, PHOTOS_DIR_NAME)
        if (!targetDir.exists()) targetDir.mkdirs()

        for (add in plan.toAdd) {
            val src = File(archivePhotosDir, add.archiveFileName)
            if (!src.exists()) continue

            val newName = "photo_${UUID.randomUUID()}.jpg"
            val dst = File(targetDir, newName)
            try {
                src.copyTo(dst, overwrite = false)
                repo.addPhoto(add.mySampleId, dst.absolutePath)
            } catch (_: Exception) {
                try { if (dst.exists()) dst.delete() } catch (_: Exception) {}
            }
        }
    }

    // ================================================================
    // Внутреннее
    // ================================================================

    private fun extractFromUri(
        context: Context,
        uri: Uri,
        targetDb: File,
        targetPhotosDir: File
    ): Boolean {
        return try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return false
            input.use { stream ->
                val zip = ZipInputStream(stream)
                var entry = zip.nextEntry
                var dbWritten = false
                while (entry != null) {
                    val name = entry.name ?: ""
                    when {
                        name == DB_ENTRY -> {
                            targetDb.outputStream().use { out -> zip.copyTo(out) }
                            dbWritten = true
                        }
                        name.startsWith(PHOTOS_PREFIX) && !entry.isDirectory -> {
                            val relative = name.removePrefix(PHOTOS_PREFIX)
                            if (relative.isNotBlank()) {
                                val f = File(targetPhotosDir, relative)
                                f.parentFile?.mkdirs()
                                f.outputStream().use { out -> zip.copyTo(out) }
                            }
                        }
                    }
                    entry = zip.nextEntry
                }
                try { zip.close() } catch (_: Exception) {}
                dbWritten
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun readSchemaVersion(dbFile: File): Int {
        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            db.rawQuery("PRAGMA user_version", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else -1
            }
        } catch (_: Exception) {
            -1
        } finally {
            try { db?.close() } catch (_: Exception) {}
        }
    }
}