package com.mamalink.app.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mamalink.app.domain.model.Appointment
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit

internal fun calculateReminderDelayMillis(
    scheduledAt: java.time.Instant,
    now: java.time.Instant,
): Long? {
    if (scheduledAt.isBefore(now)) return null
    val reminderAt = scheduledAt.minus(Duration.ofHours(24))
    if (reminderAt.isBefore(now)) return 0L
    return try {
        Duration.between(now, reminderAt).toMillis()
    } catch (_: ArithmeticException) {
        Long.MAX_VALUE
    }
}

interface AppointmentReminderScheduler {
    fun scheduleReminder(appointment: Appointment)
    fun cancelReminder(appointmentId: Long)
    suspend fun reconcileReminders(appointments: List<Appointment>)
}

class WorkManagerAppointmentReminderScheduler(
    private val context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
) : AppointmentReminderScheduler {

    override fun scheduleReminder(appointment: Appointment) {
        if (appointment.id <= 0L) return
        val scheduledAt = appointment.scheduledAt
        val delayMillis = calculateReminderDelayMillis(scheduledAt, clock.instant())
        if (delayMillis == null) {
            cancelReminder(appointment.id)
            return
        }
        val workRequest = OneTimeWorkRequestBuilder<AppointmentReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putLong(AppointmentReminderWorker.KEY_APPOINTMENT_ID, appointment.id)
                    .putLong(
                        AppointmentReminderWorker.KEY_SCHEDULED_AT,
                        scheduledAt.toEpochMilli(),
                    )
                    .build(),
            )
            .addTag(TAG_APPOINTMENT_REMINDER)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            getWorkName(appointment.id),
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
    }

    override fun cancelReminder(appointmentId: Long) {
        if (appointmentId > 0L) {
            WorkManager.getInstance(context).cancelUniqueWork(getWorkName(appointmentId))
        }
    }

    override suspend fun reconcileReminders(appointments: List<Appointment>) {
        val now = clock.instant()
        appointments.distinctBy { it.id }.forEach { appointment ->
            if (appointment.scheduledAt.isBefore(now)) {
                cancelReminder(appointment.id)
            } else {
                scheduleReminder(appointment)
            }
        }
    }

    companion object {
        const val TAG_APPOINTMENT_REMINDER = "appointment_reminder"
        fun getWorkName(appointmentId: Long): String = "appointment_reminder_$appointmentId"
    }
}
