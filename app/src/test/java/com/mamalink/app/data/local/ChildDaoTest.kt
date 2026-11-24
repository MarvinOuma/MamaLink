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
class ChildDaoTest : RoomTestBase() {
    @Test fun `child CRUD preserves calendar date and reports missing rows`() = runTest {
        val caregiver = database.caregiverDao().insertCaregiver(CaregiverEntity(fullName = "Demo"))
        val dao = database.childDao()
        val date = LocalDate.of(2024, 2, 29)
        val id = dao.insertChild(ChildEntity(caregiverId = caregiver, name = "Demo child", dateOfBirth = date))
        val saved = dao.getChildById(id)!!
        assertEquals(date, saved.dateOfBirth)
        assertEquals(1, dao.updateChild(saved.copy(name = "Updated child")))
        assertEquals("Updated child", dao.observeChildById(id).awaitValue { it?.name == "Updated child" }?.name)
        assertEquals(0, dao.updateChild(saved.copy(id = id + 100)))
        assertEquals(1, dao.deleteChildById(id))
        assertEquals(0, dao.deleteChildById(id))
        assertNull(dao.getChildById(id))
    }

    @Test fun `child insert and update reject missing caregivers`() = runTest {
        val dao = database.childDao()
        val child = ChildEntity(caregiverId = 999, name = "Demo", dateOfBirth = LocalDate.of(2024, 1, 1))
        try { dao.insertChild(child); fail("Missing caregiver") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        val caregiver = database.caregiverDao().insertCaregiver(CaregiverEntity(fullName = "Demo"))
        val id = dao.insertChild(child.copy(caregiverId = caregiver))
        try { dao.updateChild(child.copy(id = id)); fail("Missing caregiver") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(caregiver, dao.getChildById(id)?.caregiverId)
    }

    @Test fun `duplicate child insert never removes linked appointments`() = runTest {
        val caregiver = database.caregiverDao().insertCaregiver(CaregiverEntity(fullName = "Demo"))
        val child = ChildEntity(caregiverId = caregiver, name = "Original", dateOfBirth = LocalDate.of(2024, 1, 1))
        val id = database.childDao().insertChild(child)
        val appointment = database.appointmentDao().insertAppointment(AppointmentEntity(caregiverId = caregiver, childId = id, title = "Visit", scheduledAt = Instant.EPOCH))
        try { database.childDao().insertChild(child.copy(id = id, name = "Duplicate")); fail("Duplicate ID") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals("Original", database.childDao().getChildById(id)?.name)
        assertEquals(id, database.appointmentDao().getAppointmentById(appointment)?.childId)
    }

    @Test fun `children queries isolate caregivers and sort by name`() = runTest {
        val first = database.caregiverDao().insertCaregiver(CaregiverEntity(fullName = "First"))
        val second = database.caregiverDao().insertCaregiver(CaregiverEntity(fullName = "Second"))
        for ((owner, name) in listOf(first to "Z", second to "Other", first to "A")) {
            database.childDao().insertChild(ChildEntity(caregiverId = owner, name = name, dateOfBirth = LocalDate.of(2024, 1, 1)))
        }
        assertEquals(listOf("A", "Z"), database.childDao().getChildrenByCaregiverId(first).map { it.name })
    }
}
