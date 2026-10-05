package com.example.geosamplemanager.data.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * FIX 5.9-settings-bt:
 * Обёртка над системными Bluetooth-API.
 *
 * Задачи:
 *  - список сопряжённых устройств;
 *  - состояние адаптера;
 *  - переключение SCO-канала (для микрофона ГП);
 *  - открытие системных настроек BT (сопряжение новых устройств).
 *
 * Сопряжение новых устройств приложением не делается — только через
 * системные настройки. Это ограничение Android.
 */
class BluetoothController(private val context: Context) {

    private val audioManager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val bluetoothManager: BluetoothManager?
        get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    // ================================================================
    // Разрешения
    // ================================================================

    fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ================================================================
    // Состояние
    // ================================================================

    fun isBluetoothEnabled(): Boolean {
        if (!hasPermission()) return false
        return try {
            adapter?.isEnabled == true
        } catch (_: SecurityException) {
            false
        }
    }

    fun isSupported(): Boolean = adapter != null

    // ================================================================
    // Список устройств
    // ================================================================

    /**
     * Сопряжённые устройства. Фильтр: только аудио-класс
     * (гарнитуры, наушники, колонки). Устройства без класса
     * показываются — на некоторых прошивках класс не отдаётся.
     */
    fun listPairedDevices(): List<BtDevice> {
        if (!hasPermission()) return emptyList()
        val a = adapter ?: return emptyList()
        if (!a.isEnabled) return emptyList()

        val result = mutableListOf<BtDevice>()
        try {
            a.bondedDevices?.forEach { device ->
                if (!isAudioClass(device.bluetoothClass)) return@forEach
                result.add(
                    BtDevice(
                        address = device.address,
                        name = device.name ?: "Без имени",
                        isBuiltIn = false
                    )
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "listPairedDevices: SecurityException", e)
        }
        return result.sortedBy { it.name.lowercase() }
    }

    /**
     * true, если класс устройства — аудио (наушники, гарнитура,
     * колонка). Если класс неизвестен — возвращаем true, чтобы
     * не пропустить рабочее устройство.
     */
    private fun isAudioClass(cls: BluetoothClass?): Boolean {
        val main = cls?.majorDeviceClass ?: return true
        return main == BluetoothClass.Device.Major.AUDIO_VIDEO
    }

    // ================================================================
    // Открыть системные настройки BT
    // ================================================================

    fun openSystemSettings() {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "openSystemSettings: не удалось открыть", e)
        }
    }

    // ================================================================
    // SCO — канал для микрофона
    // ================================================================

    /**
     * Переключить звук в режим связи и включить SCO.
     * После этого AudioRecord берёт звук из BT-гарнитуры,
     * а не из встроенного микрофона.
     *
     * API < 31: используется startBluetoothSco / setBluetoothScoOn.
     * Эти методы deprecated с API 31, но продолжают работать
     * и поддерживаются для совместимости с minSdk 24.
     */
    @Suppress("DEPRECATION")
    fun startSco() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            Log.i(TAG, "SCO: включён")
        } catch (e: Exception) {
            Log.w(TAG, "startSco: ошибка", e)
        }
    }

    @Suppress("DEPRECATION")
    fun stopSco() {
        try {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
            Log.i(TAG, "SCO: выключен")
        } catch (e: Exception) {
            Log.w(TAG, "stopSco: ошибка", e)
        }
    }

    // ================================================================
    // Активное устройство
    // ================================================================

    /**
     * Текущее подключённое устройство (профиль HEADSET).
     * На API 31+ через BluetoothManager.getConnectedDevices.
     * На более ранних — null (можно доработать при необходимости).
     */
    fun currentActiveDevice(): BtDevice? {
        if (!hasPermission()) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return try {
            val devices = bluetoothManager?.getConnectedDevices(BluetoothProfile.HEADSET)
            devices?.firstOrNull()?.let { d ->
                BtDevice(
                    address = d.address,
                    name = d.name ?: "Без имени",
                    isBuiltIn = false
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "currentActiveDevice: SecurityException", e)
            null
        } catch (e: IllegalArgumentException) {
            // Планшет без профиля HEADSET (нет BT-звонков).
            // Ошибка "Profile not supported: 1" — профиль не поддерживается
            // на этом устройстве. Возвращаем null, UI покажет «Не выбрано».
            Log.i(TAG, "currentActiveDevice: профиль HEADSET не поддерживается", e)
            null
        }
    }

    companion object {
        private const val TAG = "BluetoothController"
    }
}