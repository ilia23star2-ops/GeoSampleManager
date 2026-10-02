package com.example.geosamplemanager.data.merge

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.example.geosamplemanager.data.AppDatabase
import com.example.geosamplemanager.data.DatabaseRepository
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * FIX 5.9-db-merge-v2/1:
 * Движок слияния текущей БД с выбранным .gsmbackup.
 *
 * /1 работает с участками и нарядами:
 *  1. Открывает архив во временной Room-БД (openArchive).
 *  2. Строит план слияния по участкам (planAreas).
 *  3. Строит план слияния по нарядам (planOrders).
 *  4. Применяет планы через репозиторий (applyAreaPlan, applyOrderPlan).
 *  5. Закрывает и удаляет temp-БД (closeAndClean).
 *
 * Пробы, скважины, заметки, фото — в /2 и /3.
 * UI — в /4.
 *
 * Чистые функции planAreas / planOrders покрыты юнит-тестами.
 */
object MergeEngine {

    /** Ожидаемая версия схемы БД в архиве. */
    const val SUPPORTED_SCHEMA_VERSION = 2

    /** Имя БД внутри zip-архива. */
    const val DB_ENTRY = "geosamples.db"

    // ================================================================
    // Открытие / закрытие архива
    // ================================================================

    /**
     * Распаковать geosamples.db из архива во временный файл,
     * проверить версию схемы, открыть Room.
     *
     * Все ошибки — через Result.failure. Временный файл удаляется
     * при ошибке или вызывающим (closeAndClean).
     */
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

    /**
     * Закрыть временную БД и удалить её файл. Безопасно вызывать
     * повторно. Не бросает исключений.
     */
    fun closeAndClean(handle: TempDatabaseHandle) {
        try { handle.db.close() } catch (_: Exception) {}
        try { if (handle.file.exists()) handle.file.delete() } catch (_: Exception) {}
    }

    // ================================================================
    // Планы слияния — чистые функции
    // ================================================================

    /**
     * План слияния участков.
     *
     *  - Совпадение — по точному равенству area_name.
     *  - Если у меня несколько участков с одним именем — целевой
     *    MIN(id) (первый заведённый).
     *  - Совпавшие — в existing (theirId -> myId).
     *  - Остальные — в toAdd, с сохранением исходного theirId.
     *  - Имена с дублями у меня — в duplicatesInMine (для UI).
     */
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

    /**
     * План слияния нарядов.
     *
     * @param areaIdMap мап их areaId -> мой areaId для уже
     *        существующих участков (из planAreas().existing).
     * @param newlyAddedAreaIds мап their areaId -> новый мой areaId
     *        для участков, которые будут созданы (после applyAreaPlan).
     *        Если apply ещё не вызывался — передать пустой map,
     *        тогда все наряды новых участков уедут в skippedOrphans.
     *
     *  - Совпадение — по паре (myAreaId, order_number).
     *  - Если (myAreaId, orderNumber) уже есть — в existing.
     *  - Если наряд ссылается на неизвестный участок — в skipped.
     *  - Остальные — в toAdd с пересчитанным areaId.
     */
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

    // ================================================================
    // Применение планов
    // ================================================================

    /**
     * Вставить участки из toAdd через репозиторий.
     * Возвращает объединённый мап их areaId -> мой areaId
     * (existing + вновь созданные).
     */
    suspend fun applyAreaPlan(
        repo: DatabaseRepository,
        plan: AreaPlan
    ): Map<Long, Long> {
        val result = HashMap<Long, Long>(plan.existing)
        for (add in plan.toAdd) {
            val newId = repo.addArea(add.entity.areaName)
            if (newId > 0) {
                result[add.theirId] = newId
            }
        }
        return result
    }

    /**
     * Вставить наряды из toAdd через репозиторий.
     * Возвращает объединённый мап их orderId -> мой orderId.
     */
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
            if (newId > 0) {
                result[add.theirId] = newId
            }
        }
        return result
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