package com.example.geosamplemanager.data.settings

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Чтение/запись настроек импорта.
 * Основной файл — filesDir/import_settings.json.
 * Дополнительно умеет экспортировать/импортировать в произвольный Uri.
 */
class SettingsRepository(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val settingsFile: File
        get() = File(context.filesDir, FILE_NAME)

    // ============ Основные операции ============

    fun load(): ImportSettings {
        return try {
            if (!settingsFile.exists()) return ImportSettings()
            val text = settingsFile.readText()
            if (text.isBlank()) return ImportSettings()
            gson.fromJson(text, ImportSettings::class.java) ?: ImportSettings()
        } catch (e: Exception) {
            e.printStackTrace()
            ImportSettings()
        }
    }

    fun save(settings: ImportSettings) {
        try {
            settingsFile.writeText(gson.toJson(settings))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resetToDefaults(): ImportSettings {
        val defaults = ImportSettings()
        save(defaults)
        return defaults
    }

    // ============ Экспорт / импорт во внешний файл ============

    suspend fun exportTo(uri: Uri, settings: ImportSettings): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val json = gson.toJson(settings)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                } ?: return@withContext Result.failure(IllegalStateException("Не удалось открыть файл"))
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun importFrom(uri: Uri): Result<ImportSettings> =
        withContext(Dispatchers.IO) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                } ?: return@withContext Result.failure(IllegalStateException("Не удалось открыть файл"))

                val parsed = gson.fromJson(text, ImportSettings::class.java)
                    ?: return@withContext Result.failure(IllegalStateException("Пустой JSON"))
                Result.success(parsed)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    companion object {
        private const val FILE_NAME = "import_settings.json"
    }
}