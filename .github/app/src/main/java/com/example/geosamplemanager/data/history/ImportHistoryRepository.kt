package com.example.geosamplemanager.data.history

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * История импортов в filesDir/import_history.json.
 * Свежие записи — в начале списка. Храним не больше 50 записей.
 */
class ImportHistoryRepository(context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val file: File get() = File(context.filesDir, FILE_NAME)

    fun load(): List<ImportHistoryEntry> {
        return try {
            if (!file.exists()) return emptyList()
            val text = file.readText()
            if (text.isBlank()) return emptyList()
            val type = object : TypeToken<List<ImportHistoryEntry>>() {}.type
            gson.fromJson<List<ImportHistoryEntry>>(text, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun add(entry: ImportHistoryEntry) {
        try {
            val current = load().toMutableList()
            current.add(0, entry)
            val trimmed = current.take(MAX_ENTRIES)
            file.writeText(gson.toJson(trimmed))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clear() {
        try {
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val FILE_NAME = "import_history.json"
        private const val MAX_ENTRIES = 50
    }
}