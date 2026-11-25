package com.mamalink.app.notifications

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.mamalink.app.domain.model.Appointment
import com.mamalink.app.ui.appointment.AppointmentViewModel
import com.mamalink.app.ui.profiles.FakeAppointments
import com.mamalink.app.ui.profiles.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class AppointmentReminderIntegrationTest {
    @get:Rule val instantExecutor = InstantTaskExecutorRule()
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val now = Instant.parse("2026-11-24T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val sample = Appointment(
        id = 9L,
        caregiverId = 3L,
        childId = null,
        title = "Routine visit",
        scheduledAt = now.plusSeconds(48 * 60 * 60),
        location = null,
    )

    private class RecordingScheduler : AppointmentReminderScheduler {
        val scheduled = linkedMapOf<Long, Appointment>()
        val cancelled = mutableListOf<Long>()
        var scheduleCalls = 0

        override suspend fun scheduleReminder(appointment: Appointment) {
            scheduleCalls++
            scheduled[appointment.id] = appointment
            cancelled.remove(appointment.id)
        }

        override suspend fun cancelReminder(appointmentId: Long) {
            scheduled.remove(appointmentId)
            cancelled += appointmentId
        }

        override suspend fun reconcileReminders(appointments: List<Appointment>) {
            val currentIds = appointments.mapTo(mutableSetOf()) { it.id }
            (scheduled.keys - currentIds).forEach { cancelReminder(it) }
            appointments.forEach { scheduleReminder(it) }
        }
    }

    @Test
    fun `create schedules only after repository insert succeeds`() = runTest {
        val repository = FakeAppointments()
        val scheduler = RecordingScheduler()
        val viewModel = AppointmentViewModel(repository, clock, scheduler)

        repository.failure = IllegalStateException("database unavailable")
        viewModel.addAppointment(sample.copy(id = 0L))
        assertTrue(scheduler.scheduled.isEmpty())

        repository.failure = null
        viewModel.addAppointment(sample.copy(id = 0L))
        assertEquals(sample.copy(id = repository.generatedId), scheduler.scheduled[repository.generatedId])
    }

    @Test
    fun `edit replaces reminder and delete cancels it by stable appointment ID`() = runTest {
        val repository = FakeAppointments()
        val scheduler = RecordingScheduler()
        val viewModel = AppointmentViewModel(repository, clock, scheduler)
        val updated = sample.copy(scheduledAt = now.plusSeconds(72 * 60 * 60))

        viewModel.updateAppointment(sample)
        viewModel.updateAppointment(updated)
        assertEquals(2, scheduler.scheduleCalls)
        assertEquals(updated, scheduler.scheduled[sample.id])

        viewModel.deleteAppointment(sample.id)
        assertNull(scheduler.scheduled[sample.id])
        assertEquals(listOf(sample.id), scheduler.cancelled)
    }

    @Test
    fun `repeated save while write is pending cannot enqueue duplicate reminders`() = runTest {
        val repository = FakeAppointments()
        val scheduler = RecordingScheduler()
        val viewModel = AppointmentViewModel(repository, clock, scheduler)
        repository.gate = CompletableDeferred()

        viewModel.addAppointment(sample.copy(id = 0L))
        viewModel.addAppointment(sample.copy(id = 0L))
        assertEquals(1, repository.calls)
        assertTrue(scheduler.scheduled.isEmpty())

        repository.gate!!.complete(Unit)
        runCurrent()
        assertEquals(1, scheduler.scheduleCalls)
        assertEquals(setOf(repository.generatedId), scheduler.scheduled.keys)
    }

    @Test
    fun `reconciliation replaces existing reminders with persisted appointment data`() = runTest {
        val scheduler = RecordingScheduler()
        scheduler.scheduleReminder(sample)
        val persisted = sample.copy(title = "Updated visit", scheduledAt = now.plusSeconds(96 * 60 * 60))

        scheduler.reconcileReminders(listOf(persisted))

        assertEquals(1, scheduler.scheduled.size)
        assertEquals(persisted, scheduler.scheduled[sample.id])
    }

    @Test
    fun `reminder delay uses 24 hours and handles near and past appointments`() {
        assertEquals(
            24 * 60 * 60 * 1000L,
            calculateReminderDelayMillis(now.plusSeconds(48 * 60 * 60), now),
        )
        assertEquals(0L, calculateReminderDelayMillis(now.plusSeconds(60 * 60), now))
        assertNull(calculateReminderDelayMillis(now.minusSeconds(1), now))
    }

    @Test
    fun `notification is suppressed for removed rescheduled or elapsed appointments`() {
        assertFalse(isCurrentUpcomingAppointment(null, sample.scheduledAt.toEpochMilli(), now))
        assertFalse(
            isCurrentUpcomingAppointment(
                sample.copy(scheduledAt = sample.scheduledAt.plusSeconds(60)),
                sample.scheduledAt.toEpochMilli(),
                now,
            ),
        )
        assertFalse(
            isCurrentUpcomingAppointment(
                sample.copy(scheduledAt = now),
                now.toEpochMilli(),
                now,
            ),
        )
        assertTrue(isCurrentUpcomingAppointment(sample, sample.scheduledAt.toEpochMilli(), now))
    }
}
