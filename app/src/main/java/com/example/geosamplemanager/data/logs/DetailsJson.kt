package com.example.geosamplemanager.data.logs

import com.google.gson.Gson

/**
 * FIX 5.9-logs-2:
 * Формирование поля details — JSON со всеми техническими
 * данными записи (id, коды, тип ошибки, stacktrace).
 *
 * Оборачивает Gson (уже в зависимостях — app/build.gradle).
 * Свой форматтер не пишем: Gson умеет Map<String, Any?> и
 * корректно обрабатывает примитивы, строки, null.
 *
 * null-значения Gson по умолчанию пропускает — это ок:
 * в details не нужно «message: null», важнее компактность.
 */
object DetailsJson {

    private val gson = Gson()

    fun encode(map: Map<String, Any?>): String = gson.toJson(map)

    /**
     * Разбор JSON обратно в Map — для UI (logs-5),
     * где details раскрываются для просмотра.
     */
    @Suppress("UNCHECKED_CAST", "unused")
    fun decode(json: String): Map<String, Any?>? {
        return try {
            gson.fromJson(json, Map::class.java) as? Map<String, Any?>
        } catch (_: Exception) {
            null
        }
    }
}