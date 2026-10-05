package com.example.geosamplemanager.data.logs

/**
 * FIX 5.9-logs-4a:
 * Псевдоним для Log — чтобы не конфликтовать с android.util.Log
 * в файлах, где уже импортирован системный логгер.
 *
 * Использование:
 *   import com.example.geosamplemanager.data.logs.AppLog
 *   AppLog.voice("Голос: «отметь третью»").detail("raw", text).write()
 *
 * Не требует править существующие вызовы android.util.Log.i/e/w —
 * они остаются для отладки в logcat. AppLog пишет в журнал сессии
 * (logs.db).
 */
typealias AppLog = Log