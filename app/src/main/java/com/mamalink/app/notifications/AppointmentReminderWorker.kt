package com.mamalink.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mamalink.app.MamaLinkApplication
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
        val appointment = app.appointmentRepository.getById(appointmentId)
            ?: return Result.success()
        if (!isCurrentUpcomingAppointment(
                appointment,
                expectedScheduledAt,
                Clock.systemDefaultZone().instant(),
            )
        ) return Result.success()

        val formattedTime = LocalDateTime.ofInstant(
            appointment.scheduledAt,
            ZoneId.systemDefault(),
        ).format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))

        AppointmentNotificationHelper.showAppointmentNotification(
            applicationContext,
            appointment.id,
            appointment.title,
            formattedTime,
        )
        return Result.success()
    }

    companion object {
        const val KEY_APPOINTMENT_ID = "appointment_id"
        const val KEY_SCHEDULED_AT = "scheduled_at"
    }
}
