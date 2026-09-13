package com.example.geosamplemanager.data.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import java.io.File

/**
 * Подготовка модели Vosk.
 *
 * Копируем модель из assets/vosk-model-small-ru в filesDir/vosk-model-small-ru
 * вручную, без StorageService. Это надёжнее — не зависим от внутренней
 * логики Vosk с её маркером `.uuid`.
 *
 * При повторных запусках копирование пропускается: если файл-маркер
 * am/final.mdl на месте, модель уже распакована.
 */
object VoiceModelPreparer {

    private const val TAG = "VoiceModelPreparer"
    private const val ASSET_DIR = "vosk-model-small-ru"
    private const val MARKER_RELATIVE = "am/final.mdl"

    suspend fun prepare(
        context: Context,
        onStatus: (String) -> Unit = {}
    ): Result<Model> = withContext(Dispatchers.IO) {
        try {
            val targetDir = File(context.filesDir, ASSET_DIR)
            val marker = File(targetDir, MARKER_RELATIVE)

            if (!marker.exists()) {
                // Проверяем, что в assets вообще есть папка модели.
                val topEntries = try {
                    context.assets.list(ASSET_DIR)?.toList().orEmpty()
                } catch (e: Exception) {
                    Log.e(TAG, "assets.list failed", e)
                    emptyList()
                }
                Log.i(TAG, "assets/$ASSET_DIR entries: $topEntries")

                if (topEntries.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "В assets нет папки $ASSET_DIR. " +
                                    "Проверь, что модель лежит по пути " +
                                    "app/src/main/assets/$ASSET_DIR/"
                        )
                    )
                }

                onStatus("Копирую модель (первый запуск, ~30 секунд)…")

                // На всякий случай сносим остатки прошлой неудачной попытки.
                if (targetDir.exists()) targetDir.deleteRecursively()
                targetDir.mkdirs()

                copyAssetDir(context, ASSET_DIR, targetDir)

                if (!marker.exists()) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "После копирования не найден $MARKER_RELATIVE. " +
                                    "Проверь содержимое assets/$ASSET_DIR/"
                        )
                    )
                }
                Log.i(TAG, "model copied to ${targetDir.absolutePath}")
            }

            onStatus("Загружаю модель…")
            val model = Model(targetDir.absolutePath)
            Log.i(TAG, "model loaded")
            Result.success(model)
        } catch (e: Exception) {
            Log.e(TAG, "prepare failed", e)
            Result.failure(e)
        }
    }

    /**
     * Рекурсивно копирует папку из assets в файловую систему.
     *
     * Если `assets.list(path)` возвращает непустой список — это папка,
     * обходим её рекурсивно. Если пустой — пробуем открыть как файл.
     */
    private fun copyAssetDir(context: Context, assetPath: String, target: File) {
        val assets = context.assets
        val children = assets.list(assetPath).orEmpty()

        if (children.isEmpty()) {
            // Это файл.
            target.parentFile?.mkdirs()
            try {
                assets.open(assetPath).use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "skip $assetPath: ${e.message}")
            }
        } else {
            // Это папка.
            target.mkdirs()
            for (child in children) {
                copyAssetDir(context, "$assetPath/$child", File(target, child))
            }
        }
    }
}