package com.mamalink.app.data.repository

import androidx.lifecycle.Observer
import com.mamalink.app.data.RoomTestBase
import com.mamalink.app.data.awaitValue
import com.mamalink.app.data.local.entity.CaregiverEntity
import com.mamalink.app.data.local.entity.ChildEntity
import com.mamalink.app.data.local.entity.toDomain
import com.mamalink.app.domain.model.Child
import com.mamalink.app.domain.repository.ChildRepository
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
class RoomChildRepositoryTest : RoomTestBase() {
    private val repository: ChildRepository get() = RoomChildRepository(database.childDao())
    private suspend fun sample(): Child {
        val owner = database.caregiverDao().insertCaregiver(CaregiverEntity(fullName = "Demo owner"))

        return Child(0, owner, "Demo child", LocalDate.of(2024, 2, 29))
    }

    @Test fun `domain contract CRUD maps values and affected row counts`() = runTest {
        val original = sample()
        val id = repository.insert(original)
        assertTrue(id > 0)
        val saved = original.copy(id = id)
        assertEquals(saved, repository.observeById(id).awaitValue { it != null })
        assertEquals(saved, database.childDao().getChildById(id)?.toDomain())
        val updated = saved.copy(name = "Updated demo")
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
        val delivered = LinkedBlockingQueue<List<Child>>()
        val observer = Observer<Child?> { delivered.offer(listOfNotNull(it)) }
        values.observeForever(observer)
        try {
            assertEquals(listOf(item.copy(id = id)), delivered.poll(10, TimeUnit.SECONDS))
            val updated = item.copy(id = id, name = "Observed update")
            repository.update(updated)
            assertEquals(listOf(updated), delivered.poll(10, TimeUnit.SECONDS))
            repository.deleteById(id)
            assertEquals(emptyList<Child>(), delivered.poll(10, TimeUnit.SECONDS))
        } finally {
            values.removeObserver(observer)
        }
    }

    @Test fun `inserts reject existing IDs and blank required labels`() = runTest {
        val item = sample()
        val id = repository.insert(item)
        try { repository.insert(item.copy(id = id)); fail("New record ID must be zero") }
        catch (_: IllegalArgumentException) { }
        try { repository.insert(item.copy(name = " ")); fail("Blank name or title") }
        catch (_: IllegalArgumentException) { }
        try { repository.update(item.copy(id = id, name = " ")); fail("Blank update") }
        catch (_: IllegalArgumentException) { }
        assertEquals(item.copy(id = id), repository.observeById(id).awaitValue { it != null })
    }
}
