package com.example.geosamplemanager.data.voice

/**
 * FIX 5.9-settings-sound:
 * Уровень громкости звуковых сигналов (бипов).
 *
 * Отдельно от TtsVolume: TTS — это озвучка фраз, сигналы — короткие
 * бипы. Регулируются независимо.
 *
 * Полного отключения нет — только уровень. Для выключения сигналов
 * есть отдельный флаг VoiceSettings.feedbackEnabled.
 *
 * intensity — громкость для ToneGenerator (0..100).
 */
enum class SoundLevel(val label: String, val intensity: Int) {
    QUIET("Тихая", 40),
    NORMAL("Обычная", 80),
    LOUD("Громкая", 100)
}