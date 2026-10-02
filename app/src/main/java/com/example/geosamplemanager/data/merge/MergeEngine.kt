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
 *
 * Чистые функции (planAreas, planOrders, planSamples, resolveSample,
 * planWells, planNotes) покрыты юнит-тестами. IO-функции
 * (openArchive, apply*) — device-check.
 */
object MergeEngine {

    const val SUPPORTED_SCHEMA_VERSION = 2
    const val DB_ENTRY = "geosamples.db"

    // ================================================================
    // Открытие / закрытие архива
    // ================================================================

    suspend fun openArchive(
        context: Context,
        uri: Uri,
        cacheDir: File
    ): Result<TempDatabaseHandle> = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            if (!cacheDir.exists()) cacheDir.mkdirs()
            tempFile = File(cacheDir, "merge_temp_${UUID.randomUUID()}.db")

            val extracted = extractDbFromUri(context, uri, tempFile)
            if (!extracted) {
                tempFile.delete()
                return@withContext Result.failure(
                    IllegalStateException("В архиве нет geosamples.db")
                )
            }

            val version = readSchemaVersion(tempFile)
            if (version != SUPPORTED_SCHEMA_VERSION) {
                tempFile.delete()
                return@withContext Result.failure(
                    IllegalStateException(
                        "Схема БД в архиве: $version, " +
                                "ожидается $SUPPORTED_SCHEMA_VERSION"
                    )
                )
            }

            val db = AppDatabase.buildTemp(context, tempFile)
            Result.success(TempDatabaseHandle(db, tempFile))
        } catch (e: Exception) {
            try { tempFile?.delete() } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    fun closeAndClean(handle: TempDatabaseHandle) {
        try { handle.db.close() } catch (_: Exception) {}
        try { if (handle.file.exists()) handle.file.delete() } catch (_: Exception) {}
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

    /**
     * План слияния проб.
     *
     *  - Совпадение — по (myOrderId, sample_number).
     *  - При совпадении — конфликт, в toAdd не попадает.
     *  - Если their.order_id не сматчился — skip.
     *  - При добавлении: обнуляем id, ставим myOrderId.
     *    has_note / has_photo — из архива, пересчитаем после
     *    применения заметок и фото.
     */
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

    /**
     * FIX 5.9-db-merge-v2/2:
     * Разрешить конфликт одной пробы.
     *
     * KEEP_MINE — вернуть null (не трогаем).
     * TAKE_THEIRS — вернуть обновлённый entity моего sample:
     *    - копируем все изменяемые поля из архива;
     *    - НЕ трогаем: id, order_id, sample_number, has_photo, has_note.
     */
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
            // hasNote / hasPhoto — не из архива, пересчитаются
            // после применения заметок и фото.
        )
    }

    /**
     * Применить план проб.
     *
     * @param resolutions карта theirSampleId -> Resolution. Если для
     *        конфликта ключа нет — считаем KEEP_MINE (безопасный
     *        дефолт).
     * @return Map<theirSampleId, mySampleId> — для новых и существующих.
     */
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

    /**
     * План слияния скважин.
     *
     * @param myWells Map<myOrderId, List<wellNumber>> — что уже есть.
     * @param theirWells Map<theirOrderId, List<wellNumber>> — из архива.
     * @param orderIdMap Map<theirOrderId, myOrderId>.
     *
     * Дедупликация по паре (myOrderId, wellNumber).
     */
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
            // Локальный набор для отлова дублей внутри самого архива.
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

    /**
     * План слияния заметок.
     *
     * @param myNotes Map<mySampleId, noteText?>.
     * @param theirNotes Map<theirSampleId, noteText?>.
     * @param sampleIdMap Map<theirSampleId, mySampleId>.
     *
     *  - Если заметки у меня нет, а у них есть (или наоборот) —
     *    записываем ту, что непустая.
     *  - Если у обоих непустые — конфликт, решается по тому же
     *    их sampleId.
     *  - Если both пустые — пропускаем.
     */
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
                myHas && theirHas -> {
                    conflicts += NoteConflict(
                        theirSampleId = theirSampleId,
                        mySampleId = mySampleId,
                        myText = myText,
                        theirText = theirText
                    )
                }
                !myHas && theirHas -> {
                    toAdd += NoteToAdd(
                        mySampleId = mySampleId,
                        text = theirText
                    )
                }
                // myHas && !theirHas -> оставляем мою
                // both пустые -> пропускаем
            }
        }

        return NotePlan(toAdd = toAdd, conflicts = conflicts)
    }

    /**
     * Применить план заметок.
     *
     * @param resolutions Map<theirSampleId, Resolution>. Дефолт —
     *        KEEP_MINE (то есть оставляем мою заметку).
     */
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
    // Внутреннее
    // ================================================================

    private fun extractDbFromUri(
        context: Context,
        uri: Uri,
        target: File
    ): Boolean {
        return try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return false
            input.use { stream ->
                val zip = ZipInputStream(stream)
                var entry = zip.nextEntry
                var written = false
                while (entry != null) {
                    if (entry.name == DB_ENTRY) {
                        target.outputStream().use { out -> zip.copyTo(out) }
                        written = true
                        break
                    }
                    entry = zip.nextEntry
                }
                try { zip.close() } catch (_: Exception) {}
                written
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