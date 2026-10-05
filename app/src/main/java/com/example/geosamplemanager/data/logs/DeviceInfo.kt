package com.example.geosamplemanager.data.logs

import android.content.Context
import android.os.Build

/**
 * FIX 5.9-logs-3:
 * Снимок данных об устройстве и приложении на момент старта.
 * Пишется один раз в начале сессии, в details записи app_start.
 *
 * Формат — Map<String, Any?>, чтобы DetailsJson.encode
 * превратил в плоский JSON.
 *
 * В JSON-ключи — английские (это technical details, не summary).
 * Правило §2.4 (без английского) относится к summary и меткам UI.
 */
object DeviceInfo {

    fun snapshot(context: Context): Map<String, Any?> {
        val appVersion = try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }

        return linkedMapOf(
            "device_model" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "android_release" to Build.VERSION.RELEASE,
            "android_sdk" to Build.VERSION.SDK_INT,
            "app_version" to appVersion,
            "package" to context.packageName
        )
    }
}