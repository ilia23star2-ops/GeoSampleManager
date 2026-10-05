package com.example.geosamplemanager.data.bluetooth

/**
 * FIX 5.9-settings-bt:
 * Настройки Bluetooth-микрофона. Хранятся отдельно от
 * VoiceSettings — BT не относится к голосу напрямую.
 *
 * Файл: filesDir/bluetooth_settings.json.
 */
data class BluetoothSettings(
    /** Использовать внешнюю BT-гарнитуру. */
    val enabled: Boolean = false,

    /** MAC-адрес выбранного устройства. null — не выбрано. */
    val deviceAddress: String? = null,

    /** Имя для UI (может устареть, если устройство переименовали). */
    val deviceName: String? = null,

    /** Предпочитаемый профиль: SCO (разговор) или A2DP (медиа). */
    val profile: BtProfile = BtProfile.SCO,

    /** Если устройство отключилось — падать на встроенный микрофон. */
    val fallbackToBuiltIn: Boolean = true
)

/** Профиль Bluetooth-канала. */
enum class BtProfile(val label: String) {
    SCO("SCO"),
    A2DP("A2DP")
}