package com.example.geosamplemanager.data.voice

/**
 * Callback от VoiceController → VoiceDialog.
 *
 * Заменяет лямбды — чтобы можно было логировать в каждом вызове
 * и точно знать, что APK содержит актуальный код.
 */
interface VoiceCallback {
    fun onReady()
    fun onPartial(text: String)
    fun onResult(text: String)
    fun onError(message: String)
}