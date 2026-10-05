package com.example.geosamplemanager.data.bluetooth

/**
 * FIX 5.9-settings-bt:
 * Устройство Bluetooth для UI. Встроенный микрофон — тоже
 * BtDevice с флагом isBuiltIn = true, чтобы список в UI был
 * единообразным.
 */
data class BtDevice(
    val address: String,
    val name: String,
    val isBuiltIn: Boolean = false
) {
    companion object {
        /** Псевдо-устройство: встроенный микрофон телефона. */
        val BUILT_IN = BtDevice(
            address = "__built_in__",
            name = "Встроенный микрофон",
            isBuiltIn = true
        )
    }
}