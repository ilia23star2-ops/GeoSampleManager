package com.example.geosamplemanager.data.settings

import android.content.Context
import com.google.gson.Gson
import java.io.File

/**
 * FIX 5.9-settings-scale:
 * Настройки внешнего вида. Масштаб интерфейса.
 *
 * FIX 5.9-settings-scale-2:
 * Разделены множители: textFactor (fontScale) и densityFactor (density).
 *
 * FIX 5.9-settings-theme:
 * Добавлено поле theme (AppTheme). Применяется в MainActivity —
 * передаётся в GeoSampleManagerTheme как darkTheme.
 */
data class AppearanceSettings(
    val scale: UiScale = UiScale.NORMAL,
    val theme: AppTheme = AppTheme.SYSTEM
)

/**
 * Пресеты масштаба интерфейса.
 *
 * textFactor — множитель к системному fontScale. Влияет только на
 * текст: заголовки, надписи, содержимое таблиц.
 *
 * densityFactor — множитель к density. Влияет на отступы, высоты,
 * иконки. Держим маленьким, чтобы вёрстка не разваливалась.
 */
enum class UiScale(
    val title: String,
    val textFactor: Float,
    val densityFactor: Float
) {
    NORMAL("Обычный", 1.00f, 1.00f),
    LARGE("Крупнее", 1.15f, 1.05f),
    HUGE("Крупный", 1.30f, 1.10f);

    companion object {
        fun fromName(name: String?): UiScale {
            if (name.isNullOrBlank()) return NORMAL
            return values().firstOrNull { it.name == name } ?: NORMAL
        }
    }
}

/**
 * FIX 5.9-settings-theme:
 * Тема оформления приложения.
 *
 * SYSTEM — следовать системной настройке (по умолчанию).
 * LIGHT  — всегда светлая.
 * DARK   — всегда тёмная.
 */
enum class AppTheme(val title: String) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Тёмная");

    companion object {
        fun fromName(name: String?): AppTheme {
            if (name.isNullOrBlank()) return SYSTEM
            return values().firstOrNull { it.name == name } ?: SYSTEM
        }
    }
}

/**
 * Репозиторий настроек внешнего вида.
 *
 * Загрузка синхронная — файл маленький, читается один раз
 * при старте приложения и при открытии раздела «Внешний вид».
 */
class AppearanceSettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val gson = Gson()

    private val file: File
        get() = File(appContext.filesDir, FILE_NAME)

    fun load(): AppearanceSettings {
        return try {
            val f = file
            if (!f.exists()) return AppearanceSettings()
            val parsed = gson.fromJson(f.readText(), AppearanceSettings::class.java)
                ?: return AppearanceSettings()
            // Защита от некорректных значений: поле может быть null
            // (например, старый JSON без этого поля) — тогда NPE
            // поймается блоком catch, но лучше явно.
            AppearanceSettings(
                scale = UiScale.fromName(runCatching { parsed.scale.name }.getOrNull()),
                theme = AppTheme.fromName(runCatching { parsed.theme.name }.getOrNull())
            )
        } catch (e: Exception) {
            AppearanceSettings()
        }
    }

    fun save(settings: AppearanceSettings) {
        try {
            file.writeText(gson.toJson(settings))
        } catch (e: Exception) {
            // Настройки не критичны — молча игнорируем.
        }
    }

    companion object {
        private const val FILE_NAME = "appearance_settings.json"
    }
}