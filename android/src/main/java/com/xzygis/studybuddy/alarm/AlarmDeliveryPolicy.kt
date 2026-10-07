package com.xzygis.studybuddy.alarm

import com.xzygis.studybuddy.data.AlarmBinding
import com.xzygis.studybuddy.data.PlanDatabase
import com.xzygis.studybuddy.data.SyncPhase

internal fun PlanDatabase.activeAlarmBinding(
    planId: String,
    bindingId: String,
): AlarmBinding? {
    val record = records.firstOrNull { it.plan.id == planId } ?: return null
    if (!record.wantsEnabled || record.pendingDeletion || record.phase != SyncPhase.ON) return null
    return record.bindings.firstOrNull { it.id == bindingId }
}
