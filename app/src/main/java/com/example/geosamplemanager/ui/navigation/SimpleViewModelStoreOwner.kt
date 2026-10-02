package com.example.geosamplemanager.ui.navigation

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * FIX 5.9-db-soft-restart:
 * Простой владелец ViewModelStore для Compose-поддерева.
 *
 * Используется в MainActivity: при изменении restart-tick создаётся
 * новый SimpleViewModelStoreOwner, старый очищается. Все ViewModel'и
 * внутри поддерева пересоздаются с нуля — без пересоздания Activity.
 */
class SimpleViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}