package com.example.geosamplemanager.data.voice

import android.content.Context
import com.example.geosamplemanager.data.AppDatabase

/**
 * Источник данных для VoiceSearch — берёт пробы из БД.
 *
 * Работает с SampleDao через JOIN-запрос, чтобы сразу получить
 * order_number и area_name без N+1.
 */
class VoiceSearchRepository(context: Context) : VoiceSampleSource {

    private val db = AppDatabase.getInstance(context.applicationContext)

    override suspend fun loadAll(): List<VoiceSampleHit> =
        db.sampleDao().getAllForVoiceSearch()
}