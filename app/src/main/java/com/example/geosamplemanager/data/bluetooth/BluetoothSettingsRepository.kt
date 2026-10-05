package com.example.geosamplemanager.data.bluetooth

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * FIX 5.9-settings-bt:
 * Репозиторий настроек Bluetooth. Файл:
 * filesDir/bluetooth_settings.json.
 *
 * Отдельно от voice_settings.json — BT-настройки не относятся
 * к голосу.
 */
class BluetoothSettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val gson = Gson()

    private val file: File
        get() = File(appContext.filesDir, FILE_NAME)

    suspend fun load(): BluetoothSettings = withContext(Dispatchers.IO) {
        try {
            val f = file
            if (!f.exists()) return@withContext BluetoothSettings()
            val json = f.readText()
            gson.fromJson(json, BluetoothSettings::class.java) ?: BluetoothSettings()
        } catch (_: Exception) {
            BluetoothSettings()
        }
    }

    suspend fun save(settings: BluetoothSettings) = withContext(Dispatchers.IO) {
        try {
            file.writeText(gson.toJson(settings))
        } catch (_: Exception) {
            // Настройки не критичны — молча игнорируем.
        }
    }

    companion object {
        private const val FILE_NAME = "bluetooth_settings.json"
    }
}