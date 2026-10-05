package com.example.geosamplemanager.data.session

import android.content.Context
import com.google.gson.Gson
import java.io.File

/**
 * FIX 5.9-main-a:
 * Хранилище последнего активного наряда. Обновляется, когда
 * ОП работает со Сверкой. Главная читает, чтобы показать
 * «Продолжить работу».
 *
 * Файл: filesDir/session_state.json. Сериализация — Gson.
 *
 * Поля:
 *  - lastAreaTitle / lastOrderTitle — строки для UI;
 *  - lastOrderId — для перехода обратно;
 *  - lastActionAt — timestamp последнего действия.
 *
 * В 5.10 здесь будут накапливаться данные теневой статистики,
 * но UI их не покажет.
 */
data class SessionState(
    val lastAreaTitle: String? = null,
    val lastOrderTitle: String? = null,
    val lastOrderId: Long? = null,
    val lastActionAt: Long = 0L
)

class SessionStateRepository(context: Context) {

    private val appContext = context.applicationContext
    private val gson = Gson()

    private val file: File
        get() = File(appContext.filesDir, FILE_NAME)

    fun load(): SessionState {
        return try {
            val f = file
            if (!f.exists()) return SessionState()
            gson.fromJson(f.readText(), SessionState::class.java) ?: SessionState()
        } catch (e: Exception) {
            SessionState()
        }
    }

    fun save(state: SessionState) {
        try {
            file.writeText(gson.toJson(state))
        } catch (_: Exception) {
            // Настройки сессии не критичны — молча игнорируем.
        }
    }

    /**
     * Отметить, что ОП работает с нарядом. Обновляет timestamp.
     */
    fun touch(
        orderId: Long,
        areaTitle: String,
        orderTitle: String
    ) {
        save(
            SessionState(
                lastAreaTitle = areaTitle,
                lastOrderTitle = orderTitle,
                lastOrderId = orderId,
                lastActionAt = System.currentTimeMillis()
            )
        )
    }

    fun clear() {
        try { file.delete() } catch (_: Exception) {}
    }

    companion object {
        private const val FILE_NAME = "session_state.json"
    }
}