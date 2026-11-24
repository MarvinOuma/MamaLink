package com.mamalink.app.ui.profiles

import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import com.mamalink.app.data.RoomTestBase
import com.mamalink.app.data.awaitValue
import com.mamalink.app.data.repository.RoomAppointmentRepository
import com.mamalink.app.data.repository.RoomCaregiverRepository
import com.mamalink.app.data.repository.RoomChildRepository
import com.mamalink.app.domain.model.Appointment
import com.mamalink.app.domain.model.Caregiver
import com.mamalink.app.domain.model.Child
import com.mamalink.app.ui.appointment.AppointmentViewModel
import com.mamalink.app.ui.caregiver.CaregiverViewModel
import com.mamalink.app.ui.child.ChildViewModel
import com.mamalink.app.ui.common.RepositoryOperationState
import com.mamalink.app.ui.common.RepositoryOperationState.Operation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ViewModelRoomIntegrationTest : RoomTestBase() {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val clock = Clock.fixed(Instant.parse("2026-11-24T12:00:00Z"), ZoneOffset.UTC)
    private val caregiverVm get() = CaregiverViewModel(RoomCaregiverRepository(database.caregiverDao()))
    private val childVm get() = ChildViewModel(RoomChildRepository(database.childDao()), clock)
    private val appointmentVm get() = AppointmentViewModel(RoomAppointmentRepository(database.appointmentDao()), clock)

    private suspend fun LiveData<RepositoryOperationState>.result() = asFlow().first { !it.inProgress }
    private suspend fun LiveData<RepositoryOperationState>.success(operation: Operation): Long {
        val state = result()
        assertNull(state.error)
        assertEquals(operation, state.completedOperation)
        return checkNotNull(state.affectedId)
    }
    private suspend fun owner(name: String = "Demo caregiver"): Long {
        val vm = caregiverVm
        vm.addCaregiver(Caregiver(0, name))
        return vm.operationState.success(Operation.INSERT)
    }
    private suspend fun child(owner: Long): Long {
        val vm = childVm
        vm.addChild(Child(0, owner, "Demo child", LocalDate.of(2024, 2, 29)))
        return vm.operationState.success(Operation.INSERT)
    }

    @Test fun `all three ViewModels persist observe update and delete linked domain records`() = runTest {
        val caregivers = caregiverVm
        caregivers.addCaregiver(Caregiver(999, "Demo caregiver", "+2540700000000"))
        val owner = caregivers.operationState.success(Operation.INSERT)
        val savedCaregiver = Caregiver(owner, "Demo caregiver", "+2540700000000")
        assertEquals(savedCaregiver, caregivers.observeCaregiver(owner).awaitValue { it != null })
        assertEquals(listOf(savedCaregiver), caregivers.caregivers.awaitValue { it.isNotEmpty() })

        val children = childVm
        val child = Child(999, owner, "Demo child", LocalDate.of(2024, 2, 29))
        children.addChild(child)
        val childId = children.operationState.success(Operation.INSERT)
        val savedChild = child.copy(id = childId)
        assertEquals(savedChild, children.observeChild(childId).awaitValue { it != null })
        assertEquals(listOf(savedChild), children.observeChildren(owner).awaitValue { it.isNotEmpty() })

        val appointments = appointmentVm
        val visit = Appointment(999, owner, childId, "Demo visit", clock.instant().plusSeconds(3600), "Demo room")
        appointments.addAppointment(visit)
        val visitId = appointments.operationState.success(Operation.INSERT)
        assertEquals(visit.copy(id = visitId), appointments.observeAppointment(visitId).awaitValue { it != null })
        assertEquals(listOf(visit.copy(id = visitId)), appointments.observeAppointments(owner).awaitValue { it.isNotEmpty() })

        caregivers.updateCaregiver(savedCaregiver.copy(fullName = "Updated caregiver"))
        caregivers.operationState.success(Operation.UPDATE)
        children.updateChild(savedChild.copy(name = "Updated child"))
        children.operationState.success(Operation.UPDATE)
        appointments.updateAppointment(visit.copy(id = visitId, title = "Corrected visit", scheduledAt = Instant.EPOCH))
        appointments.operationState.success(Operation.UPDATE)
        assertEquals("Updated caregiver", caregivers.observeCaregiver(owner).awaitValue { it?.fullName == "Updated caregiver" }?.fullName)
        assertEquals("Updated child", children.observeChild(childId).awaitValue { it?.name == "Updated child" }?.name)
        assertEquals(Instant.EPOCH, appointments.observeAppointment(visitId).awaitValue { it?.scheduledAt == Instant.EPOCH }?.scheduledAt)

        appointments.deleteAppointment(visitId)
        appointments.operationState.success(Operation.DELETE)
        children.deleteChild(childId)
        children.operationState.success(Operation.DELETE)
        caregivers.deleteCaregiver(owner)
        caregivers.operationState.success(Operation.DELETE)
        assertTrue(appointments.observeAppointments(owner).awaitValue { it.isEmpty() }.isEmpty())
        assertTrue(children.observeChildren(owner).awaitValue { it.isEmpty() }.isEmpty())
        assertTrue(caregivers.caregivers.awaitValue { it.isEmpty() }.isEmpty())
    }

    @Test fun `Room rejects nonexistent and cross caregiver links through ViewModels safely`() = runTest {
        val first = owner()
        val second = owner("Other demo caregiver")
        val child = child(first)
        val children = childVm
        children.addChild(Child(0, 99999, "Demo", LocalDate.of(2024, 1, 1)))
        assertNotNull(children.operationState.result().error)
        val appointments = appointmentVm
        for (value in listOf(
            Appointment(0, second, child, "Demo", clock.instant()),
            Appointment(0, first, 99999, "Demo", clock.instant()),
            Appointment(0, 99999, null, "Demo", clock.instant()),
        )) {
            appointments.addAppointment(value)
            val state = appointments.operationState.result()
            assertTrue(state.error.orEmpty().startsWith("Unable to change"))
            assertNull(state.completedOperation)
            assertNull(state.affectedId)
        }
        assertEquals(first, database.childDao().getChildById(child)?.caregiverId)
        assertTrue(database.appointmentDao().getAppointmentsByCaregiverId(first).isEmpty())
        assertTrue(database.appointmentDao().getAppointmentsByCaregiverId(second).isEmpty())
        appointments.addAppointment(Appointment(0, first, child, "Valid retry", clock.instant()))
        appointments.operationState.success(Operation.INSERT)
    }

    @Test fun `restricted deletion and reassignment errors preserve data and retry succeeds in order`() = runTest {
        val owner = owner()
        val other = owner("Other demo caregiver")
        val child = child(owner)
        val appointments = appointmentVm
        appointments.addAppointment(Appointment(0, owner, child, "Demo visit", clock.instant()))
        val visit = appointments.operationState.success(Operation.INSERT)
        val caregivers = caregiverVm
        val children = childVm

        caregivers.deleteCaregiver(owner)
        assertNotNull(caregivers.operationState.result().error)
        children.deleteChild(child)
        assertNotNull(children.operationState.result().error)
        children.updateChild(Child(child, other, "Demo child", LocalDate.of(2024, 2, 29)))
        assertNotNull(children.operationState.result().error)
        appointments.updateAppointment(Appointment(visit, other, child, "Demo visit", clock.instant()))
        assertNotNull(appointments.operationState.result().error)
        assertEquals(owner, database.childDao().getChildById(child)?.caregiverId)
        assertEquals(owner, database.appointmentDao().getAppointmentById(visit)?.caregiverId)
        assertNotNull(database.caregiverDao().getCaregiverById(owner))

        appointments.deleteAppointment(visit)
        appointments.operationState.success(Operation.DELETE)
        children.deleteChild(child)
        children.operationState.success(Operation.DELETE)
        caregivers.deleteCaregiver(owner)
        caregivers.operationState.success(Operation.DELETE)
        assertNull(database.caregiverDao().getCaregiverById(owner))
    }

    @Test fun `nullable fields and missing saved rows follow real repository contract`() = runTest {
        val owner = owner()
        val caregivers = caregiverVm
        assertNull(caregivers.observeCaregiver(owner).awaitValue { it != null }?.phoneNumber)
        val appointments = appointmentVm
        appointments.addAppointment(Appointment(0, owner, null, "Caregiver only", clock.instant(), null))
        val visit = appointments.operationState.success(Operation.INSERT)
        val saved = appointments.observeAppointment(visit).awaitValue { it != null }!!
        assertNull(saved.childId)
        assertNull(saved.location)
        val children = childVm
        val absent = 99999L
        caregivers.updateCaregiver(Caregiver(absent, "Missing"))
        assertEquals("Caregiver was not found.", caregivers.operationState.result().error)
        children.updateChild(Child(absent, owner, "Missing", LocalDate.of(2024, 1, 1)))
        assertEquals("Child was not found.", children.operationState.result().error)
        appointments.updateAppointment(saved.copy(id = absent))
        assertEquals("Appointment was not found.", appointments.operationState.result().error)
        caregivers.deleteCaregiver(absent)
        assertEquals("Caregiver was not found.", caregivers.operationState.result().error)
        children.deleteChild(absent)
        assertEquals("Child was not found.", children.operationState.result().error)
        appointments.deleteAppointment(absent)
        assertEquals("Appointment was not found.", appointments.operationState.result().error)
        assertEquals(saved, appointments.observeAppointment(visit).awaitValue { it != null })
    }
}
