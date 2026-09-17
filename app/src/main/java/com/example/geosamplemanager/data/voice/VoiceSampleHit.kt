package com.example.geosamplemanager.data.voice

/**
 * Проба, найденная голосовым поиском.
 *
 * FIX 5.8.9h-2b-ii: класс VoiceSearch удалён, оставлены только модели.
 * Используется в UnifiedSearch, VoiceSearchRepository и UI.
 */
data class VoiceSampleHit(
    val sampleId: Long,
    val sampleNumber: String,
    val wellNumber: String,
    val orderId: Long,
    val orderNumber: String,
    val areaTitle: String
)

/**
 * Источник данных для голосового поиска.
 */
interface VoiceSampleSource {
    suspend fun loadAll(): List<VoiceSampleHit>
}