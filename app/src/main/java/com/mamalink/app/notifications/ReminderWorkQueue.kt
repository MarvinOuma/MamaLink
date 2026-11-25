package com.mamalink.app.notifications

import android.content.Context
import androidx.work.*
import com.mamalink.app.domain.model.Appointment
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Suspend until WorkManager's database transaction completes, without blocking its executor. */
internal suspend fun Operation.awaitPersistence() = suspendCancellableCoroutine<Unit> { continuation ->
    result.addListener({
        try {
            result.get()
            continuation.resume(Unit)
        } catch (failure: Exception) {
            continuation.resumeWithException(failure.cause ?: failure)
        }
    }, java.util.concurrent.Executor { it.run() })
    // Cancelling a caller must not cancel an already submitted durable operation.
}

interface ReminderWorkQueue {
    suspend fun schedule(appointment: Appointment, delayMillis: Long, replace: Boolean)
    suspend fun cancel(id: Long)
    suspend fun ensureRecovery()
    suspend fun requestReconciliation()
}

class AndroidReminderWorkQueue(context: Context) : ReminderWorkQueue {
    private val applicationContext = context.applicationContext
    private val manager get() = WorkManager.getInstance(applicationContext)
    override suspend fun schedule(appointment: Appointment, delayMillis: Long, replace: Boolean) {
        val request = OneTimeWorkRequestBuilder<AppointmentReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(
                AppointmentReminderWorker.KEY_APPOINTMENT_ID to appointment.id,
                AppointmentReminderWorker.KEY_SCHEDULED_AT to appointment.scheduledAt.toEpochMilli(),
            ))
            .addTag(WorkManagerAppointmentReminderScheduler.TAG_APPOINTMENT_REMINDER)
            .build()
        manager.enqueueUniqueWork(
            WorkManagerAppointmentReminderScheduler.getWorkName(appointment.id),
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        ).awaitPersistence()
    }
    override suspend fun cancel(id: Long) {
        manager.cancelUniqueWork(WorkManagerAppointmentReminderScheduler.getWorkName(id)).awaitPersistence()
    }
    override suspend fun ensureRecovery() {
        manager.enqueueUniquePeriodicWork(
            RECOVERY_NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderReconciliationWorker>(15, TimeUnit.MINUTES).build(),
        ).awaitPersistence()
    }
    override suspend fun requestReconciliation() {
        ensureRecovery()
        manager.enqueueUniqueWork(
            RECONCILE_NAME, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ReminderReconciliationWorker>().build(),
        ).awaitPersistence()
    }
    companion object {
        const val RECOVERY_NAME = "appointment_reminder_recovery"
        const val RECONCILE_NAME = "appointment_reminder_reconciliation"
    }
}
