package com.mamalink.app.data.local

import com.mamalink.app.data.RoomTestBase
import com.mamalink.app.data.awaitValue
import com.mamalink.app.data.local.entity.CaregiverEntity
import com.mamalink.app.data.local.entity.ChildEntity
import com.mamalink.app.data.local.entity.AppointmentEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppointmentDaoTest : RoomTestBase() {
    private suspend fun caregiver(name: String = "Demo") =
        database.caregiverDao().insertCaregiver(CaregiverEntity(fullName = name))
    private suspend fun child(owner: Long) =
        database.childDao().insertChild(ChildEntity(caregiverId = owner, name = "Child", dateOfBirth = LocalDate.of(2024, 1, 1)))
    private fun appointment(owner: Long, child: Long? = null) =
        AppointmentEntity(caregiverId = owner, childId = child, title = "Visit", scheduledAt = Instant.ofEpochMilli(1234567890123))

    @Test fun `appointment CRUD supports nullable child and location and row counts`() = runTest {
        val owner = caregiver()
        val dao = database.appointmentDao()
        val id = dao.insertAppointment(appointment(owner))
        val saved = dao.getAppointmentById(id)!!
        assertNull(saved.childId)
        assertNull(saved.location)
        assertEquals(appointment(owner).scheduledAt, saved.scheduledAt)
        assertEquals(1, dao.updateAppointment(saved.copy(title = "Updated")))
        assertEquals("Updated", dao.observeAppointmentById(id).awaitValue { it?.title == "Updated" }?.title)
        assertEquals(0, dao.updateAppointment(saved.copy(id = id + 100)))
        assertEquals(1, dao.deleteAppointmentById(id))
        assertEquals(0, dao.deleteAppointmentById(id))
    }

    @Test fun `insert rejects child from another caregiver and missing references`() = runTest {
        val first = caregiver("First")
        val second = caregiver("Second")
        val child = child(first)
        for (invalid in listOf(appointment(second, child), appointment(first, 999), appointment(999))) {
            try { database.appointmentDao().insertAppointment(invalid); fail("Invalid relationship") }
            catch (_: android.database.sqlite.SQLiteConstraintException) { }
        }
        assertTrue(database.appointmentDao().getAppointmentsByCaregiverId(first).isEmpty())
        assertTrue(database.appointmentDao().insertAppointment(appointment(first, child)) > 0)
    }

    @Test fun `updates cannot move appointments or linked children across caregivers`() = runTest {
        val first = caregiver("First")
        val second = caregiver("Second")
        val child = child(first)
        val otherChild = child(second)
        val dao = database.appointmentDao()
        val id = dao.insertAppointment(appointment(first, child))
        for (invalid in listOf(appointment(second, child), appointment(first, otherChild))) {
            try { dao.updateAppointment(invalid.copy(id = id)); fail("Cross-caregiver update") }
            catch (_: android.database.sqlite.SQLiteConstraintException) { }
        }
        val savedChild = database.childDao().getChildById(child)!!
        try { database.childDao().updateChild(savedChild.copy(caregiverId = second)); fail("Linked child reassignment") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(first, dao.getAppointmentById(id)?.caregiverId)
        assertEquals(first, database.childDao().getChildById(child)?.caregiverId)
    }

    @Test fun `linked child and caregiver deletions preserve appointments`() = runTest {
        val owner = caregiver()
        val child = child(owner)
        val id = database.appointmentDao().insertAppointment(appointment(owner, child))
        try { database.childDao().deleteChildById(child); fail("Linked child delete") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        try { database.caregiverDao().deleteCaregiverById(owner); fail("Linked caregiver delete") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertNotNull(database.appointmentDao().getAppointmentById(id))
        database.appointmentDao().deleteAppointmentById(id)
        assertEquals(1, database.childDao().deleteChildById(child))
        assertEquals(1, database.caregiverDao().deleteCaregiverById(owner))
    }

    @Test fun `caregiver-only appointments also restrict caregiver deletion`() = runTest {
        val owner = caregiver()
        database.appointmentDao().insertAppointment(appointment(owner))
        try { database.caregiverDao().deleteCaregiverById(owner); fail("Dependent appointment") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(1, database.appointmentDao().getAppointmentsByCaregiverId(owner).size)
    }

    @Test fun `appointments are chronological and isolated by caregiver`() = runTest {
        val first = caregiver("First")
        val second = caregiver("Second")
        val dao = database.appointmentDao()
        dao.insertAppointment(appointment(first).copy(scheduledAt = Instant.ofEpochMilli(3000)))
        dao.insertAppointment(appointment(second).copy(scheduledAt = Instant.ofEpochMilli(2000)))
        dao.insertAppointment(appointment(first).copy(scheduledAt = Instant.ofEpochMilli(1000)))
        assertEquals(listOf(1000L, 3000L), dao.getAppointmentsByCaregiverId(first).map { it.scheduledAt.toEpochMilli() })
    }
}
