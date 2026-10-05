package com.example.geosamplemanager.data.backup

import android.content.Context
import com.example.geosamplemanager.data.DatabaseRepository
import java.io.File

/**
 * FIX 5.9-exit:
 * Резервный бэкап перед выходом из приложения.
 *
 * Файл — один, фиксированный, перезаписывается при каждом выходе.
 * Не участвует в ротации, не удаляется из UI менеджера бэкапов.
 *
 * Путь: filesDir/exit_backup/last_exit.gsmbackup
 *
 * Операция в манифесте — "exit". Если в GsmBackupWriter есть
 * валидация операций по списку — добавь "exit" в этот список.
 */
object ExitBackupWriter {

    private const val DIR_NAME = "exit_backup"
    const val FILE_NAME = "last_exit.gsmbackup"
    const val OP_EXIT = "exit"

    fun backupDir(context: Context): File =
        File(context.filesDir, DIR_NAME)

    fun backupFile(context: Context): File =
        File(backupDir(context), FILE_NAME)

    fun exists(context: Context): Boolean = backupFile(context).exists()

    /**
     * Создать бэкап. Перезаписывает существующий файл.
     * Пишем во временный файл, потом переименовываем — чтобы при
     * обрыве не потерять предыдущий бэкап.
     *
     * Возвращает true при успехе.
     */
    fun write(
        context: Context,
        repo: DatabaseRepository,
        appVersion: String,
        counts: BackupCounts,
        dbSchemaVersion: Int,
        operation: String = OP_EXIT
    ): Boolean {
        return try {
            val dir = backupDir(context)
            if (!dir.exists()) dir.mkdirs()

            val target = backupFile(context)
            val tmp = File(dir, "$FILE_NAME.tmp")
            if (tmp.exists()) tmp.delete()

            tmp.outputStream().use { out ->
                repo.checkpointWal()
                GsmBackupWriter.write(
                    out = out,
                    dbFile = repo.getDatabaseFile(),
                    photosDir = repo.getPhotosDir(),
                    dbSchemaVersion = dbSchemaVersion,
                    appVersion = appVersion,
                    counts = counts,
                    operation = operation
                )
            }

            if (target.exists()) target.delete()
            tmp.renameTo(target)
        } catch (_: Exception) {
            false
        }
    }
}