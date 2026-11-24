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
class CaregiverDaoTest : RoomTestBase() {
    @Test fun `insert update observe and delete caregiver with row counts`() = runTest {
        val dao = database.caregiverDao()
        val id = dao.insertCaregiver(CaregiverEntity(fullName = "Demo caregiver"))
        val saved = dao.getCaregiverById(id)!!
        assertTrue(id > 0)
        assertNull(saved.phoneNumber)
        assertEquals(1, dao.updateCaregiver(saved.copy(fullName = "Updated", phoneNumber = "+00123")))
        assertEquals("+00123", dao.observeCaregiverById(id).awaitValue { it?.fullName == "Updated" }?.phoneNumber)
        assertEquals(0, dao.updateCaregiver(saved.copy(id = id + 100)))
        assertEquals(1, dao.deleteCaregiverById(id))
        assertEquals(0, dao.deleteCaregiverById(id))
        assertNull(dao.getCaregiverById(id))
    }

    @Test fun `duplicate caregiver insert aborts and keeps children and appointments`() = runTest {
        val dao = database.caregiverDao()
        val id = dao.insertCaregiver(CaregiverEntity(fullName = "Original"))
        val child = database.childDao().insertChild(ChildEntity(caregiverId = id, name = "Demo child", dateOfBirth = LocalDate.of(2024, 2, 29)))
        val appointment = database.appointmentDao().insertAppointment(AppointmentEntity(caregiverId = id, childId = child, title = "Demo visit", scheduledAt = Instant.EPOCH))
        try {
            dao.insertCaregiver(CaregiverEntity(id, "Duplicate"))
            fail("Duplicate primary key must be rejected")
        } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals("Original", dao.getCaregiverById(id)?.fullName)
        assertNotNull(database.childDao().getChildById(child))
        assertNotNull(database.appointmentDao().getAppointmentById(appointment))
        assertEquals(1, dao.updateCaregiver(CaregiverEntity(id, "Renamed")))
        assertNotNull(database.childDao().getChildById(child))
    }

    @Test fun `caregiver deletion is restricted until dependents are explicitly deleted`() = runTest {
        val id = database.caregiverDao().insertCaregiver(CaregiverEntity(fullName = "Demo"))
        val child = database.childDao().insertChild(ChildEntity(caregiverId = id, name = "Child", dateOfBirth = LocalDate.of(2024, 1, 1)))
        try {
            database.caregiverDao().deleteCaregiverById(id)
            fail("Caregiver with a child must be retained")
        } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertNotNull(database.caregiverDao().getCaregiverById(id))
        assertNotNull(database.childDao().getChildById(child))
        database.childDao().deleteChildById(child)
        assertEquals(1, database.caregiverDao().deleteCaregiverById(id))
    }

    @Test fun `caregiver with children relation is loaded transactionally`() = runTest {
        val id = database.caregiverDao().insertCaregiver(CaregiverEntity(fullName = "Demo"))
        repeat(2) { database.childDao().insertChild(ChildEntity(caregiverId = id, name = "Child $it", dateOfBirth = LocalDate.of(2024, 1, 1))) }
        val relation = database.caregiverDao().getCaregiverWithChildren(id)!!
        assertEquals(id, relation.caregiver.id)
        assertEquals(2, relation.children.size)
        assertTrue(relation.children.all { it.caregiverId == id })
    }
}
