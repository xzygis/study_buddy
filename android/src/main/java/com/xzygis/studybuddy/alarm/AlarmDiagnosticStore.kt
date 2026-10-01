package com.xzygis.studybuddy.alarm

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class AlarmDiagnosticEntry(
    val occurrenceId: String,
    val planId: String,
    val bindingId: String,
    val title: String,
    val scheduledAt: Long,
    val triggerAt: Long,
    val receiverAt: Long? = null,
    val serviceAt: Long? = null,
    val audioAt: Long? = null,
    val screenAt: Long? = null,
    val cancelledAt: Long? = null,
    val error: String? = null,
) {
    fun status(now: Long): AlarmDiagnosticStatus = when {
        error != null -> AlarmDiagnosticStatus.ERROR
        audioAt != null -> AlarmDiagnosticStatus.RINGING
        serviceAt != null -> AlarmDiagnosticStatus.SERVICE_STARTED
        receiverAt != null -> AlarmDiagnosticStatus.RECEIVED
        cancelledAt != null -> AlarmDiagnosticStatus.CANCELLED
        triggerAt + OVERDUE_GRACE_MILLIS < now -> AlarmDiagnosticStatus.MISSED
        else -> AlarmDiagnosticStatus.SCHEDULED
    }

    companion object {
        private const val OVERDUE_GRACE_MILLIS = 2 * 60 * 1_000L
    }
}

enum class AlarmDiagnosticStatus {
    SCHEDULED,
    RECEIVED,
    SERVICE_STARTED,
    RINGING,
    CANCELLED,
    MISSED,
    ERROR,
}

class AlarmDiagnosticStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun recordScheduled(
        occurrenceId: String,
        planId: String,
        bindingId: String,
        title: String,
        scheduledAt: Long,
        triggerAt: Long,
    ) = update { entries ->
        upsertDiagnostic(
            entries,
            AlarmDiagnosticEntry(
                occurrenceId = occurrenceId,
                planId = planId,
                bindingId = bindingId,
                title = title,
                scheduledAt = scheduledAt,
                triggerAt = triggerAt,
            ),
        )
    }

    fun recordReceiver(
        occurrenceId: String,
        planId: String,
        bindingId: String,
        title: String,
        triggerAt: Long,
        timestamp: Long,
    ) = update { entries ->
        updateDiagnostic(
            entries = entries,
            occurrenceId = occurrenceId,
            fallback = AlarmDiagnosticEntry(
                occurrenceId = occurrenceId,
                planId = planId,
                bindingId = bindingId,
                title = title,
                scheduledAt = timestamp,
                triggerAt = triggerAt,
            ),
        ) { it.copy(receiverAt = timestamp) }
    }

    fun recordService(occurrenceId: String, timestamp: Long) =
        updateOccurrence(occurrenceId) { it.copy(serviceAt = timestamp) }

    fun recordAudio(occurrenceId: String, timestamp: Long) =
        updateOccurrence(occurrenceId) { it.copy(audioAt = timestamp) }

    fun recordScreen(occurrenceId: String, timestamp: Long) =
        updateOccurrence(occurrenceId) { it.copy(screenAt = timestamp) }

    fun recordError(occurrenceId: String, stage: String, error: Throwable) =
        recordError(occurrenceId, "$stage: ${error.javaClass.simpleName}: ${error.message.orEmpty()}")

    fun recordError(occurrenceId: String, message: String) =
        updateOccurrence(occurrenceId) { it.copy(error = message.take(MAX_ERROR_LENGTH)) }

    fun recordCancelled(bindingId: String, timestamp: Long) = update { entries ->
        entries.map { entry ->
            if (entry.bindingId == bindingId &&
                entry.cancelledAt == null &&
                entry.receiverAt == null &&
                entry.triggerAt >= timestamp - CANCELLATION_GRACE_MILLIS
            ) {
                entry.copy(cancelledAt = timestamp)
            } else {
                entry
            }
        }
    }

    fun entriesForPlan(planId: String): List<AlarmDiagnosticEntry> =
        read().filter { it.planId == planId }.sortedByDescending { it.triggerAt }

    fun allEntries(): List<AlarmDiagnosticEntry> = read().sortedByDescending { it.triggerAt }

    fun clearPlan(planId: String) = update { entries ->
        entries.filterNot { it.planId == planId }
    }

    private fun updateOccurrence(
        occurrenceId: String,
        transform: (AlarmDiagnosticEntry) -> AlarmDiagnosticEntry,
    ) = update { entries ->
        val index = entries.indexOfFirst { it.occurrenceId == occurrenceId }
        if (index < 0) entries
        else entries.toMutableList().also { it[index] = transform(it[index]) }
    }

    private fun update(transform: (List<AlarmDiagnosticEntry>) -> List<AlarmDiagnosticEntry>) {
        synchronized(lock) {
            val updated = transform(readUnlocked())
                .sortedByDescending { it.scheduledAt }
                .take(MAX_ENTRIES)
            preferences.edit()
                .putString(KEY_ENTRIES, json.encodeToString(serializer, updated))
                .commit()
        }
    }

    private fun read(): List<AlarmDiagnosticEntry> = synchronized(lock) { readUnlocked() }

    private fun readUnlocked(): List<AlarmDiagnosticEntry> {
        val raw = preferences.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFERENCES_NAME = "alarm_diagnostics"
        private const val KEY_ENTRIES = "entries"
        private const val MAX_ENTRIES = 200
        private const val MAX_ERROR_LENGTH = 240
        private const val CANCELLATION_GRACE_MILLIS = 60 * 1_000L
        private val lock = Any()
        private val serializer = ListSerializer(AlarmDiagnosticEntry.serializer())

        fun occurrenceId(bindingId: String, triggerAt: Long): String = "$bindingId:$triggerAt"
    }
}

internal fun upsertDiagnostic(
    entries: List<AlarmDiagnosticEntry>,
    entry: AlarmDiagnosticEntry,
): List<AlarmDiagnosticEntry> {
    val existing = entries.firstOrNull { it.occurrenceId == entry.occurrenceId }
    val value = if (existing == null) {
        entry
    } else {
        entry.copy(
            receiverAt = existing.receiverAt,
            serviceAt = existing.serviceAt,
            audioAt = existing.audioAt,
            screenAt = existing.screenAt,
        )
    }
    return entries.filterNot { it.occurrenceId == entry.occurrenceId } + value
}

internal fun updateDiagnostic(
    entries: List<AlarmDiagnosticEntry>,
    occurrenceId: String,
    fallback: AlarmDiagnosticEntry,
    transform: (AlarmDiagnosticEntry) -> AlarmDiagnosticEntry,
): List<AlarmDiagnosticEntry> {
    val current = entries.firstOrNull { it.occurrenceId == occurrenceId } ?: fallback
    return entries.filterNot { it.occurrenceId == occurrenceId } + transform(current)
}
