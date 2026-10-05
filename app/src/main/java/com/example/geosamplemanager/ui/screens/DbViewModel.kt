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
import com.example.geosamplemanager.data.compare.CompareEngine
import com.example.geosamplemanager.data.compare.CompareResult
import com.example.geosamplemanager.data.compare.ConflictInfo
import com.example.geosamplemanager.data.diagnostics.DbDiagnosticsState
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import com.example.geosamplemanager.data.merge.FieldOwner
import com.example.geosamplemanager.data.merge.FieldResolution
import com.example.geosamplemanager.data.merge.MassStrategy
import com.example.geosamplemanager.data.merge.MergeEngine
import com.example.geosamplemanager.data.merge.MergePreview
import com.example.geosamplemanager.data.merge.MergeRunner
import com.example.geosamplemanager.data.merge.MergeStats
import com.example.geosamplemanager.data.merge.MergeWizardState
import com.example.geosamplemanager.data.merge.SampleConflict
import com.example.geosamplemanager.data.merge.SampleField
import com.example.geosamplemanager.data.merge.TempDatabaseHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    // Диагностика БД (5.9-db-diagnostics)
    // ================================================================

    private val _diagnosticsState =
        MutableStateFlow<DbDiagnosticsState>(DbDiagnosticsState.Idle)
    val diagnosticsState: StateFlow<DbDiagnosticsState> =
        _diagnosticsState.asStateFlow()

    fun startDiagnostics() {
        _diagnosticsState.value = DbDiagnosticsState.Loading
        viewModelScope.launch {
            try {
                val issues = withContext(Dispatchers.IO) {
                    repo.runDiagnostics()
                }
                _diagnosticsState.value = DbDiagnosticsState.Ready(
                    issues = issues,
                    selectedIds = issues.map { it.id }.toSet()
                )
            } catch (e: Exception) {
                _diagnosticsState.value = DbDiagnosticsState.Error(
                    e.message ?: "Ошибка диагностики"
                )
            }
        }
    }

    fun toggleDiagnosticsSelection(issueId: String) {
        val s = _diagnosticsState.value as? DbDiagnosticsState.Ready ?: return
        val newSelected = if (issueId in s.selectedIds) {
            s.selectedIds - issueId
        } else {
            s.selectedIds + issueId
        }
        _diagnosticsState.value = s.copy(selectedIds = newSelected)
    }

    fun toggleDiagnosticsSelectAll() {
        val s = _diagnosticsState.value as? DbDiagnosticsState.Ready ?: return
        _diagnosticsState.value = if (s.allSelected) {
            s.copy(selectedIds = emptySet())
        } else {
            s.copy(selectedIds = s.issues.map { it.id }.toSet())
        }
    }

    fun resetDiagnosticsState() {
        _diagnosticsState.value = DbDiagnosticsState.Idle
    }

    fun applyDiagnosticsFixes() {
        val s = _diagnosticsState.value as? DbDiagnosticsState.Ready ?: return
        if (s.selectedIds.isEmpty()) return
        val toFix = s.issues.filter { it.id in s.selectedIds }

        viewModelScope.launch {
            try {
                _diagnosticsState.value =
                    DbDiagnosticsState.Applying("Готовим бэкап…")
                withContext(Dispatchers.IO) {
                    autoBackup(AUTO_BACKUP_OP_DIAGNOSTICS)
                }

                _diagnosticsState.value =
                    DbDiagnosticsState.Applying("Исправляем…")
                val fixed = withContext(Dispatchers.IO) {
                    repo.applyDiagnosticsFixes(toFix)
                }

                withContext(Dispatchers.IO) { rotateAllBackups() }
                _diagnosticsState.value = DbDiagnosticsState.Done(fixed)
                _message.value = "Исправлено: $fixed"
            } catch (e: Exception) {
                _diagnosticsState.value = DbDiagnosticsState.Error(
                    e.message ?: "Ошибка исправления"
                )
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
                        if (!PublicBackupsLister.isAutoBackupSubDir(pb.subDir)) {
                            continue
                        }
                        if (PublicBackupsLister.deleteByUri(
                                getApplication(), pb.uri
                            )
                        ) {
                            count++
                        }
                    }
                    count
                }
                _message.value = "Удалено авто-бэкапов: $deleted"
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
    // Сравнение БД
    // ================================================================

    private val _compareState = MutableStateFlow<CompareResult?>(null)
    val compareState: StateFlow<CompareResult?> = _compareState.asStateFlow()

    private val _compareLoading = MutableStateFlow(false)
    val compareLoading: StateFlow<Boolean> = _compareLoading.asStateFlow()

    fun startCompare() {
        _compareState.value = null
        _compareLoading.value = true
    }

    fun resetCompareState() {
        _compareState.value = null
        _compareLoading.value = false
    }

    fun loadCompare(uri: Uri, fileName: String) {
        _compareLoading.value = true
        viewModelScope.launch {
            var handle: TempDatabaseHandle? = null
            try {
                val cacheDir = getApplication<Application>().cacheDir
                handle = withContext(Dispatchers.IO) {
                    MergeEngine.openArchive(getApplication(), uri, cacheDir)
                        .getOrThrow()
                }

                val archive = withContext(Dispatchers.IO) {
                    MergeRunner.readArchiveData(handle)
                }

                val myAreas = repo.getAreas()
                val myOrders = repo.getAllOrders()
                val mySamples = withContext(Dispatchers.IO) {
                    repo.getAllSamplesFlow().first()
                }

                val myWells = HashMap<Long, List<String>>()
                for (o in myOrders) {
                    myWells[o.id] = repo.getWellsForOrder(o.id)
                }

                // Конфликты считаем через MergeEngine (только для полей).
                val areaPlan = MergeEngine.planAreas(myAreas, archive.areas)
                val orderPlan = MergeEngine.planOrders(
                    myOrders = myOrders,
                    theirOrders = archive.orders,
                    areaIdMap = areaPlan.existing
                )
                val pseudoOrderMap = HashMap(orderPlan.existing)
                for (add in orderPlan.toAdd) pseudoOrderMap[add.theirId] = -1L

                val mySamplesFiltered = repo.getSamplesForOrders(
                    (orderPlan.existing.values +
                            orderPlan.toAdd.map { it.theirId }).toList()
                ).filter { it.orderId > 0 }

                val samplePlan = MergeEngine.planSamples(
                    mySamples = mySamplesFiltered,
                    theirSamples = archive.samples,
                    orderIdMap = pseudoOrderMap,
                    myOrders = myOrders,
                    myAreas = myAreas
                )

                val conflicts = samplePlan.conflicts.map { c ->
                    ConflictInfo(
                        areaName = c.areaName,
                        orderNumber = c.orderNumber,
                        sampleNumber = c.sampleNumber,
                        fieldLabels = c.fieldDiffs.joinToString(", ") {
                            it.field.label
                        }
                    )
                }

                val result = withContext(Dispatchers.IO) {
                    CompareEngine.buildResult(
                        fileName = fileName,
                        myAreas = myAreas,
                        theirAreas = archive.areas,
                        myOrders = myOrders,
                        theirOrders = archive.orders,
                        mySamples = mySamples,
                        theirSamples = archive.samples,
                        myWellsByOrder = myWells,
                        theirWellsByOrder = archive.wellsByOrderId,
                        conflicts = conflicts
                    )
                }

                _compareState.value = result
            } catch (e: Exception) {
                _message.value = "Ошибка сравнения: ${e.message}"
                _compareState.value = null
            } finally {
                try {
                    handle?.let { MergeEngine.closeAndClean(it) }
                } catch (_: Exception) {}
                _compareLoading.value = false
            }
        }
    }

    // ================================================================
    // Слияние
    // ================================================================

    private val _mergeState = MutableStateFlow<MergeWizardState>(MergeWizardState.Idle)
    val mergeState: StateFlow<MergeWizardState> = _mergeState.asStateFlow()

    private var mergeHandle: TempDatabaseHandle? = null

    fun startMergeWizard() {
        _mergeState.value = MergeWizardState.Loading
    }

    fun loadMergePreview(uri: Uri, fileName: String) {
        if (_mergeState.value is MergeWizardState.Running) return
        _mergeState.value = MergeWizardState.Loading

        viewModelScope.launch {
            try {
                val cacheDir = getApplication<Application>().cacheDir
                val handle = withContext(Dispatchers.IO) {
                    MergeEngine.openArchive(getApplication(), uri, cacheDir)
                        .getOrThrow()
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

                val pseudoOrderMap = HashMap(orderPlan.existing)
                for (add in orderPlan.toAdd) pseudoOrderMap[add.theirId] = -1L

                val mySamples = repo.getSamplesForOrders(
                    (orderPlan.existing.values + orderPlan.toAdd.map { it.theirId }).toList()
                ).filter { it.orderId > 0 }

                val samplePlan = MergeEngine.planSamples(
                    mySamples = mySamples,
                    theirSamples = archive.samples,
                    orderIdMap = pseudoOrderMap,
                    myOrders = myOrders,
                    myAreas = myAreas
                )

                val pseudoSampleMap = HashMap<Long, Long>()
                for (add in samplePlan.toAdd) pseudoSampleMap[add.theirId] = -1L
                for (m in samplePlan.identical) pseudoSampleMap[m.theirId] = m.myId
                for (c in samplePlan.conflicts) pseudoSampleMap[c.theirId] = c.myId

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
                    areaPlan, orderPlan, samplePlan, wellPlan, notePlan, photoPlan
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

    fun continueFromPreview() {
        val state = _mergeState.value as? MergeWizardState.Preview ?: return
        val p = state.preview
        if (p.samplePlan.conflicts.isEmpty()) {
            runMerge(emptyMap())
        } else {
            _mergeState.value = MergeWizardState.ConflictStep(
                preview = p,
                resolutions = emptyMap()
            )
        }
    }

    fun setFieldResolution(
        theirSampleId: Long,
        field: SampleField,
        owner: FieldOwner
    ) {
        val state = _mergeState.value as? MergeWizardState.ConflictStep ?: return
        val current = state.resolutions[theirSampleId] ?: FieldResolution.Empty
        val updated = current.with(field, owner)
        val newMap = state.resolutions + (theirSampleId to updated)
        _mergeState.value = state.copy(resolutions = newMap)
    }

    fun setSampleResolution(
        theirSampleId: Long,
        owner: FieldOwner
    ) {
        val state = _mergeState.value as? MergeWizardState.ConflictStep ?: return
        val conflict = state.preview.samplePlan.conflicts
            .firstOrNull { it.theirId == theirSampleId } ?: return
        val updated = FieldResolution.all(conflict.fieldDiffs, owner)
        val newMap = state.resolutions + (theirSampleId to updated)
        _mergeState.value = state.copy(resolutions = newMap)
    }

    fun applyMassStrategy(owner: FieldOwner) {
        val state = _mergeState.value as? MergeWizardState.ConflictStep ?: return
        val newMap = HashMap<Long, FieldResolution>()
        for (c in state.preview.samplePlan.conflicts) {
            newMap[c.theirId] = FieldResolution.all(c.fieldDiffs, owner)
        }
        _mergeState.value = state.copy(resolutions = newMap)
    }

    fun applyFillEmptyStrategy() {
        val state = _mergeState.value as? MergeWizardState.ConflictStep ?: return
        val newMap = HashMap<Long, FieldResolution>()
        for (c in state.preview.samplePlan.conflicts) {
            newMap[c.theirId] = FieldResolution.fillEmpty(c.fieldDiffs)
        }
        _mergeState.value = state.copy(resolutions = newMap)
    }

    fun applyStrategyForField(field: SampleField, owner: FieldOwner) {
        val state = _mergeState.value as? MergeWizardState.ConflictStep ?: return
        val newMap = HashMap(state.resolutions)
        for (c in state.preview.samplePlan.conflicts) {
            if (c.fieldDiffs.any { it.field == field }) {
                val cur = newMap[c.theirId] ?: FieldResolution.Empty
                newMap[c.theirId] = cur.with(field, owner)
            }
        }
        _mergeState.value = state.copy(resolutions = newMap)
    }

    fun applyGroupMass(
        conflicts: List<SampleConflict>,
        strategy: MassStrategy
    ) {
        val state = _mergeState.value as? MergeWizardState.ConflictStep ?: return
        if (conflicts.isEmpty()) return
        val newMap = HashMap(state.resolutions)
        for (c in conflicts) {
            newMap[c.theirId] = when (strategy) {
                MassStrategy.ALL_MINE ->
                    FieldResolution.all(c.fieldDiffs, FieldOwner.MINE)
                MassStrategy.ALL_THEIRS ->
                    FieldResolution.all(c.fieldDiffs, FieldOwner.THEIRS)
                MassStrategy.FILL_EMPTY ->
                    FieldResolution.fillEmpty(c.fieldDiffs)
            }
        }
        _mergeState.value = state.copy(resolutions = newMap)
    }

    fun allConflictsResolved(): Boolean {
        val state = _mergeState.value as? MergeWizardState.ConflictStep ?: return true
        return state.preview.samplePlan.allResolved(state.resolutions)
    }

    fun confirmConflicts() {
        val state = _mergeState.value as? MergeWizardState.ConflictStep ?: return
        val finalMap = HashMap<Long, FieldResolution>()
        for (c in state.preview.samplePlan.conflicts) {
            val cur = state.resolutions[c.theirId] ?: FieldResolution.Empty
            val missing = c.fieldDiffs
                .filter { cur.ownerOf(it.field) == null }
                .map { it.field }
            var complete = cur
            for (f in missing) complete = complete.with(f, FieldOwner.MINE)
            finalMap[c.theirId] = complete
        }
        runMerge(finalMap)
    }

    private fun runMerge(resolutions: Map<Long, FieldResolution>) {
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
        _mergeState.value = MergeWizardState.Idle
    }

    fun resetMergeState() {
        cleanupMergeHandle()
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
    // Откат
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
        const val AUTO_BACKUP_OP_DIAGNOSTICS = "diagnostics"
    }
}