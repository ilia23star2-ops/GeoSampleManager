package com.example.geosamplemanager.ui.screens

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geosamplemanager.GeoSampleApp
import com.example.geosamplemanager.data.DbInfo
import com.example.geosamplemanager.data.DatabaseRepository
import com.example.geosamplemanager.data.backup.BackupCounts
import com.example.geosamplemanager.data.backup.GsmBackupWriter
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FIX 5.9-db-info: dbInfo + loadDbInfo().
 *
 * FIX 5.9-db-backup-v2: экспорт .gsmbackup.
 *
 * FIX 5.9-db-backup-fix:
 *  - экспорт в ПУБЛИЧНУЮ папку Загрузки через MediaStore (API 29+),
 *    файл виден в любом проводнике;
 *  - на API < 29 — fallback через exportToUri (системный диалог);
 *  - убран exportToInternal (внутренняя папка бесполезна);
 *  - после сохранения — lastExportUri, UI может открыть Share.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DbViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as GeoSampleApp).repository

    val areas: StateFlow<List<AreaEntity>> = repo.getAreasFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedArea = MutableStateFlow<AreaEntity?>(null)
    val selectedArea: StateFlow<AreaEntity?> = _selectedArea.asStateFlow()

    val orders: StateFlow<List<OrderEntity>> = _selectedArea
        .flatMapLatest { area ->
            if (area == null) flowOf(emptyList())
            else repo.getOrdersForArea(area.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedOrder = MutableStateFlow<OrderEntity?>(null)
    val selectedOrder: StateFlow<OrderEntity?> = _selectedOrder.asStateFlow()

    val samples: StateFlow<List<SampleEntity>> = _selectedOrder
        .flatMapLatest { order ->
            if (order == null) flowOf(emptyList())
            else repo.getSamplesForOrder(order.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    // ================================================================
    // Инфо о БД
    // ================================================================

    private val _dbInfo = MutableStateFlow<DbInfo?>(null)
    val dbInfo: StateFlow<DbInfo?> = _dbInfo.asStateFlow()

    private val _dbInfoLoading = MutableStateFlow(false)
    val dbInfoLoading: StateFlow<Boolean> = _dbInfoLoading.asStateFlow()

    fun loadDbInfo() {
        if (_dbInfoLoading.value) return
        _dbInfoLoading.value = true
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { repo.getDbInfo() }
                _dbInfo.value = info
            } catch (e: Exception) {
                _message.value = "Ошибка чтения БД: ${e.message}"
            } finally {
                _dbInfoLoading.value = false
            }
        }
    }

    // ================================================================
    // Экспорт .gsmbackup
    // ================================================================

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    /**
     * FIX 5.9-db-backup-fix:
     * URI последнего успешного экспорта. UI читает и сбрасывает через
     * consumeLastExportUri(). Нужно, чтобы предложить Share.
     */
    private val _lastExportUri = MutableStateFlow<Uri?>(null)
    val lastExportUri: StateFlow<Uri?> = _lastExportUri.asStateFlow()

    fun consumeLastExportUri() {
        _lastExportUri.value = null
    }

    /**
     * FIX 5.9-db-backup-fix:
     * Сохранить .gsmbackup в ПУБЛИЧНУЮ папку Загрузки
     * (Download / Downloads / Загрузки — имя задаёт система).
     * Подпапка — GeoSampleManager, чтобы бэкапы не смешивались.
     *
     * Работает без разрешений на API 29+.
     * На API < 29 — бросает исключение; UI должен использовать
     * exportToUri через системный диалог.
     */
    fun exportToDownloads(name: String) {
        if (_exporting.value) return
        viewModelScope.launch {
            _exporting.value = true
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    throw IllegalStateException(
                        "Для сохранения нужен Android 10+. " +
                                "Выберите «Сохранить наружу»."
                    )
                }

                val uri = withContext(Dispatchers.IO) {
                    val ctx = getApplication<Application>()
                    val resolver = ctx.contentResolver
                    val fileName = "$name.${GsmBackupWriter.EXTENSION}"

                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(
                            MediaStore.MediaColumns.MIME_TYPE,
                            "application/octet-stream"
                        )
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            "${Environment.DIRECTORY_DOWNLOADS}/GeoSampleManager"
                        )
                    }

                    val uri = resolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        values
                    ) ?: throw IllegalStateException("Не удалось создать файл")

                    try {
                        resolver.openOutputStream(uri)?.use { out ->
                            writeBackup(out)
                        } ?: throw IllegalStateException("Не удалось открыть поток")
                    } catch (e: Exception) {
                        // Если запись упала — убираем пустой файл.
                        try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                        throw e
                    }
                    uri
                }

                _lastExportUri.value = uri
                _message.value = "Сохранено в Загрузки/GeoSampleManager: $name"
            } catch (e: Exception) {
                _message.value = "Ошибка бэкапа: ${e.message}"
            } finally {
                _exporting.value = false
            }
        }
    }

    /**
     * FIX 5.9-db-backup-fix:
     * Сохранить по внешнему URI (системный диалог "Сохранить как").
     * Используется на API < 29 как fallback.
     */
    fun exportToUri(uri: Uri) {
        if (_exporting.value) return
        viewModelScope.launch {
            _exporting.value = true
            try {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri)?.use { out ->
                        writeBackup(out)
                    } ?: throw IllegalStateException("Не удалось открыть файл")
                }
                _message.value = "Бэкап сохранён"
            } catch (e: Exception) {
                _message.value = "Ошибка бэкапа: ${e.message}"
            } finally {
                _exporting.value = false
            }
        }
    }

    private suspend fun writeBackup(out: java.io.OutputStream) {
        withContext(Dispatchers.IO) {
            repo.checkpointWal()

            val dbFile = repo.getDatabaseFile()
            val photosDir = repo.getPhotosDir()
            val info = repo.getDbInfo()

            GsmBackupWriter.write(
                out = out,
                dbFile = dbFile,
                photosDir = photosDir,
                dbSchemaVersion = DatabaseRepository.DB_SCHEMA_VERSION,
                appVersion = appVersion(),
                counts = BackupCounts(
                    areas = info.areasCount,
                    orders = info.ordersCount,
                    samples = info.samplesCount,
                    photos = info.photosCount,
                    notes = info.notesCount
                )
            )
        }
    }

    private fun appVersion(): String = try {
        val ctx = getApplication<Application>()
        ctx.packageManager
            .getPackageInfo(ctx.packageName, 0)
            .versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }

    // ================================================================
    // Выбор / действия
    // ================================================================

    fun selectArea(area: AreaEntity?) {
        _selectedArea.value = area
        _selectedOrder.value = null
    }

    fun selectOrder(order: OrderEntity?) {
        _selectedOrder.value = order
    }

    fun addArea(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _message.value = "Введите название участка"
            return
        }
        viewModelScope.launch {
            try {
                val id = repo.addArea(trimmed)
                if (id == -1L) {
                    _message.value = "Участок «$trimmed» уже существует"
                } else {
                    _message.value = "Участок «$trimmed» добавлен"
                }
            } catch (e: Exception) {
                _message.value = "Ошибка: ${e.message}"
            }
        }
    }

    fun deleteArea(area: AreaEntity) {
        viewModelScope.launch {
            try {
                repo.deleteAreaById(area.id)
                if (_selectedArea.value?.id == area.id) {
                    _selectedArea.value = null
                    _selectedOrder.value = null
                }
                _message.value = "Участок «${area.areaName}» удалён"
            } catch (e: Exception) {
                _message.value = "Ошибка: ${e.message}"
            }
        }
    }

    fun addOrder(orderNumber: String) {
        val area = _selectedArea.value ?: run {
            _message.value = "Сначала выберите участок"
            return
        }
        val trimmed = orderNumber.trim()
        if (trimmed.isEmpty()) {
            _message.value = "Введите номер наряда"
            return
        }
        viewModelScope.launch {
            try {
                val id = repo.addOrder(area.id, trimmed)
                if (id == -1L) {
                    _message.value = "Наряд «$trimmed» уже существует в этом участке"
                } else {
                    _message.value = "Наряд «$trimmed» добавлен"
                }
            } catch (e: Exception) {
                _message.value = "Ошибка: ${e.message}"
            }
        }
    }

    fun deleteOrder(order: OrderEntity) {
        viewModelScope.launch {
            try {
                repo.deleteOrder(order.id)
                if (_selectedOrder.value?.id == order.id) {
                    _selectedOrder.value = null
                }
                _message.value = "Наряд «${order.orderNumber}» удалён"
            } catch (e: Exception) {
                _message.value = "Ошибка: ${e.message}"
            }
        }
    }
}