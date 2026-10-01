package com.xzygis.studybuddy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xzygis.studybuddy.alarm.AndroidAlarmScheduler
import com.xzygis.studybuddy.data.AlarmBinding
import com.xzygis.studybuddy.data.PlanDatabase
import com.xzygis.studybuddy.data.PlanRecord
import com.xzygis.studybuddy.data.PlanRepository
import com.xzygis.studybuddy.data.StudyPlan
import com.xzygis.studybuddy.data.SyncPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PlanUiState(
    val database: PlanDatabase = PlanDatabase(),
    val isBusy: Boolean = true,
    val storageIssue: String? = null,
    val message: String? = null,
)

class PlanViewModel(
    private val repository: PlanRepository,
    private val scheduler: AndroidAlarmScheduler,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PlanUiState())
    val state: StateFlow<PlanUiState> = mutableState.asStateFlow()
    private val operationMutex = Mutex()

    init {
        viewModelScope.launch {
            repository.database
                .catch { error ->
                    mutableState.update {
                        it.copy(
                            isBusy = false,
                            storageIssue = "无法读取本地计划，原数据未被覆盖。${error.message.orEmpty()}",
                        )
                    }
                }
                .collectLatest { database ->
                    mutableState.update { it.copy(database = database, isBusy = false) }
                }
        }
        viewModelScope.launch {
            runCatching { repository.initialize() }
                .onFailure { failStorage(it) }
            reconcile()
        }
    }

    fun isEnabled(record: PlanRecord): Boolean =
        record.wantsEnabled &&
            !record.pendingDeletion &&
            record.phase == SyncPhase.ON &&
            record.installedInSystemClock &&
            record.bindings.isNotEmpty()

    fun statusText(record: PlanRecord): String = when {
        record.pendingDeletion -> "待清理后删除"
        isEnabled(record) -> "已提交到系统时钟"
        record.phase == SyncPhase.ATTENTION -> "需要处理"
        record.wantsEnabled -> if (state.value.isBusy) "正在同步" else "待核对"
        record.bindings.isEmpty() -> "未启用"
        else -> "待清理"
    }

    fun save(plan: StudyPlan, onSaved: () -> Unit = {}) = launchOperation {
        val normalized = plan.normalized()
        normalized.validationMessage()?.let {
            showMessage(it)
            return@launchOperation
        }
        val existing = state.value.database.records.firstOrNull { it.plan.id == normalized.id }
        if (existing == null) {
            persist { database ->
                database.copy(records = database.records + PlanRecord(plan = normalized))
            }
        } else {
            val updated = existing.copy(
                plan = normalized,
                phase = if (existing.wantsEnabled || existing.bindings.isNotEmpty()) {
                    SyncPhase.INSTALLING
                } else {
                    existing.phase
                },
            )
            persistRecord(updated)
            if (updated.wantsEnabled || updated.bindings.isNotEmpty()) {
                synchronize(updated.plan.id)
                if (existing.installedInSystemClock) {
                    showMessage("新时间已写入；请到系统时钟中删除该计划的旧闹钟。")
                }
            }
        }
        onSaved()
    }

    fun setEnabled(
        planId: String,
        enabled: Boolean,
        onCompleted: () -> Unit = {},
    ) = launchOperation {
        val record = record(planId) ?: return@launchOperation
        persistRecord(
            record.copy(
                wantsEnabled = enabled,
                phase = SyncPhase.INSTALLING,
                issue = null,
            ),
        )
        synchronize(planId)
        if (!enabled && record.installedInSystemClock) {
            showMessage("计划已停用；请到系统时钟中手动删除对应闹钟。")
        }
        onCompleted()
    }

    fun delete(planId: String, onDeleted: () -> Unit = {}) = launchOperation {
        val record = record(planId) ?: return@launchOperation
        persistRecord(
            record.copy(
                wantsEnabled = false,
                pendingDeletion = true,
                phase = SyncPhase.INSTALLING,
            ),
        )
        synchronize(planId)
        if (record.installedInSystemClock) {
            showMessage("计划已删除；请到系统时钟中手动删除对应闹钟。")
        }
        if (record(planId) == null) onDeleted()
    }

    fun retry(planId: String) = launchOperation { synchronize(planId) }

    fun reconcile() = launchOperation {
        val records = state.value.database.records.toList()
        records.forEach { original ->
            when {
                original.pendingDeletion -> {
                    cleanupLegacy(original)
                    removeRecord(original.plan.id)
                }
                original.wantsEnabled &&
                    original.phase == SyncPhase.ON &&
                    original.installedInSystemClock &&
                    original.bindings.isNotEmpty() -> Unit
                !original.wantsEnabled && original.bindings.isEmpty() -> {
                    if (original.phase != SyncPhase.OFF || original.issue != null) {
                        persistRecord(
                            original.copy(
                                phase = SyncPhase.OFF,
                                issue = null,
                                installedInSystemClock = false,
                            ),
                        )
                    }
                }
                else -> {
                    cleanupLegacy(original)
                    persistRecord(
                        original.copy(
                            bindings = emptyList(),
                            phase = if (original.wantsEnabled) SyncPhase.ATTENTION else SyncPhase.OFF,
                            issue = if (original.wantsEnabled) {
                                "已切换为系统时钟模式，请重新启用此计划。"
                            } else {
                                null
                            },
                            installedInSystemClock = false,
                        ),
                    )
                }
            }
        }
    }

    fun clearMessage() {
        mutableState.update { it.copy(message = null) }
    }

    private suspend fun synchronize(planId: String) {
        var record = record(planId) ?: return
        cleanupLegacy(record)
        record = record(planId)?.copy(
            bindings = emptyList(),
            issue = null,
            installedInSystemClock = false,
        ) ?: return
        persistRecord(record)

        if (record.pendingDeletion) {
            removeRecord(planId)
            return
        }
        if (!record.wantsEnabled) {
            persistRecord(record.copy(phase = SyncPhase.OFF))
            return
        }
        val bindings = AlarmBinding.forPlan(record.plan)
        record = record.copy(bindings = bindings, phase = SyncPhase.INSTALLING)
        persistRecord(record)
        try {
            bindings.forEach { scheduler.schedule(record.plan.name, it) }
            persistRecord(
                record.copy(
                    phase = SyncPhase.ON,
                    issue = null,
                    installedInSystemClock = true,
                ),
            )
        } catch (error: Exception) {
            persistRecord(
                record.copy(
                    phase = SyncPhase.ATTENTION,
                    issue = "系统时钟可能只写入了部分闹钟，请打开系统时钟核对。${error.message.orEmpty()}",
                ),
            )
        }
    }

    private fun launchOperation(block: suspend () -> Unit) {
        viewModelScope.launch {
            operationMutex.withLock {
                if (state.value.storageIssue != null) return@withLock
                mutableState.update { it.copy(isBusy = true) }
                try {
                    block()
                } catch (error: Exception) {
                    failStorage(error)
                } finally {
                    mutableState.update { it.copy(isBusy = false) }
                }
            }
        }
    }

    private suspend fun persistRecord(record: PlanRecord) {
        persist { database ->
            val index = database.records.indexOfFirst { it.plan.id == record.plan.id }
            if (index < 0) database.copy(records = database.records + record)
            else database.copy(records = database.records.toMutableList().also { it[index] = record })
        }
    }

    private suspend fun removeRecord(planId: String) {
        persist { it.copy(records = it.records.filterNot { record -> record.plan.id == planId }) }
    }

    private suspend fun persist(transform: (PlanDatabase) -> PlanDatabase) {
        val database = repository.update(transform)
        mutableState.update { it.copy(database = database) }
    }

    private fun record(planId: String) =
        state.value.database.records.firstOrNull { it.plan.id == planId }

    private fun cleanupLegacy(record: PlanRecord) {
        if (record.installedInSystemClock) return
        record.bindings.forEach { runCatching { scheduler.cancelLegacyAlarm(it.id) } }
    }

    private fun failStorage(error: Throwable) {
        mutableState.update {
            it.copy(
                isBusy = false,
                storageIssue = "无法写入本地计划，未继续修改系统闹钟。${error.message.orEmpty()}",
            )
        }
    }

    private fun showMessage(message: String) {
        mutableState.update { it.copy(message = message) }
    }

    class Factory(
        private val repository: PlanRepository,
        private val scheduler: AndroidAlarmScheduler,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlanViewModel(repository, scheduler) as T
    }
}
