package com.mamalink.app.ui.profiles

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.mamalink.app.domain.model.Appointment
import com.mamalink.app.domain.model.Caregiver
import com.mamalink.app.domain.model.Child
import com.mamalink.app.ui.appointment.AppointmentViewModel
import com.mamalink.app.ui.caregiver.CaregiverViewModel
import com.mamalink.app.ui.child.ChildViewModel
import com.mamalink.app.ui.common.RepositoryOperationState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelValidationTest {
    @get:Rule val instantExecutor = InstantTaskExecutorRule()
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val clock = Clock.fixed(Instant.parse("2026-11-24T12:00:00Z"), ZoneOffset.UTC)
    private val child = Child(8, 3, "Demo child", LocalDate.of(2024, 2, 29))
    private val appointment = Appointment(8, 3, null, "Demo visit", clock.instant())

    private fun assertRejected(state: LiveData<RepositoryOperationState>, message: String) {
        assertEquals(message, state.value?.error)
        assertFalse(state.value!!.inProgress)
        assertNull(state.value?.affectedId)
        assertNull(state.value?.completedOperation)
    }

    @Test fun `blank caregiver names fail insert and update before repository`() = runTest {
        val fake = FakeCaregivers()
        val vm = CaregiverViewModel(fake)
        for (name in listOf("", "  ", "\n\t")) {
            val value = Caregiver(8, name)
            vm.addCaregiver(value)
            assertRejected(vm.operationState, "Caregiver name is required.")
            vm.updateCaregiver(value)
            assertRejected(vm.operationState, "Caregiver name is required.")
        }
        assertEquals(0, fake.calls)
        vm.addCaregiver(Caregiver(0, "Demo"))
        assertNull(vm.operationState.value?.error)
        assertEquals(1, fake.calls)
        assertNull(fake.lastValue?.phoneNumber)
    }

    @Test fun `blank child names and missing caregivers fail both write paths`() = runTest {
        val fake = FakeChildren()
        val vm = ChildViewModel(fake, clock)
        for (value in listOf(child.copy(name = " \t"), child.copy(caregiverId = 0), child.copy(caregiverId = -1))) {
            vm.addChild(value)
            assertNotNull(vm.operationState.value?.error)
            vm.updateChild(value)
            assertNotNull(vm.operationState.value?.error)
        }
        assertEquals(0, fake.calls)
    }

    @Test fun `future birth dates fail insert and update but today and leap dates pass`() = runTest {
        val fake = FakeChildren()
        val vm = ChildViewModel(fake, clock)
        val tomorrow = child.copy(dateOfBirth = LocalDate.now(clock).plusDays(1))
        vm.addChild(tomorrow)
        assertRejected(vm.operationState, "Birth date cannot be in the future.")
        vm.updateChild(tomorrow)
        assertRejected(vm.operationState, "Birth date cannot be in the future.")
        assertEquals(0, fake.calls)
        vm.addChild(child.copy(dateOfBirth = LocalDate.now(clock)))
        vm.updateChild(child)
        assertEquals(2, fake.calls)
        assertNull(vm.operationState.value?.error)
    }

    @Test fun `birth date validation uses clock local calendar across UTC midnight`() = runTest {
        val localClock = Clock.fixed(Instant.parse("2026-11-24T22:00:00Z"), ZoneId.of("Africa/Nairobi"))
        val fake = FakeChildren()
        val vm = ChildViewModel(fake, localClock)
        vm.addChild(child.copy(dateOfBirth = LocalDate.of(2026, 11, 25)))
        assertEquals(1, fake.calls)
        assertNull(vm.operationState.value?.error)
        vm.addChild(child.copy(dateOfBirth = LocalDate.of(2026, 11, 26)))
        assertRejected(vm.operationState, "Birth date cannot be in the future.")
        assertEquals(1, fake.calls)
    }

    @Test fun `appointment title caregiver and optional child IDs are validated before writes`() = runTest {
        val fake = FakeAppointments()
        val vm = AppointmentViewModel(fake, clock)
        for (value in listOf(
            appointment.copy(title = "\n "), appointment.copy(caregiverId = 0),
            appointment.copy(caregiverId = -1), appointment.copy(childId = 0), appointment.copy(childId = -1),
        )) {
            vm.addAppointment(value)
            assertNotNull(vm.operationState.value?.error)
            vm.updateAppointment(value)
            assertNotNull(vm.operationState.value?.error)
        }
        assertEquals(0, fake.calls)
    }

    @Test fun `new appointments reject past instants and accept now and future`() = runTest {
        val fake = FakeAppointments()
        val vm = AppointmentViewModel(fake, clock)
        vm.addAppointment(appointment.copy(scheduledAt = clock.instant().minusNanos(1)))
        assertRejected(vm.operationState, "New appointment time cannot be in the past.")
        assertEquals(0, fake.calls)
        vm.addAppointment(appointment)
        assertEquals(appointment.copy(id = 0), fake.lastValue)
        vm.addAppointment(appointment.copy(scheduledAt = clock.instant().plusSeconds(60), childId = 4, location = "Demo room"))
        assertEquals(2, fake.calls)
        assertEquals(4L, fake.lastValue?.childId)
        assertEquals("Demo room", fake.lastValue?.location)
        assertNull(vm.operationState.value?.error)
    }

    @Test fun `existing appointments can be corrected after scheduled time`() = runTest {
        val fake = FakeAppointments()
        val vm = AppointmentViewModel(fake, clock)
        val past = appointment.copy(scheduledAt = Instant.EPOCH)
        vm.updateAppointment(past)
        assertEquals(past, fake.lastValue)
        assertNull(vm.operationState.value?.error)
        assertEquals(RepositoryOperationState.Operation.UPDATE, vm.operationState.value?.completedOperation)
    }

    @Test fun `out of range appointment times are rejected before Room conversion`() = runTest {
        val fake = FakeAppointments()
        val vm = AppointmentViewModel(fake, clock)
        for (time in listOf(Instant.MIN, Instant.MAX)) {
            vm.addAppointment(appointment.copy(scheduledAt = time))
            assertRejected(vm.operationState, "Appointment time is outside the supported range.")
            vm.updateAppointment(appointment.copy(scheduledAt = time))
            assertRejected(vm.operationState, "Appointment time is outside the supported range.")
        }
        assertEquals(0, fake.calls)
        vm.updateAppointment(appointment.copy(scheduledAt = Instant.ofEpochMilli(Long.MIN_VALUE)))
        vm.updateAppointment(appointment.copy(scheduledAt = Instant.ofEpochMilli(Long.MAX_VALUE)))
        assertEquals(2, fake.calls)
    }

    @Test fun `updates require positive saved IDs for all profile types`() = runTest {
        val caregivers = FakeCaregivers()
        val children = FakeChildren()
        val appointments = FakeAppointments()
        val caregiverVm = CaregiverViewModel(caregivers)
        val childVm = ChildViewModel(children, clock)
        val appointmentVm = AppointmentViewModel(appointments, clock)
        for (id in listOf(0L, -1L)) {
            caregiverVm.updateCaregiver(Caregiver(id, "Demo"))
            childVm.updateChild(child.copy(id = id))
            appointmentVm.updateAppointment(appointment.copy(id = id))
            assertRejected(caregiverVm.operationState, "A saved caregiver is required.")
            assertRejected(childVm.operationState, "A saved child is required.")
            assertRejected(appointmentVm.operationState, "A saved appointment is required.")
        }
        assertEquals(0, caregivers.calls + children.calls + appointments.calls)
    }

    @Test fun `factories create expected ViewModels and reject unrelated types`() {
        val factories = listOf(
            CaregiverViewModel.Factory(FakeCaregivers()) to CaregiverViewModel::class.java,
            ChildViewModel.Factory(FakeChildren()) to ChildViewModel::class.java,
            AppointmentViewModel.Factory(FakeAppointments()) to AppointmentViewModel::class.java,
        )
        for ((factory, type) in factories) {
            assertEquals(type, factory.create(type).javaClass)
            assertThrows(IllegalArgumentException::class.java) { factory.create(ViewModel::class.java) }
        }
    }
}
