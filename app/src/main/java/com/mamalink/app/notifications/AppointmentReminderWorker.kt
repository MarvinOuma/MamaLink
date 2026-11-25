package com.mamalink.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mamalink.app.MamaLinkApplication

internal fun isCurrentUpcomingAppointment(
    appointment: com.mamalink.app.domain.model.Appointment?,
    expectedScheduledAt: Long,
    now: java.time.Instant,
): Boolean = appointment != null &&
    appointment.scheduledAt.toEpochMilli() == expectedScheduledAt &&
    appointment.scheduledAt.isAfter(now)

class AppointmentReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val appointmentId = inputData.getLong(KEY_APPOINTMENT_ID, -1L)
        val expectedScheduledAt = inputData.getLong(KEY_SCHEDULED_AT, Long.MIN_VALUE)
        if (appointmentId <= 0L || expectedScheduledAt == Long.MIN_VALUE) return Result.failure()

        val app = applicationContext as? MamaLinkApplication ?: return Result.failure()
        return try {
            app.reminderScheduler.deliver(appointmentId, expectedScheduledAt)
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            android.util.Log.e("AppointmentReminder", "Delivery failed; WorkManager will retry", failure)
            Result.retry()
        }
    }

    companion object {
        const val KEY_APPOINTMENT_ID = "appointment_id"
        const val KEY_SCHEDULED_AT = "scheduled_at"
    }
}
