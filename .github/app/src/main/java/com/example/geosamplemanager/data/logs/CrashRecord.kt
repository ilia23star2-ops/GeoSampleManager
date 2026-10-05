package com.example.geosamplemanager.data.logs

import com.google.gson.Gson

/**
 * FIX 5.9-logs-3:
 * Запись о падении приложения. Хранится в файле
 * filesDir/logs/pending_crash.json до следующего старта.
 *
 * Почему файл, а не сразу БД: в момент необработанного
 * исключения процесс может быть в неработоспособном состоянии,
 * канал LogWriter'а может не дойти до записи в logs.db. Файл —
 * самый надёжный способ сохранить данные до того, как процесс
 * умрёт. При следующем старте читаем файл → пишем в журнал →
 * удаляем файл.
 */
data class CrashRecord(
    val timestamp: Long,
    val sessionId: String,
    val threadName: String,
    val exceptionType: String,
    val message: String?,
    val stackTrace: String
) {

    fun encode(): String = gson.toJson(this)

    companion object {

        private val gson = Gson()

        fun decode(json: String): CrashRecord? = try {
            gson.fromJson(json, CrashRecord::class.java)
        } catch (_: Exception) {
            null
        }
    }
}