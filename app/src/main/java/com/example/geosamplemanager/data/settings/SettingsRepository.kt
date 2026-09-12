package com.example.geosamplemanager.data.settings

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Чтение/запись настроек импорта.
 * Основной файл — filesDir/import_settings.json.
 *
 * При загрузке, если это JSON старого формата (без новых полей),
 * заполняем отсутствующие поля дефолтами, сохраняя при этом уже
 * существующие (участки, правило наряда и т.п.).
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
            parse(text)
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

                val parsed = parse(text)
                Result.success(parsed)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ============ Разбор JSON с миграцией ============

    /**
     * Ручной разбор JSON — чтобы корректно применить дефолты для
     * полей, которых нет в старых файлах.
     */
    private fun parse(text: String): ImportSettings {
        val root: JsonObject = try {
            JsonParser.parseString(text).asJsonObject
        } catch (e: Exception) {
            return ImportSettings()
        }
        val defaults = ImportSettings()

        val areaPrefixes = readMapList(root, "areaPrefixes")
            .takeIf { it.isNotEmpty() } ?: defaults.areaPrefixes

        val orderSource = readEnum(root, "orderSource", OrderSource.values())
            ?: defaults.orderSource

        val orderNumberRule = readEnum(root, "orderNumberRule", OrderNumberRule.values())
            ?: defaults.orderNumberRule

        val defaultHeaderKeywords = readMapList(root, "defaultHeaderKeywords")
            .takeIf { it.isNotEmpty() } ?: defaults.defaultHeaderKeywords

        val userHeaderKeywords = readMapList(root, "userHeaderKeywords")

        val defaultTypeValueKeywords = readMapList(root, "defaultTypeValueKeywords")
            .takeIf { it.isNotEmpty() } ?: defaults.defaultTypeValueKeywords

        val userTypeValueKeywords = readMapList(root, "userTypeValueKeywords")

        val blankKeywords = readList(root, "blankKeywords")
            .takeIf { it.isNotEmpty() } ?: defaults.blankKeywords

        val skipBlanks = if (root.has("skipBlanksWithoutData"))
            root.get("skipBlanksWithoutData").asBoolean
        else defaults.skipBlanksWithoutData

        return ImportSettings(
            areaPrefixes = areaPrefixes,
            orderSource = orderSource,
            orderNumberRule = orderNumberRule,
            defaultHeaderKeywords = defaultHeaderKeywords,
            userHeaderKeywords = userHeaderKeywords,
            defaultTypeValueKeywords = defaultTypeValueKeywords,
            userTypeValueKeywords = userTypeValueKeywords,
            blankKeywords = blankKeywords,
            skipBlanksWithoutData = skipBlanks
        )
    }

    private fun readMapList(root: JsonObject, key: String): Map<String, List<String>> {
        if (!root.has(key)) return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            gson.fromJson<Map<String, List<String>>>(root.get(key), type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun readList(root: JsonObject, key: String): List<String> {
        if (!root.has(key)) return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(root.get(key), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun <T : Enum<T>> readEnum(root: JsonObject, key: String, values: Array<T>): T? {
        if (!root.has(key)) return null
        return try {
            val name = root.get(key).asString
            values.firstOrNull { it.name == name }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val FILE_NAME = "import_settings.json"
    }
}