package com.mamalink.app.data.repository

import androidx.lifecycle.Observer
import com.mamalink.app.data.RoomTestBase
import com.mamalink.app.data.awaitValue
import com.mamalink.app.data.local.entity.CaregiverEntity
import com.mamalink.app.data.local.entity.ChildEntity
import com.mamalink.app.data.local.entity.toDomain
import com.mamalink.app.domain.model.Caregiver
import com.mamalink.app.domain.repository.CaregiverRepository
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
class RoomCaregiverRepositoryTest : RoomTestBase() {
    private val repository: CaregiverRepository get() = RoomCaregiverRepository(database.caregiverDao())
    private suspend fun sample(): Caregiver {


        return Caregiver(0, "Demo caregiver")
    }

    @Test fun `domain contract CRUD maps values and affected row counts`() = runTest {
        val original = sample()
        val id = repository.insert(original)
        assertTrue(id > 0)
        val saved = original.copy(id = id)
        assertEquals(saved, repository.observeById(id).awaitValue { it != null })
        assertEquals(saved, database.caregiverDao().getCaregiverById(id)?.toDomain())
        val updated = saved.copy(fullName = "Updated demo")
        assertEquals(1, repository.update(updated))
        assertEquals(updated, repository.observeById(id).awaitValue { it == updated })
        assertEquals(0, repository.update(updated.copy(id = id + 1000)))
        assertEquals(1, repository.deleteById(id))
        assertEquals(0, repository.deleteById(id))
        assertNull(repository.observeById(id).awaitValue())
    }

    @Test fun `domain list contract observes only the requested records`() = runTest {
        val item = sample()

        val values = repository.observeAll()
        assertTrue(values.awaitValue().isEmpty())
        val id = repository.insert(item)
        assertEquals(listOf(item.copy(id = id)), values.awaitValue { it.size == 1 })
    }

    @Test fun `active observer receives insert update and deletion`() = runTest {
        val item = sample()
        val id = repository.insert(item)
        val values = repository.observeById(id)
        val delivered = LinkedBlockingQueue<List<Caregiver>>()
        val observer = Observer<Caregiver?> { delivered.offer(listOfNotNull(it)) }
        values.observeForever(observer)
        try {
            assertEquals(listOf(item.copy(id = id)), delivered.poll(10, TimeUnit.SECONDS))
            val updated = item.copy(id = id, fullName = "Observed update")
            repository.update(updated)
            assertEquals(listOf(updated), delivered.poll(10, TimeUnit.SECONDS))
            repository.deleteById(id)
            assertEquals(emptyList<Caregiver>(), delivered.poll(10, TimeUnit.SECONDS))
        } finally {
            values.removeObserver(observer)
        }
    }

    @Test fun `inserts reject existing IDs and blank required labels`() = runTest {
        val item = sample()
        val id = repository.insert(item)
        try { repository.insert(item.copy(id = id)); fail("New record ID must be zero") }
        catch (_: IllegalArgumentException) { }
        try { repository.insert(item.copy(fullName = " ")); fail("Blank name or title") }
        catch (_: IllegalArgumentException) { }
        try { repository.update(item.copy(id = id, fullName = " ")); fail("Blank update") }
        catch (_: IllegalArgumentException) { }
        assertEquals(item.copy(id = id), repository.observeById(id).awaitValue { it != null })
    }
}
