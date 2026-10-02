package com.example.geosamplemanager.ui.screens

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geosamplemanager.GeoSampleApp
import com.example.geosamplemanager.data.AppDatabase
import com.example.geosamplemanager.data.DbInfo
import com.example.geosamplemanager.data.DatabaseRepository
import com.example.geosamplemanager.data.backup.BackupCounts
import com.example.geosamplemanager.data.backup.BackupManifest
import com.example.geosamplemanager.data.backup.GsmBackupReader
import com.example.geosamplemanager.data.backup.GsmBackupWriter
import com.example.geosamplemanager.data.backup.PublicBackupsMigrator
import com.example.geosamplemanager.data.backup.RollbackBackup
import com.example.geosamplemanager.data.backup.RollbackBackups
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class RestoreState {
    data object Idle : RestoreState()
    data class InProgress(val message: String) : RestoreState()
    data object Done : RestoreState()
    data class Error(val message: String) : RestoreState()
}

@OptIn(ExperimentalCoroutinesApi::class)
class DbViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GeoSampleApp
    private val repo = app.repository

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
    // FIX 5.9-db-backups-ops/2: миграция старых публичных бэкапов
    // ================================================================

    private var publicMigrationStarted = false

    /**
     * Одноразовая ленивая миграция: переложить старые pre_* из корня
     * Downloads/GeoSampleManager/ в подпапки pre_restore/ и т.д.
     *
     * Вызывается при первом показе вкладки БД. Флаг — в
     * SharedPreferences, чтобы не гонять после перезапуска процесса.
     */
    fun migrateOldPublicBackupsIfNeeded() {
        if (publicMigrationStarted) return
        publicMigrationStarted = true
        if (!shouldMigratePublicBackups()) return

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    PublicBackupsMigrator.migrate(getApplication())
                }
                markPublicBackupsMigrated()
            } catch (_: Exception) {
                // Не критично: миграция — фон.
            }
        }
    }

    private fun shouldMigratePublicBackups(): Boolean {
        val prefs = getApplication<Application>().getSharedPreferences(
            PublicBackupsMigrator.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        return !prefs.getBoolean(PublicBackupsMigrator.KEY_MIGRATED, false)
    }

    private fun markPublicBackupsMigrated() {
        val prefs = getApplication<Application>().getSharedPreferences(
            PublicBackupsMigrator.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        prefs.edit()
            .putBoolean(PublicBackupsMigrator.KEY_MIGRATED, true)
            .apply()
    }

    // ================================================================
    // Экспорт
    // ================================================================

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    private val _lastExportUri = MutableStateFlow<Uri?>(null)
    val lastExportUri: StateFlow<Uri?> = _lastExportUri.asStateFlow()

    fun consumeLastExportUri() {
        _lastExportUri.value = null
    }

    fun exportToDownloads(name: String) {
        if (_exporting.value) return
        viewModelScope.launch {
            _exporting.value = true
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    throw IllegalStateException(
                        "Для сохранения нужен Android 10+. " +
                                "Используйте системный диалог."
                    )
                }
                val uri = withContext(Dispatchers.IO) {
                    writeToPublicDownloads(
                        "$name.${GsmBackupWriter.EXTENSION}",
                        PublicBackupsMigrator.EXPORTS_DIR
                    )
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

    fun exportToUri(uri: Uri) {
        if (_exporting.value) return
        viewModelScope.launch {
            _exporting.value = true
            try {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri)?.use { out ->
                        writeBackup(out, AUTO_BACKUP_OP_EXPORT)
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

    // ================================================================
    // Импорт
    // ================================================================

    private val _restoreState = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val restoreState: StateFlow<RestoreState> = _restoreState.asStateFlow()

    fun resetRestoreState() {
        _restoreState.value = RestoreState.Idle
    }

    suspend fun readManifest(uri: Uri): BackupManifest? = withContext(Dispatchers.IO) {
        GsmBackupReader.readManifest(getApplication(), uri)
    }

    fun restoreFromUri(uri: Uri) {
        performReplacement(
            stateFlow = _restoreState,
            autoBackupOperation = AUTO_BACKUP_OP_RESTORE,
            errorMessage = "Ошибка импорта",
            extract = { targetDb, targetPhotosDir ->
                GsmBackupReader.extract(
                    context = getApplication(),
                    uri = uri,
                    targetDb = targetDb,
                    targetPhotosDir = targetPhotosDir
                )
            }
        )
    }

    // ================================================================
    // FIX 5.9-db-rollback: откат к авто-бэкапу
    // ================================================================

    private val _rollbackBackups = MutableStateFlow<List<RollbackBackup>>(emptyList())
    val rollbackBackups: StateFlow<List<RollbackBackup>> =
        _rollbackBackups.asStateFlow()

    private val _rollbackLoading = MutableStateFlow(false)
    val rollbackLoading: StateFlow<Boolean> = _rollbackLoading.asStateFlow()

    private val _rollbackState = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val rollbackState: StateFlow<RestoreState> = _rollbackState.asStateFlow()

    fun resetRollbackState() {
        _rollbackState.value = RestoreState.Idle
    }

    /**
     * Загружает список pre_*_* из filesDir/db_backups/.
     * Manifest читается для каждого файла — нужен для диалога.
     */
    fun loadRollbackBackups() {
        if (_rollbackLoading.value) return
        _rollbackLoading.value = true
        viewModelScope.launch {
            try {
                val dir = repo.getRollbackBackupsDir()
                val list = withContext(Dispatchers.IO) {
                    RollbackBackups.list(dir) { GsmBackupReader.readManifest(it) }
                }
                _rollbackBackups.value = list
            } catch (e: Exception) {
                _message.value = "Ошибка чтения бэкапов: ${e.message}"
            } finally {
                _rollbackLoading.value = false
            }
        }
    }

    /**
     * Откат к авто-бэкапу из приватной папки.
     */
    fun rollbackFromInternal(file: File) {
        performReplacement(
            stateFlow = _rollbackState,
            autoBackupOperation = AUTO_BACKUP_OP_ROLLBACK,
            errorMessage = "Ошибка отката",
            extract = { targetDb, targetPhotosDir ->
                GsmBackupReader.extract(
                    file = file,
                    targetDb = targetDb,
                    targetPhotosDir = targetPhotosDir
                )
            }
        )
    }

    // ================================================================
    // Общая цепочка замены БД (импорт и откат)
    // ================================================================

    /**
     * FIX 5.9-db-rollback:
     * Общая логика для import/rollback:
     *  1) авто-бэкап текущего состояния с операцией;
     *  2) закрытие БД, удаление -wal/-shm, очистка папки фото;
     *  3) extract;
     *  4) resetRepository;
     *  5) ротация pre_*_ по каждому префиксу;
     *  6) Done.
     *
     * Импорт и откат отличаются только источником extract и operation.
     *
     * FIX 5.9-db-backups-ops:
     *  - параметр operation ("restore"/"rollback"/"clean");
     *  - ротация теперь по каждому префиксу.
     */
    private fun performReplacement(
        stateFlow: MutableStateFlow<RestoreState>,
        autoBackupOperation: String,
        errorMessage: String,
        extract: (targetDb: File, targetPhotosDir: File) -> Boolean
    ) {
        if (stateFlow.value is RestoreState.InProgress) return

        viewModelScope.launch {
            try {
                stateFlow.value = RestoreState.InProgress("Готовим бэкап…")
                withContext(Dispatchers.IO) { autoBackup(autoBackupOperation) }

                stateFlow.value = RestoreState.InProgress("Закрываем БД…")
                val ok = withContext(Dispatchers.IO) {
                    AppDatabase.closeAndReset()

                    val dbFile = getApplication<Application>()
                        .getDatabasePath("geosamples.db")
                    deleteQuietly(File(dbFile.absolutePath + "-wal"))
                    deleteQuietly(File(dbFile.absolutePath + "-shm"))
                    deleteQuietly(dbFile)

                    val photosDir = File(
                        getApplication<Application>().filesDir,
                        "sample_photos"
                    )
                    if (photosDir.exists()) photosDir.deleteRecursively()
                    photosDir.mkdirs()

                    extract(dbFile, photosDir)
                }

                if (!ok) {
                    throw IllegalStateException("В архиве нет geosamples.db")
                }

                app.resetRepository()
                withContext(Dispatchers.IO) { rotateAllBackups() }
                stateFlow.value = RestoreState.Done
            } catch (e: Exception) {
                stateFlow.value = RestoreState.Error(
                    e.message ?: errorMessage
                )
            }
        }
    }

    // ================================================================
    // Внутренние утилиты
    // ================================================================

    /**
     * FIX 5.9-db-backups-ops:
     * Общий авто-бэкап. operation — restore/rollback/clean/export.
     * Имя файла — pre_<operation>_YYYYMMDD_HHmm.gsmbackup.
     * Пишем в приватную папку и (на Android 10+) в публичные Загрузки.
     */
    private suspend fun autoBackup(operation: String) {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
        val prefix = RollbackBackups.prefixFor(operation)
        val name = "$prefix${sdf.format(Date())}"
        val fileName = "$name.${GsmBackupWriter.EXTENSION}"

        try {
            val dir = repo.getRollbackBackupsDir()
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, fileName)
            f.outputStream().use { out -> writeBackup(out, operation) }
        } catch (_: Exception) {
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                writeToPublicDownloads(fileName, "pre_$operation")
            } catch (_: Exception) {
            }
        }
    }

    /**
     * FIX 5.9-db-backups-ops:
     * Ротация отдельно по каждому префиксу (5 на операцию).
     * Вызывается после успешной замены БД.
     */
    private suspend fun rotateAllBackups() {
        try {
            RollbackBackups.rotateByPrefix(
                repo.getRollbackBackupsDir(),
                RollbackBackups.MAX_KEEP
            )
        } catch (_: Exception) {
        }
    }

    /**
     * FIX 5.9-db-backups-ops/2:
     * subDir — подпапка внутри GeoSampleManager/ в Загрузках.
     */
    private suspend fun writeToPublicDownloads(
        fileName: String,
        subDir: String
    ): Uri {
        val ctx = getApplication<Application>()
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/" +
                        "${PublicBackupsMigrator.ROOT_DIR}/$subDir"
            )
        }
        val uri = resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        ) ?: throw IllegalStateException("Не удалось создать файл")

        try {
            resolver.openOutputStream(uri)?.use { out ->
                writeBackup(out, AUTO_BACKUP_OP_EXPORT)
            } ?: throw IllegalStateException("Не удалось открыть поток")
        } catch (e: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            throw e
        }
        return uri
    }

    /**
     * FIX 5.9-db-backups-ops:
     * operation — попадает в манифест.
     */
    private suspend fun writeBackup(out: java.io.OutputStream, operation: String) {
        repo.checkpointWal()
        val info = repo.getDbInfo()
        GsmBackupWriter.write(
            out = out,
            dbFile = repo.getDatabaseFile(),
            photosDir = repo.getPhotosDir(),
            dbSchemaVersion = DatabaseRepository.DB_SCHEMA_VERSION,
            appVersion = appVersion(),
            counts = BackupCounts(
                areas = info.areasCount,
                orders = info.ordersCount,
                samples = info.samplesCount,
                photos = info.photosCount,
                notes = info.notesCount
            ),
            operation = operation
        )
    }

    private fun appVersion(): String = try {
        val ctx = getApplication<Application>()
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }

    private fun deleteQuietly(f: File) {
        try { if (f.exists()) f.delete() } catch (_: Exception) {}
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

    companion object {
        /** FIX 5.9-db-backups-ops: операции авто-бэкапа. */
        const val AUTO_BACKUP_OP_RESTORE = "restore"
        const val AUTO_BACKUP_OP_ROLLBACK = "rollback"
        const val AUTO_BACKUP_OP_CLEAN = "clean"
        const val AUTO_BACKUP_OP_EXPORT = "export"
    }
}