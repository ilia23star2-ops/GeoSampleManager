package com.example.geosamplemanager.ui.screens

/**
 * FIX 5.9-db-clean:
 * Состояние подтверждения очистки БД.
 *
 * Кнопка «Очистить» в диалоге активна только если пользователь
 * поставил галочку «Понимаю, что все данные будут удалены».
 *
 * Вынесено отдельно от Compose — покрывается юнит-тестами.
 */
data class CleanConfirmState(
    val confirmed: Boolean = false
) {
    /** Кнопка «Очистить» в диалоге активна. */
    val confirmEnabled: Boolean get() = confirmed

    /** Переключить галочку. */
    fun toggle(): CleanConfirmState = copy(confirmed = !confirmed)

    companion object {
        val Initial = CleanConfirmState()
    }
}