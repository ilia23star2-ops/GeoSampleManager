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
import com.example.geosamplemanager.data.backup.BackupSource
import com.example.geosamplemanager.data.backup.GsmBackupReader
import com.example.geosamplemanager.data.backup.GsmBackupWriter
import com.example.geosamplemanager.data.backup.PublicBackup
import com.example.geosamplemanager.data.backup.PublicBackupsLister
import com.example.geosamplemanager.data.backup.PublicBackupsMigrator
import com.example.geosamplemanager.data.backup.RollbackBackup
import com.example.geosamplemanager.data.backup.RollbackBackups
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import com.example.geosamplemanager.data.merge.ArchiveData
import com.example.geosamplemanager.data.merge.ConflictResolution
import com.example.geosamplemanager.data.merge.MergeEngine
import com.example.geosamplemanager.data.merge.MergePreview
import com.example.geosamplemanager.data.merge.MergeRunner
import com.example.geosamplemanager.data.merge.MergeStats
import com.example.geosamplemanager.data.merge.MergeWizardState
import com.example.geosamplemanager.data.merge.TempDatabaseHandle
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
    // Миграция старых публичных бэкапов
    // ================================================================

    private var publicMigrationStarted = false

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
    // Публичные бэкапы (импорт)
    // ================================================================

    private val _publicBackups = MutableStateFlow<List<PublicBackup>>(emptyList())
    val publicBackups: StateFlow<List<PublicBackup>> = _publicBackups.asStateFlow()

    private val _publicBackupsLoading = MutableStateFlow(false)
    val publicBackupsLoading: StateFlow<Boolean> =
        _publicBackupsLoading.asStateFlow()

    fun loadPublicBackups() {
        if (_publicBackupsLoading.value) return
        _publicBackupsLoading.value = true
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    PublicBackupsLister.listForImport(getApplication())
                }
                _publicBackups.value = list
            } catch (e: Exception) {
                _message.value = "Ошибка чтения списка: ${e.message}"
            } finally {
                _publicBackupsLoading.value = false
            }
        }
    }

    // ================================================================
    // Управление бэкапами
    // ================================================================

    private val _managerBackups = MutableStateFlow<List<RollbackBackup>>(emptyList())
    val managerBackups: StateFlow<List<RollbackBackup>> =
        _managerBackups.asStateFlow()

    private val _managerLoading = MutableStateFlow(false)
    val managerLoading: StateFlow<Boolean> = _managerLoading.asStateFlow()

    fun loadAllBackupsForManager() {
        if (_managerLoading.value) return
        _managerLoading.value = true
        viewModelScope.launch {
            try {
                val dir = repo.getRollbackBackupsDir()
                val privateList = withContext(Dispatchers.IO) {
                    RollbackBackups.list(dir) { GsmBackupReader.readManifest(it) }
                }
                val publicList = withContext(Dispatchers.IO) {
                    PublicBackupsLister.listAllPublic(getApplication())
                        .map { RollbackBackups.fromPublic(it) }
                }
                _managerBackups.value = RollbackBackups.merge(
                    privateList = privateList,
                    publicList = publicList
                )
            } catch (e: Exception) {
                _message.value = "Ошибка чтения бэкапов: ${e.message}"
            } finally {
                _managerLoading.value = false
            }
        }
    }

    fun deleteBackup(backup: RollbackBackup) {
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    deleteAllCopies(backup)
                }
                _message.value = if (ok) {
                    "Удалён: ${backup.fileName}"
                } else {
                    "Не удалось удалить: ${backup.fileName}"
                }
                reloadManagerBackups()
            } catch (e: Exception) {
                _message.value = "Ошибка удаления: ${e.message}"
            }
        }
    }

    private fun deleteAllCopies(backup: RollbackBackup): Boolean {
        var anyDeleted = false
        try {
            val f = File(repo.getRollbackBackupsDir(), backup.fileName)
            if (f.exists() && f.delete()) anyDeleted = true
        } catch (_: Exception) {
        }
        try {
            val allPublic = PublicBackupsLister.listAllPublic(getApplication())
            for (pb in allPublic) {
                if (pb.displayName == backup.fileName) {
                    if (PublicBackupsLister.deleteByUri(
                            getApplication(), pb.uri
                        )
                    ) {
                        anyDeleted = true
                    }
                }
            }
        } catch (_: Exception) {
        }
        return anyDeleted
    }

    fun deleteOldBackups() {
        viewModelScope.launch {
            try {
                val deleted = withContext(Dispatchers.IO) {
                    val privateDeleted = RollbackBackups.rotateByPrefix(
                        repo.getRollbackBackupsDir(),
                        RollbackBackups.MAX_KEEP
                    ).size
                    val publicDeleted = PublicBackupsLister.rotateAutoBackups(
                        getApplication(),
                        RollbackBackups.MAX_KEEP
                    )
                    privateDeleted + publicDeleted
                }
                _message.value = if (deleted > 0) {
                    "Удалено старых: $deleted"
                } else {
                    "Нечего удалять"
                }
                reloadManagerBackups()
            } catch (e: Exception) {
                _message.value = "Ошибка ротации: ${e.message}"
            }
        }
    }

    fun deleteAllBackups() {
        viewModelScope.launch {
            try {
                val deleted = withContext(Dispatchers.IO) {
                    var count = 0
                    val dir = repo.getRollbackBackupsDir()
                    dir.listFiles()?.forEach { f ->
                        try {
                            if (f.isFile && f.delete()) count++
                        } catch (_: Exception) {
                        }
                    }
                    val allPublic = PublicBackupsLister
                        .listAllPublic(getApplication())
                    for (pb in allPublic) {
                        if (PublicBackupsLister.deleteByUri(
                                getApplication(), pb.uri
                            )
                        ) {
                            count++
                        }
                    }
                    count
                }
                _message.value = "Удалено бэкапов: $deleted"
                reloadManagerBackups()
            } catch (e: Exception) {
                _message.value = "Ошибка удаления: ${e.message}"
            }
        }
    }

    private suspend fun reloadManagerBackups() {
        _managerLoading.value = false
        loadAllBackupsForManager()
    }

    // ================================================================
    // FIX 5.9-db-merge-v2/4: слияние БД
    // ================================================================

    private val _mergeState = MutableStateFlow<MergeWizardState>(MergeWizardState.Idle)
    val mergeState: StateFlow<MergeWizardState> = _mergeState.asStateFlow()

    private var mergeHandle: TempDatabaseHandle? = null
    private var mergeUri: Uri? = null
    private var mergeFileName: String? = null

    fun startMergeWizard() {
        _mergeState.value = MergeWizardState.Loading
    }

    /**
     * Пользователь выбрал .gsmbackup для слияния.
     * Открываем архив, читаем его, строим предварительные планы.
     */
    fun loadMergePreview(uri: Uri, fileName: String) {
        if (_mergeState.value is MergeWizardState.Running) return
        _mergeState.value = MergeWizardState.Loading
        mergeUri = uri
        mergeFileName = fileName

        viewModelScope.launch {
            try {
                val cacheDir = getApplication<Application>().cacheDir
                val handle = withContext(Dispatchers.IO) {
                    MergeEngine.openArchive(
                        context = getApplication(),
                        uri = uri,
                        cacheDir = cacheDir
                    ).getOrThrow()
                }
                mergeHandle = handle

                val archive = withContext(Dispatchers.IO) {
                    MergeRunner.readArchiveData(handle)
                }

                val myAreas = repo.getAreas()
                val myOrders = repo.getAllOrders()

                val areaPlan = MergeEngine.planAreas(myAreas, archive.areas)
                val orderPlan = MergeEngine.planOrders(
                    myOrders = myOrders,
                    theirOrders = archive.orders,
                    areaIdMap = areaPlan.existing
                )

                // Псевдомаппинг для превью: их orderId -> -1 для
                // новых нарядов. Точное значение получим в Runner.
                val pseudoOrderMap = HashMap(orderPlan.existing)
                for (add in orderPlan.toAdd) {
                    pseudoOrderMap[add.theirId] = -1L
                }

                val mySamples = repo.getSamplesForOrders(
                    (orderPlan.existing.values +
                            orderPlan.toAdd.map { it.theirId }).toList()
                ).filter { it.orderId > 0 }

                val samplePlan = MergeEngine.planSamples(
                    mySamples = mySamples,
                    theirSamples = archive.samples,
                    orderIdMap = pseudoOrderMap
                )

                val pseudoSampleMap = HashMap<Long, Long>()
                for (add in samplePlan.toAdd) {
                    pseudoSampleMap[add.theirId] = -1L
                }
                for (c in samplePlan.conflicts) {
                    pseudoSampleMap[c.theirId] = c.myId
                }

                val wellPlan = MergeEngine.planWells(
                    myWells = emptyMap(),
                    theirWells = archive.wellsByOrderId,
                    orderIdMap = pseudoOrderMap
                )

                val notePlan = MergeEngine.planNotes(
                    myNotes = emptyMap(),
                    theirNotes = archive.notesBySampleId,
                    sampleIdMap = pseudoSampleMap
                )

                val theirImages = archive.imagesBySampleId.values.flatten()
                val conflictIds = samplePlan.conflicts.map { it.theirId }.toHashSet()
                val photoPlan = MergeEngine.planPhotos(
                    theirImages = theirImages,
                    sampleIdMap = pseudoSampleMap,
                    conflictSampleIds = conflictIds,
                    resolutions = emptyMap()
                )

                val stats = MergeStats.from(
                    areaPlan = areaPlan,
                    orderPlan = orderPlan,
                    samplePlan = samplePlan,
                    wellPlan = wellPlan,
                    notePlan = notePlan,
                    photoPlan = photoPlan
                )

                val preview = MergePreview(
                    fileName = fileName,
                    archive = archive,
                    areaPlan = areaPlan,
                    orderPlan = orderPlan,
                    samplePlan = samplePlan,
                    wellPlan = wellPlan,
                    notePlan = notePlan,
                    photoPlan = photoPlan,
                    stats = stats
                )
                _mergeState.value = MergeWizardState.Preview(preview)
            } catch (e: Exception) {
                cleanupMergeHandle()
                _mergeState.value = MergeWizardState.Error(
                    e.message ?: "Не удалось прочитать архив"
                )
            }
        }
    }

    /**
     * Продолжить с превью.
     * Если есть конфликты проб — на шаг разбора конфликтов.
     * Иначе — сразу на запуск.
     */
    fun continueFromPreview() {
        val state = _mergeState.value
        if (state !is MergeWizardState.Preview) return
        val preview = state.preview
        if (preview.samplePlan.conflicts.isEmpty()) {
            runMerge(emptyMap())
        } else {
            _mergeState.value = MergeWizardState.ConflictStep(preview, 0)
        }
    }

    /**
     * Установить разрешение для текущего конфликта и перейти к
     * следующему. Если конфликты закончились — запускаем.
     */
    fun setConflictResolution(
        sampleId: Long,
        resolution: ConflictResolution
    ) {
        val state = _mergeState.value
        if (state !is MergeWizardState.ConflictStep) return
        val preview = state.preview

        val updated = preview.copy(
            // пересчёт photoPlan не делаем — он строится в Runner
        )
        _conflictResolutions[sampleId] = resolution

        val next = state.currentIndex + 1
        if (next >= preview.samplePlan.conflicts.size) {
            val finalResolutions = HashMap<Long, ConflictResolution>(_conflictResolutions)
            _conflictResolutions.clear()
            _mergeState.value = MergeWizardState.ConflictStep(updated, next)
            runMerge(finalResolutions)
        } else {
            _mergeState.value = MergeWizardState.ConflictStep(updated, next)
        }
    }

    /**
     * Применить одно разрешение ко всем оставшимся конфликтам
     * и сразу запустить слияние.
     */
    fun setAllConflictsResolution(resolution: ConflictResolution) {
        val state = _mergeState.value
        if (state !is MergeWizardState.ConflictStep) return
        val preview = state.preview

        val resolutions = HashMap<Long, ConflictResolution>()
        for (i in state.currentIndex until preview.samplePlan.conflicts.size) {
            val c = preview.samplePlan.conflicts[i]
            resolutions[c.theirId] = resolution
        }
        // Плюс уже выбранные ранее.
        resolutions.putAll(_conflictResolutions)

        _conflictResolutions.clear()
        runMerge(resolutions)
    }

    private val _conflictResolutions = HashMap<Long, ConflictResolution>()

    private fun runMerge(resolutions: Map<Long, ConflictResolution>) {
        val state = _mergeState.value
        val preview: MergePreview = when (state) {
            is MergeWizardState.Preview -> state.preview
            is MergeWizardState.ConflictStep -> state.preview
            else -> return
        }
        val handle = mergeHandle ?: run {
            _mergeState.value = MergeWizardState.Error("Потерян архив")
            return
        }

        viewModelScope.launch {
            try {
                _mergeState.value = MergeWizardState.Running("Сливаем…")
                val stats = withContext(Dispatchers.IO) {
                    MergeRunner.run(
                        context = getApplication(),
                        repo = repo,
                        preview = preview,
                        archivePhotosDir = handle.photosDir,
                        resolutions = resolutions,
                        onProgress = { msg ->
                            _mergeState.value = MergeWizardState.Running(msg)
                        }
                    )
                }
                cleanupMergeHandle()
                _mergeState.value = MergeWizardState.Done(stats)
                _message.value = "Слияние завершено"
            } catch (e: Exception) {
                cleanupMergeHandle()
                _mergeState.value = MergeWizardState.Error(
                    e.message ?: "Ошибка слияния"
                )
            }
        }
    }

    fun cancelMerge() {
        cleanupMergeHandle()
        _conflictResolutions.clear()
        _mergeState.value = MergeWizardState.Idle
    }

    fun resetMergeState() {
        cleanupMergeHandle()
        _conflictResolutions.clear()
        _mergeState.value = MergeWizardState.Idle
    }

    private fun cleanupMergeHandle() {
        val h = mergeHandle ?: return
        mergeHandle = null
        try { MergeEngine.closeAndClean(h) } catch (_: Exception) {}
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
    // Откат (приватные + публичные)
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

    fun loadRollbackBackups() {
        if (_rollbackLoading.value) return
        _rollbackLoading.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    PublicBackupsLister.rotateAutoBackups(getApplication())
                }

                val dir = repo.getRollbackBackupsDir()
                val privateList = withContext(Dispatchers.IO) {
                    RollbackBackups.list(dir) { GsmBackupReader.readManifest(it) }
                }
                val publicList = withContext(Dispatchers.IO) {
                    PublicBackupsLister.listAutoBackups(getApplication())
                        .map { RollbackBackups.fromPublic(it) }
                }

                _rollbackBackups.value = RollbackBackups.merge(
                    privateList = privateList,
                    publicList = publicList
                )
            } catch (e: Exception) {
                _message.value = "Ошибка чтения бэкапов: ${e.message}"
            } finally {
                _rollbackLoading.value = false
            }
        }
    }

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

    fun rollbackFromPublic(uri: Uri) {
        performReplacement(
            stateFlow = _rollbackState,
            autoBackupOperation = AUTO_BACKUP_OP_ROLLBACK,
            errorMessage = "Ошибка отката",
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
    // Очистка БД
    // ================================================================

    private val _cleanState = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val cleanState: StateFlow<RestoreState> = _cleanState.asStateFlow()

    fun resetCleanState() {
        _cleanState.value = RestoreState.Idle
    }

    fun cleanDatabase() {
        if (_cleanState.value is RestoreState.InProgress) return

        viewModelScope.launch {
            try {
                _cleanState.value = RestoreState.InProgress("Готовим бэкап…")
                withContext(Dispatchers.IO) { autoBackup(AUTO_BACKUP_OP_CLEAN) }

                _cleanState.value = RestoreState.InProgress("Очищаем БД…")
                withContext(Dispatchers.IO) { repo.clearAllData() }

                _cleanState.value = RestoreState.InProgress("Закрываем соединение…")
                withContext(Dispatchers.IO) {
                    AppDatabase.closeAndReset()
                }
                app.resetRepository()

                withContext(Dispatchers.IO) { rotateAllBackups() }
                _cleanState.value = RestoreState.Done
            } catch (e: Exception) {
                _cleanState.value = RestoreState.Error(
                    e.message ?: "Ошибка очистки"
                )
            }
        }
    }

    // ================================================================
    // Общая цепочка замены БД
    // ================================================================

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
    // Утилиты
    // ================================================================

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

    private suspend fun rotateAllBackups() {
        try {
            RollbackBackups.rotateByPrefix(
                repo.getRollbackBackupsDir(),
                RollbackBackups.MAX_KEEP
            )
        } catch (_: Exception) {
        }
        try {
            PublicBackupsLister.rotateAutoBackups(
                getApplication(),
                RollbackBackups.MAX_KEEP
            )
        } catch (_: Exception) {
        }
    }

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
        const val AUTO_BACKUP_OP_RESTORE = "restore"
        const val AUTO_BACKUP_OP_ROLLBACK = "rollback"
        const val AUTO_BACKUP_OP_CLEAN = "clean"
        const val AUTO_BACKUP_OP_EXPORT = "export"
    }
}