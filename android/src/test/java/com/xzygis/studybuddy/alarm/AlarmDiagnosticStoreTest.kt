package com.xzygis.studybuddy.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmDiagnosticStoreTest {
    private val scheduled = AlarmDiagnosticEntry(
        occurrenceId = "binding:2000",
        planId = "plan",
        bindingId = "binding",
        title = "数学作业",
        scheduledAt = 1_000,
        triggerAt = 2_000,
    )

    @Test
    fun statusTracksDeliveryStagesAndOverdueAlarm() {
        assertEquals(AlarmDiagnosticStatus.SCHEDULED, scheduled.status(2_000))
        assertEquals(
            AlarmDiagnosticStatus.MISSED,
            scheduled.status(2_000 + 2 * 60 * 1_000L + 1),
        )
        assertEquals(
            AlarmDiagnosticStatus.RECEIVED,
            scheduled.copy(receiverAt = 2_010).status(200_000),
        )
        assertEquals(
            AlarmDiagnosticStatus.SERVICE_STARTED,
            scheduled.copy(receiverAt = 2_010, serviceAt = 2_020).status(200_000),
        )
        assertEquals(
            AlarmDiagnosticStatus.RINGING,
            scheduled.copy(receiverAt = 2_010, serviceAt = 2_020, audioAt = 2_030)
                .status(200_000),
        )
    }

    @Test
    fun errorHasPriorityOverSuccessfulStages() {
        val failed = scheduled.copy(
            receiverAt = 2_010,
            serviceAt = 2_020,
            audioAt = 2_030,
            error = "audio_runtime",
        )

        assertEquals(AlarmDiagnosticStatus.ERROR, failed.status(2_040))
    }

    @Test
    fun receiverCanRecoverMissingScheduledEntry() {
        val received = updateDiagnostic(
            entries = emptyList(),
            occurrenceId = scheduled.occurrenceId,
            fallback = scheduled,
        ) {
            it.copy(receiverAt = 2_010)
        }

        assertEquals(1, received.size)
        assertEquals(2_010L, received.single().receiverAt)
    }

    @Test
    fun reschedulingSameOccurrencePreservesDeliveryEvidence() {
        val delivered = scheduled.copy(
            receiverAt = 2_010,
            serviceAt = 2_020,
            audioAt = 2_030,
            screenAt = 2_040,
        )
        val rescheduled = scheduled.copy(scheduledAt = 1_500)

        val result = upsertDiagnostic(listOf(delivered), rescheduled).single()

        assertEquals(1_500L, result.scheduledAt)
        assertEquals(2_010L, result.receiverAt)
        assertEquals(2_020L, result.serviceAt)
        assertEquals(2_030L, result.audioAt)
        assertEquals(2_040L, result.screenAt)
    }

    @Test
    fun retryingScheduleClearsPreviousScheduleError() {
        val failed = scheduled.copy(error = "schedule: failed")

        val result = upsertDiagnostic(listOf(failed), scheduled.copy(scheduledAt = 1_500)).single()

        assertEquals(null, result.error)
    }
}
