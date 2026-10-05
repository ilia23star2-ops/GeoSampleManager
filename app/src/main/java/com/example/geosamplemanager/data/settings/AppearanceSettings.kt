package com.example.geosamplemanager.data.settings

import android.content.Context
import com.google.gson.Gson
import java.io.File

/**
 * FIX 5.9-settings-scale:
 * Настройки внешнего вида. Пока — только масштаб интерфейса.
 *
 * FIX 5.9-settings-scale-2:
 * Разделены множители: textFactor (fontScale) и densityFactor (density).
 * Множить одну density — плохо: при крупном масштабе растут отступы
 * и высоты, элементы уезжают за экран. fontScale даёт крупный текст,
 * density чуть увеличивает сами элементы.
 */
data class AppearanceSettings(
    val scale: UiScale = UiScale.NORMAL
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
        /**
         * Безопасный разбор из строки: неизвестное значение →
         * NORMAL. Нужно, если в JSON лежит устаревшее имя
         * или мусор.
         */
        fun fromName(name: String?): UiScale {
            if (name.isNullOrBlank()) return NORMAL
            return values().firstOrNull { it.name == name } ?: NORMAL
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
                ?: AppearanceSettings()
            // Защита от некорректного значения scale.
            parsed.copy(scale = UiScale.fromName(parsed.scale.name))
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