package com.mamalink.app.notifications

import android.content.Context
import com.mamalink.app.domain.model.Appointment
import java.time.Clock
import java.time.Duration
import com.mamalink.app.MamaLinkApplication
import com.mamalink.app.domain.repository.AppointmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    suspend fun scheduleReminder(appointment: Appointment)
    suspend fun cancelReminder(appointmentId: Long)
    suspend fun reconcileReminders(appointments: List<Appointment>)
    /** Recovery must already be durable before committing a change to Room. */
    suspend fun <T> persistChange(change: suspend () -> T): T = change()
}

class WorkManagerAppointmentReminderScheduler(
    private val context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val repository: AppointmentRepository = (context.applicationContext as MamaLinkApplication).appointmentRepository,
    private val queue: ReminderWorkQueue = AndroidReminderWorkQueue(context),
) : AppointmentReminderScheduler {
    private val state = ReminderState(context)

    override suspend fun <T> persistChange(change: suspend () -> T): T = withContext(Dispatchers.IO) {
        coordination.withLock {
            // If registration fails, do not commit a write that has no durable repair path.
            queue.ensureRecovery()
            state.markDirty()
            // Capture IDs before deletion as well, including reminders created before this journal existed.
            repository.getAll().forEach {
                state.remember(it.id)
                if (state.observedRevision(it.id) == null) state.observed(it.id, it.scheduledAt.toEpochMilli())
            }
            change()
        }
    }

    override suspend fun scheduleReminder(appointment: Appointment) = withContext(Dispatchers.IO) {
        coordination.withLock {
            state.markDirty()
            // Never trust an asynchronous caller's snapshot, even for ordinary CRUD.
            synchronize(appointment.id, repository.getById(appointment.id))
        }
    }

    override suspend fun cancelReminder(appointmentId: Long) = withContext(Dispatchers.IO) {
        coordination.withLock {
            state.markDirty()
            synchronize(appointmentId, repository.getById(appointmentId))
        }
    }

    override suspend fun reconcileReminders(appointments: List<Appointment>) = withContext(Dispatchers.IO) {
        coordination.withLock {
            state.markDirty()
            // Read inside the same lock as writes; the supplied list is only an ID hint.
            val current = repository.getAll().associateBy { it.id }
            (current.keys + state.knownIds() + appointments.map { it.id }).forEach { id ->
                synchronize(id, repository.getById(id))
            }
            state.markClean()
        }
    }

    private suspend fun synchronize(id: Long, appointment: Appointment?) {
        if (id <= 0L) return
        if (appointment == null) {
            // Keep the ID until both operations succeed so a failed cancel is recoverable.
            state.remember(id)
            AppointmentNotificationHelper.cancel(context, id)
            queue.cancel(id)
            state.forget(id)
            return
        }
        val scheduledAt = appointment.scheduledAt
        val revision = scheduledAt.toEpochMilli()
        state.remember(id)
        val observedRevision = state.observedRevision(id)
        if (observedRevision != null && observedRevision != revision) {
            // A new persisted time invalidates the visible old reminder, even if enqueue fails.
            AppointmentNotificationHelper.cancel(context, id)
        }
        state.observed(id, revision)
        val delayMillis = calculateReminderDelayMillis(scheduledAt, clock.instant())
        if (delayMillis == null || state.wasDelivered(id, revision)) {
            queue.cancel(id)
            if (delayMillis == null) AppointmentNotificationHelper.cancel(context, id)
            return
        }
        queue.schedule(appointment, delayMillis, state.scheduledRevision(id) != revision)
        state.scheduled(id, revision)
    }

    /** The worker uses the same lock as mutations and rechecks Room immediately before posting. */
    internal suspend fun deliver(id: Long, revision: Long) = withContext(Dispatchers.IO) {
        coordination.withLock {
            val appointment = repository.getById(id)
            if (!isCurrentUpcomingAppointment(appointment, revision, clock.instant()) ||
                state.wasDelivered(id, revision) || !AppointmentNotificationHelper.canNotify(context)
            ) return@withLock
            val current = checkNotNull(appointment)
            // Persist the claim first: completed revisions must never alert again after recreation.
            state.delivered(id, revision)
            try {
                if (!AppointmentNotificationHelper.showAppointmentNotification(
                        context, id, current.title,
                        java.time.LocalDateTime.ofInstant(current.scheduledAt, java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a")),
                    )
                ) state.undoDelivery(id, revision)
            } catch (failure: Exception) {
                state.undoDelivery(id, revision)
                throw failure
            }
        }
    }

    companion object {
        private val coordination = Mutex()
        const val TAG_APPOINTMENT_REMINDER = "appointment_reminder"
        fun getWorkName(appointmentId: Long): String = "appointment_reminder_$appointmentId"
    }
}
