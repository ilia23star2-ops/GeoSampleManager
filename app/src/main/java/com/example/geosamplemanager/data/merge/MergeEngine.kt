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
 * FIX 5.9-db-merge-v2/7:
 *  - planSamples заполняет areaName / orderNumber / wellNumber;
 *  - buildConflictTree — дерево конфликтов по наряду и скважине.
 */
object MergeEngine {

    const val SUPPORTED_SCHEMA_VERSION = 2
    const val DB_ENTRY = "geosamples.db"
    const val PHOTOS_PREFIX = "sample_photos/"
    private const val PHOTOS_DIR_NAME = "sample_photos"

    // ================================================================
    // Открытие / закрытие архива
    // ================================================================

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
                        "Схема БД в архиве: $version, ожидается $SUPPORTED_SCHEMA_VERSION"
                    )
                )
            }

            val db = AppDatabase.buildTemp(context, tempDbFile)
            Result.success(TempDatabaseHandle(db, tempDbFile, tempPhotosDir))
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
        try {
            if (photosDir != null && photosDir.exists()) photosDir.deleteRecursively()
        } catch (_: Exception) {}
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
            if (myId != null) existing[their.id] = myId
            else toAdd += AreaToAdd(
                theirId = their.id,
                entity = AreaEntity(0, their.areaName, their.createdDate)
            )
        }
        return AreaPlan(
            existing = existing,
            toAdd = toAdd,
            duplicatesInMine = myNameCount.filter { it.value > 1 }.keys.toList()
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
        val myByKey = myOrders.associate { (it.areaId to it.orderNumber) to it.id }

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
                entity = OrderEntity(0, myAreaId, their.orderNumber, their.createdDate)
            )
        }
        return OrderPlan(existing, toAdd, skipped)
    }

    suspend fun applyOrderPlan(
        repo: DatabaseRepository,
        plan: OrderPlan
    ): Map<Long, Long> {
        val result = HashMap<Long, Long>(plan.existing)
        for (add in plan.toAdd) {
            val newId = repo.addOrder(add.entity.areaId, add.entity.orderNumber)
            if (newId > 0) result[add.theirId] = newId
        }
        return result
    }

    // ================================================================
    // /5, /6, /7: пробы
    // ================================================================

    /**
     * FIX 5.9-db-merge-v2/7:
     * @param myOrders / myAreas — для заполнения orderNumber/areaName
     *        в SampleConflict. Если не переданы — поля пустые.
     */
    fun planSamples(
        mySamples: List<SampleEntity>,
        theirSamples: List<SampleEntity>,
        orderIdMap: Map<Long, Long>,
        newlyAddedOrderIds: Map<Long, Long> = emptyMap(),
        myOrders: List<OrderEntity> = emptyList(),
        myAreas: List<AreaEntity> = emptyList()
    ): SamplePlan {
        val combined = HashMap<Long, Long>(orderIdMap)
        combined.putAll(newlyAddedOrderIds)

        val myByKey = mySamples.associate { (it.orderId to it.sampleNumber) to it }

        val orderById: Map<Long, OrderEntity> = myOrders.associateBy { it.id }
        val areaById: Map<Long, AreaEntity> = myAreas.associateBy { it.id }

        val toAdd = mutableListOf<SampleToAdd>()
        val identical = mutableListOf<SampleMatch>()
        val conflicts = mutableListOf<SampleConflict>()
        var skipped = 0

        for (their in theirSamples) {
            val myOrderId = combined[their.orderId]
            if (myOrderId == null) {
                skipped++
                continue
            }
            val my = myByKey[myOrderId to their.sampleNumber]
            if (my == null) {
                toAdd += SampleToAdd(
                    theirId = their.id,
                    entity = their.copy(id = 0, orderId = myOrderId)
                )
                continue
            }
            val diffs = diffFields(my, their)
            if (diffs.isEmpty()) {
                identical += SampleMatch(theirId = their.id, myId = my.id)
            } else {
                val ord = orderById[myOrderId]
                val areaName = ord?.let { areaById[it.areaId]?.areaName } ?: ""
                conflicts += SampleConflict(
                    theirId = their.id,
                    myId = my.id,
                    sampleNumber = their.sampleNumber,
                    myEntity = my,
                    theirEntity = their,
                    fieldDiffs = diffs,
                    areaName = areaName,
                    orderNumber = ord?.orderNumber ?: "",
                    wellNumber = my.wellNumber
                )
            }
        }

        return SamplePlan(toAdd, identical, conflicts, skipped)
    }

    /**
     * FIX 5.9-db-merge-v2/7:
     * Дерево конфликтов: Наряд → Скважина → Проба.
     *
     * Группировка по (areaName, orderNumber), внутри — по wellNumber.
     * Порядок групп — по первому появлению.
     */
    fun buildConflictTree(conflicts: List<SampleConflict>): List<ConflictTreeNode> {
        if (conflicts.isEmpty()) return emptyList()

        data class OrderKey(val area: String, val order: String)

        val orderOrder = mutableListOf<OrderKey>()
        val byOrder = mutableMapOf<OrderKey, MutableMap<String, MutableList<SampleConflict>>>()

        for (c in conflicts) {
            val key = OrderKey(c.areaName, c.orderNumber)
            val wellMap = byOrder.getOrPut(key) {
                orderOrder += key
                mutableMapOf()
            }
            wellMap.getOrPut(c.wellNumber) { mutableListOf() }.add(c)
        }

        return orderOrder.map { key ->
            val wellMap = byOrder[key] ?: emptyMap()
            val wells = wellMap.map { (well, list) ->
                ConflictWellNode(
                    wellNumber = well,
                    conflicts = list
                )
            }
            ConflictTreeNode(
                areaName = key.area,
                orderNumber = key.order,
                wells = wells
            )
        }
    }

    fun diffFields(my: SampleEntity, their: SampleEntity): List<FieldDiff> {
        val list = mutableListOf<FieldDiff>()

        if (my.weight != their.weight) list += diff(
            SampleField.WEIGHT, my.weight, their.weight
        )
        if (my.sampleType != their.sampleType) list += diff(
            SampleField.SAMPLE_TYPE, my.sampleType, their.sampleType
        )
        if (my.status != their.status) list += diff(
            SampleField.STATUS, my.status, their.status
        )
        if (my.controlWeight != their.controlWeight) list += diff(
            SampleField.CONTROL_WEIGHT, my.controlWeight, their.controlWeight
        )
        if (my.intervalFrom != their.intervalFrom) list += diff(
            SampleField.INTERVAL_FROM, my.intervalFrom, their.intervalFrom
        )
        if (my.intervalTo != their.intervalTo) list += diff(
            SampleField.INTERVAL_TO, my.intervalTo, their.intervalTo
        )

        if (!my.found && their.found) list += diff(
            SampleField.FOUND, my.found, their.found
        )
        if (!my.postponed && their.postponed) list += diff(
            SampleField.POSTPONED, my.postponed, their.postponed
        )
        if (!my.weightControl && their.weightControl) list += diff(
            SampleField.WEIGHT_CONTROL, my.weightControl, their.weightControl
        )

        if ((my.materialDesc ?: "") != (their.materialDesc ?: "")) list += diff(
            SampleField.MATERIAL_DESC, my.materialDesc, their.materialDesc
        )

        return list
    }

    private fun diff(field: SampleField, myRaw: Any?, theirRaw: Any?): FieldDiff =
        FieldDiff(
            field = field,
            myRaw = myRaw,
            theirRaw = theirRaw,
            myDisplay = displayFor(field, myRaw),
            theirDisplay = displayFor(field, theirRaw)
        )

    fun displayFor(field: SampleField, raw: Any?): String = when (field) {
        SampleField.SAMPLE_TYPE -> when (raw as? String) {
            "auger" -> "Шнековая"
            "channel" -> "Бороздовая"
            "cobra" -> "Кобра"
            "duplicate" -> "Дубликат"
            else -> "—"
        }
        SampleField.STATUS -> when (raw as? String) {
            "normal" -> "Обычная"
            "blank" -> "Холостая"
            "control" -> "Вес. контроль"
            else -> "—"
        }
        SampleField.FOUND,
        SampleField.POSTPONED,
        SampleField.WEIGHT_CONTROL -> when (raw) {
            true -> "Да"
            false -> "Нет"
            else -> "—"
        }
        SampleField.WEIGHT,
        SampleField.CONTROL_WEIGHT,
        SampleField.INTERVAL_FROM,
        SampleField.INTERVAL_TO -> (raw as? Double)?.toString() ?: "—"
        SampleField.MATERIAL_DESC -> {
            val s = raw as? String
            if (s.isNullOrBlank()) "—" else s
        }
    }

    fun resolveSample(
        my: SampleEntity,
        their: SampleEntity,
        resolution: FieldResolution
    ): SampleEntity? {
        var s = my
        for ((field, owner) in resolution.map) {
            if (owner == FieldOwner.MINE) continue
            s = applyField(s, field, their)
        }
        return if (s != my) s else null
    }

    private fun applyField(
        s: SampleEntity,
        field: SampleField,
        src: SampleEntity
    ): SampleEntity = when (field) {
        SampleField.WEIGHT -> s.copy(weight = src.weight)
        SampleField.SAMPLE_TYPE -> s.copy(sampleType = src.sampleType)
        SampleField.STATUS -> s.copy(status = src.status)
        SampleField.CONTROL_WEIGHT -> s.copy(controlWeight = src.controlWeight)
        SampleField.INTERVAL_FROM -> s.copy(intervalFrom = src.intervalFrom)
        SampleField.INTERVAL_TO -> s.copy(intervalTo = src.intervalTo)
        SampleField.FOUND -> s.copy(found = src.found)
        SampleField.POSTPONED -> s.copy(postponed = src.postponed)
        SampleField.WEIGHT_CONTROL -> s.copy(weightControl = src.weightControl)
        SampleField.MATERIAL_DESC -> s.copy(materialDesc = src.materialDesc)
    }

    suspend fun applySamplePlan(
        repo: DatabaseRepository,
        plan: SamplePlan,
        resolutions: Map<Long, FieldResolution>
    ): Map<Long, Long> {
        val result = HashMap<Long, Long>()

        for (add in plan.toAdd) {
            val newId = repo.addSample(add.entity)
            if (newId > 0) result[add.theirId] = newId
        }

        for (m in plan.identical) {
            result[m.theirId] = m.myId
        }

        for (conflict in plan.conflicts) {
            result[conflict.theirId] = conflict.myId
            val r = resolutions[conflict.theirId] ?: FieldResolution.Empty
            val updated = resolveSample(conflict.myEntity, conflict.theirEntity, r)
            if (updated != null) repo.updateSample(updated)
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
                toAdd += OrderWellToAdd(myOrderId, well)
            }
        }
        return WellPlan(toAdd)
    }

    suspend fun applyWellPlan(repo: DatabaseRepository, plan: WellPlan) {
        for (add in plan.toAdd) repo.addWell(add.myOrderId, add.wellNumber)
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
                    theirSampleId, mySampleId, myText, theirText
                )
                !myHas && theirHas -> toAdd += NoteToAdd(mySampleId, theirText)
            }
        }
        return NotePlan(toAdd, conflicts)
    }

    suspend fun applyNotePlan(
        repo: DatabaseRepository,
        plan: NotePlan,
        resolutions: Map<Long, FieldResolution>
    ) {
        for (add in plan.toAdd) {
            repo.upsertNote(
                SampleNoteEntity(0, add.mySampleId, add.text, System.currentTimeMillis())
            )
        }
        for (c in plan.conflicts) {
            val r = resolutions[c.theirSampleId] ?: FieldResolution.Empty
            if (r.ownerOf(SampleField.MATERIAL_DESC) == FieldOwner.THEIRS) {
                repo.upsertNote(
                    SampleNoteEntity(0, c.mySampleId, c.theirText, System.currentTimeMillis())
                )
            }
        }
    }

    // ================================================================
    // /3: фото
    // ================================================================

    fun extractArchivePhotoName(imagePath: String): String? {
        if (imagePath.isBlank()) return null
        return imagePath.substringAfterLast('/').takeIf { it.isNotBlank() }
    }

    fun planPhotos(
        theirImages: List<SampleImageEntity>,
        sampleIdMap: Map<Long, Long>,
        conflictSampleIds: Set<Long>,
        resolutions: Map<Long, FieldResolution>
    ): PhotoPlan {
        val toAdd = mutableListOf<PhotoToAdd>()
        for (img in theirImages) {
            val mySampleId = sampleIdMap[img.sampleId] ?: continue
            if (img.sampleId in conflictSampleIds) {
                val r = resolutions[img.sampleId]
                val anyTheirs = r?.map?.values?.any { it == FieldOwner.THEIRS } == true
                if (!anyTheirs) continue
            }
            val name = extractArchivePhotoName(img.imagePath) ?: continue
            toAdd += PhotoToAdd(mySampleId, name)
        }
        return PhotoPlan(toAdd)
    }

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
                dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
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