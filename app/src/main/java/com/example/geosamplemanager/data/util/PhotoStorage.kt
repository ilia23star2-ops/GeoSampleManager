package com.example.geosamplemanager.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Утилита для работы с фото проб.
 *
 * Все файлы лежат в filesDir/sample_photos/ — внутреннее хранилище приложения.
 * Извне туда никто не подсунет, при удалении приложения файлы удаляются.
 *
 * Сжатие: до 1024 px по длинной стороне, JPEG 80%.
 * Ориентир размера: 100–300 КБ на файл.
 */
object PhotoStorage {

    private const val DIR_NAME = "sample_photos"
    private const val MAX_DIMENSION = 1024
    private const val JPEG_QUALITY = 80

    /** Папка для фото. Создаётся при первом обращении. */
    fun getPhotoDir(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Временный файл, в который система положит снимок камеры.
     * После сжатия удаляется.
     */
    fun createTempFile(context: Context): File {
        val dir = getPhotoDir(context)
        return File(dir, "tmp_${UUID.randomUUID()}.jpg")
    }

    /**
     * Uri для FileProvider.
     * authority = "${packageName}.fileprovider" — должно совпадать с манифестом.
     */
    fun getUriForFile(context: Context, file: File): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

    /**
     * Сжимает изображение из произвольного Uri (галерея / Photo Picker)
     * и сохраняет в filesDir/sample_photos/.
     *
     * @return абсолютный путь к сохранённому файлу или null при ошибке.
     */
    fun compressAndSave(context: Context, sourceUri: Uri): String? {
        return try {
            val input = context.contentResolver.openInputStream(sourceUri) ?: return null
            val original = BitmapFactory.decodeStream(input)
            input.close()
            if (original == null) return null
            saveScaled(context, original, tempToDelete = null)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Для фото, снятого через TakePicture: снимок уже лежит во временном файле.
     * Сжимаем, сохраняем под финальным именем, временный удаляем.
     */
    fun compressAndSaveFromFile(context: Context, tempFile: File): String? {
        return try {
            val original = BitmapFactory.decodeFile(tempFile.absolutePath)
            if (original == null) {
                tempFile.delete()
                return null
            }
            saveScaled(context, original, tempToDelete = tempFile)
        } catch (e: Exception) {
            tempFile.delete()
            null
        }
    }

    /** Удаляет файл по пути. Возвращает true, если файл удалён. */
    fun delete(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return try {
            File(path).delete()
        } catch (e: Exception) {
            false
        }
    }

    /** Удалить сразу несколько путей. */
    fun deleteAll(paths: Collection<String>) {
        paths.forEach { delete(it) }
    }

    // ================================================================
    // Внутреннее
    // ================================================================

    private fun saveScaled(context: Context, original: Bitmap, tempToDelete: File?): String? {
        val scaled = scaleDown(original, MAX_DIMENSION)
        val outFile = File(getPhotoDir(context), "photo_${UUID.randomUUID()}.jpg")
        FileOutputStream(outFile).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        if (scaled !== original) scaled.recycle()
        original.recycle()
        tempToDelete?.delete()
        return outFile.absolutePath
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val maxSide = maxOf(w, h)
        if (maxSide <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxSide
        val newW = (w * ratio).toInt().coerceAtLeast(1)
        val newH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }
}