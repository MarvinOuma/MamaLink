package com.mamalink.app.data.repository

import androidx.lifecycle.Observer
import com.mamalink.app.data.RoomTestBase
import com.mamalink.app.data.awaitValue
import com.mamalink.app.data.local.entity.CaregiverEntity
import com.mamalink.app.data.local.entity.ChildEntity
import com.mamalink.app.data.local.entity.toDomain
import com.mamalink.app.domain.model.Appointment
import com.mamalink.app.domain.repository.AppointmentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.Instant
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomAppointmentRepositoryTest : RoomTestBase() {
    private val repository: AppointmentRepository get() = RoomAppointmentRepository(database.appointmentDao())
    private suspend fun sample(): Appointment {
        val owner = database.caregiverDao().insertCaregiver(CaregiverEntity(fullName = "Demo owner"))
        val childId = database.childDao().insertChild(ChildEntity(caregiverId = owner, name = "Demo child", dateOfBirth = LocalDate.of(2024, 2, 29)))
        return Appointment(0, owner, childId, "Demo visit", Instant.ofEpochMilli(1234567890123))
    }

    @Test fun `domain contract CRUD maps values and affected row counts`() = runTest {
        val original = sample()
        val id = repository.insert(original)
        assertTrue(id > 0)
        val saved = original.copy(id = id)
        assertEquals(saved, repository.observeById(id).awaitValue { it != null })
        assertEquals(saved, database.appointmentDao().getAppointmentById(id)?.toDomain())
        val updated = saved.copy(title = "Updated demo")
        assertEquals(1, repository.update(updated))
        assertEquals(updated, repository.observeById(id).awaitValue { it == updated })
        assertEquals(0, repository.update(updated.copy(id = id + 1000)))
        assertEquals(1, repository.deleteById(id))
        assertEquals(0, repository.deleteById(id))
        assertNull(repository.observeById(id).awaitValue())
    }

    @Test fun `domain list contract observes only the requested records`() = runTest {
        val item = sample()
        val owner = item.caregiverId
        val values = repository.observeByCaregiver(owner)
        assertTrue(values.awaitValue().isEmpty())
        val id = repository.insert(item)
        assertEquals(listOf(item.copy(id = id)), values.awaitValue { it.size == 1 })
    }

    @Test fun `active observer receives insert update and deletion`() = runTest {
        val item = sample()
        val id = repository.insert(item)
        val values = repository.observeById(id)
        val delivered = LinkedBlockingQueue<List<Appointment>>()
        val observer = Observer<Appointment?> { delivered.offer(listOfNotNull(it)) }
        values.observeForever(observer)
        try {
            assertEquals(listOf(item.copy(id = id)), delivered.poll(10, TimeUnit.SECONDS))
            val updated = item.copy(id = id, title = "Observed update")
            repository.update(updated)
            assertEquals(listOf(updated), delivered.poll(10, TimeUnit.SECONDS))
            repository.deleteById(id)
            assertEquals(emptyList<Appointment>(), delivered.poll(10, TimeUnit.SECONDS))
        } finally {
            values.removeObserver(observer)
        }
    }

    @Test fun `inserts reject existing IDs and blank required labels`() = runTest {
        val item = sample()
        val id = repository.insert(item)
        try { repository.insert(item.copy(id = id)); fail("New record ID must be zero") }
        catch (_: IllegalArgumentException) { }
        try { repository.insert(item.copy(title = " ")); fail("Blank name or title") }
        catch (_: IllegalArgumentException) { }
        try { repository.update(item.copy(id = id, title = " ")); fail("Blank update") }
        catch (_: IllegalArgumentException) { }
        assertEquals(item.copy(id = id), repository.observeById(id).awaitValue { it != null })
    }
    @Test fun `repository rejects foreign caregiver child links on insert and update`() = runTest {
        val sample = sample()
        val otherOwner = database.caregiverDao().insertCaregiver(CaregiverEntity(fullName = "Other"))
        try { repository.insert(sample.copy(caregiverId = otherOwner)); fail("Foreign child") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        val id = repository.insert(sample)
        try { repository.update(sample.copy(id = id, caregiverId = otherOwner)); fail("Foreign child update") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(sample.copy(id = id), repository.observeById(id).awaitValue { it != null })
    }

    @Test fun `nullable fields and chronological caregiver filtering use the domain API`() = runTest {
        val sample = sample().copy(childId = null, location = null)
        val lateId = repository.insert(sample.copy(scheduledAt = Instant.ofEpochMilli(3000)))
        val earlyId = repository.insert(sample.copy(scheduledAt = Instant.ofEpochMilli(1000)))
        val other = database.caregiverDao().insertCaregiver(CaregiverEntity(fullName = "Other"))
        repository.insert(sample.copy(caregiverId = other))
        val saved = repository.observeByCaregiver(sample.caregiverId).awaitValue { it.size == 2 }
        assertEquals(listOf(earlyId, lateId), saved.map { it.id })
        assertTrue(saved.all { it.childId == null && it.location == null })
    }

}
