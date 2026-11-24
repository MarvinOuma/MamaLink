package com.mamalink.app.ui.profiles

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.mamalink.app.domain.model.Appointment
import com.mamalink.app.domain.model.Caregiver
import com.mamalink.app.domain.model.Child
import com.mamalink.app.ui.appointment.AppointmentViewModel
import com.mamalink.app.ui.caregiver.CaregiverViewModel
import com.mamalink.app.ui.child.ChildViewModel
import com.mamalink.app.ui.common.RepositoryOperationState
import com.mamalink.app.ui.common.RepositoryOperationState.Operation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Run the same externally observable repository contract against all three ViewModels. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class ViewModelRepositoryTest(private val kind: String) {
    @get:Rule val instantExecutor = InstantTaskExecutorRule()
    @get:Rule val mainDispatcher = MainDispatcherRule()

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun kinds() = listOf("caregiver", "child", "appointment")
    }

    private val clock = Clock.fixed(Instant.parse("2026-11-24T12:00:00Z"), ZoneOffset.UTC)

    private class Harness<T>(
        val fake: FakeWrites<T>,
        val vm: ViewModel,
        val state: LiveData<RepositoryOperationState>,
        val sample: T,
        val newValue: T,
        val add: () -> Unit,
        val update: () -> Unit,
        val delete: (Long) -> Unit,
        val list: LiveData<List<T>>,
        val single: (Long) -> LiveData<T?>,
    )

    private fun harness(): Harness<out Any> = when (kind) {
        "caregiver" -> {
            val fake = FakeCaregivers()
            val vm = CaregiverViewModel(fake)
            val item = Caregiver(9, "Demo caregiver", "+2540700000000")
            Harness(fake, vm, vm.operationState, item, item.copy(id = 0),
                { vm.addCaregiver(item) }, { vm.updateCaregiver(item) }, vm::deleteCaregiver,
                vm.caregivers, vm::observeCaregiver)
        }
        "child" -> {
            val fake = FakeChildren()
            val vm = ChildViewModel(fake, clock)
            val item = Child(9, 3, "Demo child", LocalDate.of(2024, 2, 29))
            Harness(fake, vm, vm.operationState, item, item.copy(id = 0),
                { vm.addChild(item) }, { vm.updateChild(item) }, vm::deleteChild,
                vm.observeChildren(3), vm::observeChild)
        }
        else -> {
            val fake = FakeAppointments()
            val vm = AppointmentViewModel(fake, clock)
            val item = Appointment(9, 3, null, "Demo visit", clock.instant(), null)
            Harness(fake, vm, vm.operationState, item, item.copy(id = 0),
                { vm.addAppointment(item) }, { vm.updateAppointment(item) }, vm::deleteAppointment,
                vm.observeAppointments(3), vm::observeAppointment)
        }
    }

    @Test fun `initial state is idle and repository observations stay live`() {
        fun <T> verify(h: Harness<T>) {
            assertEquals(RepositoryOperationState(), h.state.value)
            assertSame(h.fake.list, h.list)
            assertSame(h.fake.single, h.single(9))
            assertEquals(9L, h.fake.observedId)
            if (kind != "caregiver") assertEquals(3L, h.fake.observedCaregiver)
            val seen = mutableListOf<List<T>>()
            val observer = Observer<List<T>> { seen.add(it) }
            h.list.observeForever(observer)
            try {
                h.fake.list.value = listOf(h.sample)
                h.fake.list.value = emptyList()
                assertEquals(listOf(emptyList(), listOf(h.sample), emptyList()), seen)
                h.fake.single.value = h.sample
                assertEquals(h.sample, h.single(9).value)
                h.fake.single.value = null
                assertNull(h.single(9).value)
            } finally { h.list.removeObserver(observer) }
        }
        verify(harness())
    }

    @Test fun `insert sends zero ID preserves fields and exposes generated ID after loading`() = runTest {
        val h = harness()
        val states = mutableListOf<RepositoryOperationState>()
        val observer = Observer<RepositoryOperationState> { states.add(it) }
        h.state.observeForever(observer)
        try {
            h.fake.gate = CompletableDeferred()
            h.add()
            assertEquals(RepositoryOperationState(inProgress = true), h.state.value)
            assertEquals(h.newValue, h.fake.lastValue)
            h.fake.gate!!.complete(Unit)
            runCurrent()
            assertEquals(RepositoryOperationState(completedOperation = Operation.INSERT, affectedId = 71), h.state.value)
            assertEquals(listOf(false, true, false), states.map { it.inProgress })
        } finally { h.state.removeObserver(observer) }
    }

    @Test fun `update and delete preserve saved identifiers and report operation`() = runTest {
        val h = harness()
        h.update()
        assertEquals(h.sample, h.fake.lastValue)
        assertEquals(RepositoryOperationState(completedOperation = Operation.UPDATE, affectedId = 9), h.state.value)
        h.delete(9)
        assertEquals(9L, h.fake.lastDeletedId)
        assertEquals(RepositoryOperationState(completedOperation = Operation.DELETE, affectedId = 9), h.state.value)
    }

    @Test fun `missing update and deletion are errors rather than success`() = runTest {
        val h = harness()
        h.fake.rows = 0
        for (action in listOf(h.update, { h.delete(9) })) {
            action()
            val state = h.state.value!!
            assertFalse(state.inProgress)
            assertTrue(state.error.orEmpty().contains("not found"))
            assertNull(state.completedOperation)
            assertNull(state.affectedId)
        }
    }

    @Test fun `invalid delete identifiers never reach repository`() = runTest {
        val h = harness()
        for (id in listOf(0L, -1L)) {
            h.delete(id)
            assertNotNull(h.state.value?.error)
        }
        assertEquals(0, h.fake.calls)
    }

    @Test fun `repository failures are safe clear loading and allow retry`() = runTest {
        val h = harness()
        for (action in listOf(h.add, h.update, { h.delete(9) })) {
            for (failure in listOf(Exception(), IllegalStateException("SQL private diagnostic"))) {
                h.fake.failure = failure
                action()
                val state = h.state.value!!
                assertFalse(state.inProgress)
                assertTrue(state.error.orEmpty().startsWith("Unable to change"))
                assertFalse(state.error.orEmpty().contains("SQL"))
                assertNull(state.completedOperation)
                assertNull(state.affectedId)
            }
        }
        h.fake.failure = null
        h.add()
        assertNull(h.state.value?.error)
        assertEquals(Operation.INSERT, h.state.value?.completedOperation)
    }

    @Test fun `repository cancellation is propagated and clears loading`() = runTest {
        val h = harness()
        h.fake.failure = CancellationException("Cancelled repository work")
        h.add()
        assertTrue(h.fake.cancelled)
        assertEquals(RepositoryOperationState(), h.state.value)
        h.fake.failure = null
        h.add()
        assertEquals(Operation.INSERT, h.state.value?.completedOperation)
    }

    @Test fun `clearing ViewModel cancels suspended writes and does not publish success`() = runTest {
        val h = harness()
        val store = ViewModelStore()
        store.put("subject", h.vm)
        h.fake.gate = CompletableDeferred()
        h.add()
        assertTrue(h.state.value!!.inProgress)
        store.clear()
        runCurrent()
        assertTrue(h.fake.cancelled)
        assertEquals(RepositoryOperationState(), h.state.value)
        h.fake.gate!!.complete(Unit)
        runCurrent()
        assertNull(h.state.value?.completedOperation)
    }

    @Test fun `second write during loading cannot duplicate or race first write`() = runTest {
        val h = harness()
        h.fake.gate = CompletableDeferred()
        h.add()
        h.add()
        h.update()
        h.delete(9)
        assertEquals(1, h.fake.calls)
        h.fake.gate!!.complete(Unit)
        runCurrent()
        h.update()
        assertEquals(2, h.fake.calls)
        assertEquals(Operation.UPDATE, h.state.value?.completedOperation)
    }

    @Test fun `invalid generated IDs never publish success`() = runTest {
        val h = harness()
        for (id in listOf(0L, -1L)) {
            h.fake.generatedId = id
            h.add()
            assertNotNull(h.state.value?.error)
            assertNull(h.state.value?.affectedId)
            assertNull(h.state.value?.completedOperation)
        }
    }
}
