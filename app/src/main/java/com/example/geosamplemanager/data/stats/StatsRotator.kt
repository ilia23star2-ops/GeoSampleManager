package com.example.geosamplemanager.data.stats

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/**
 * FIX 5.10-stat-daily-file-2:
 * Месячная ротация stats.db. `active.db` → `archive/YYYY-MM.db`.
 *
 * Логика:
 *  - при старте приложения вызывается checkAndRotate();
 *  - если данные в active.db за прошлый месяц, а сейчас новый —
 *    файл переезжает в archive/, при следующем getInstance()
 *    создаётся пустой active.db;
 *  - если month-архив уже существует — не ротируем (защита от
 *    потери данных), warn в лог.
 *
 * Решения:
 *  - У7: месяц определяется по локальной зоне устройства;
 *  - У8=В: коллизия в archive/ — пропускаем ротацию, warn;
 *  - У9=А: при ошибке I/O продолжаем писать в active.db;
 *  - У10=А: чистая shouldRotate() (тестируется в JVM)
 *    + suspend checkAndRotate() (I/O).
 *
 * Порядок операций в checkAndRotate (важно — см. CONTEXT_BRIEF):
 *  1. Прочитать MAX(started_at) из active.db.
 *  2. StatsDatabase.closeAndReset() — закрыть соединение.
 *  3. Удалить side-файлы active.db-wal / active.db-shm.
 *  4. Переименовать active.db → archive/YYYY-MM.db.
 *  5. Ничего не создавать — при следующем getInstance() активная
 *     БД создастся заново.
 *
 * FIX 5.10-stat-daily-file-2 (фикс компиляции):
 *  - в checkAndRotate явная проверка `lastMonth == null` —
 *    иначе компилятор не делает smart cast и ругается на
 *    `String?` в местах, где ожидается `String`.
 */
object StatsRotator {

    private const val TAG = "StatsRotator"

    private const val ARCHIVE_DIR = "archive"

    // ============================================================
    // Чистая логика (тестируется в JVM)
    // ============================================================

    /**
     * Ключ месяца `YYYY-MM` для timestamp в указанной зоне.
     * Формат совпадает с именем файла архива.
     */
    fun monthKey(ts: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val cal = Calendar.getInstance(tz)
        cal.timeInMillis = ts
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        return "%04d-%02d".format(y, m)
    }

    /**
     * Ключ текущего месяца.
     */
    fun currentMonthKey(tz: TimeZone = TimeZone.getDefault()): String =
        monthKey(System.currentTimeMillis(), tz)

    /**
     * Нужно ли ротировать.
     *  - lastDataMonth == null (нет данных) → false, нечего архивировать;
     *  - lastDataMonth == currentMonth → false;
     *  - lastDataMonth != currentMonth → true.
     */
    fun shouldRotate(lastDataMonth: String?, currentMonth: String): Boolean {
        if (lastDataMonth.isNullOrBlank()) return false
        return lastDataMonth != currentMonth
    }

    // ============================================================
    // I/O — собственно ротация
    // ============================================================

    sealed class RotateResult {
        /** Нет файла, нет данных, или месяц совпадает — нечего делать. */
        data object Noop : RotateResult()

        /** В archive/ уже есть файл за этот месяц. Данные не теряем. */
        data class Collision(val month: String) : RotateResult()

        /** Ротация выполнена. */
        data class Rotated(val month: String, val archivedFile: File) : RotateResult()

        /** I/O-ошибка: продолжаем писать в active.db. */
        data class Failed(val error: String) : RotateResult()
    }

    /**
     * Проверить и при необходимости выполнить ротацию.
     * Безопасно вызывать при каждом старте приложения — при
     * отсутствии файла или совпадении месяца это no-op.
     *
     * Вызывать **до** SessionTracker.onAppStart() — иначе новая
     * сессия уйдёт в старый active.db, который потом переедет в
     * архив.
     */
    suspend fun checkAndRotate(context: Context): RotateResult {
        val active = StatsDatabase.activeFile(context)
        if (!active.exists()) return RotateResult.Noop

        // 1. Прочитать MAX(started_at) из active.db. Если БД пуста —
        //    берём mtime файла.
        val maxTs: Long? = try {
            val dao = StatsDatabase.getInstance(context).statsDao()
            dao.getMaxSessionStartedAt()
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось прочитать max(started_at): ${e.message}")
            null
        }

        val fallbackTs = active.lastModified().takeIf { it > 0L }
        val lastMonth: String? = (maxTs ?: fallbackTs)?.let { monthKey(it) }
        val currentMonth = currentMonthKey()

        // FIX 5.10-stat-daily-file-2 (фикс): явная проверка null +
        // shouldRotate, чтобы дальше был smart cast в non-null String.
        if (lastMonth == null || !shouldRotate(lastMonth, currentMonth)) {
            return RotateResult.Noop
        }

        // 2. Проверка коллизии.
        val archiveDir = File(StatsDatabase.statsDir(context), ARCHIVE_DIR)
        if (!archiveDir.exists()) archiveDir.mkdirs()
        val dest = File(archiveDir, "$lastMonth.db")
        if (dest.exists()) {
            Log.w(TAG, "Ротация пропущена: архив $lastMonth уже существует")
            return RotateResult.Collision(lastMonth)
        }

        // 3. Закрыть соединение — WAL должен checkpoint'нуться
        //    автоматически. Затем удалить side-файлы.
        StatsDatabase.closeAndReset()
        deleteSideFiles(active)

        // 4. Переезд.
        val ok = try {
            active.renameTo(dest)
        } catch (e: Exception) {
            Log.e(TAG, "renameTo упал", e)
            false
        }
        if (!ok) {
            Log.e(TAG, "Не удалось переименовать active.db в $dest")
            return RotateResult.Failed("Не удалось переименовать active.db")
        }

        Log.i(TAG, "Ротация выполнена: $lastMonth → ${dest.absolutePath}")
        return RotateResult.Rotated(lastMonth, dest)
    }

    // ============================================================
    // Внутренняя часть
    // ============================================================

    private fun deleteSideFiles(dbFile: File) {
        try { File(dbFile.absolutePath + "-wal").delete() } catch (_: Exception) {}
        try { File(dbFile.absolutePath + "-shm").delete() } catch (_: Exception) {}
    }
}