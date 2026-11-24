package com.mamalink.app.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamalink.app.data.local.dao.AppointmentDao
import com.mamalink.app.data.local.dao.CaregiverDao
import com.mamalink.app.data.local.dao.ChildDao
import com.mamalink.app.data.local.entity.AppointmentEntity
import com.mamalink.app.data.local.entity.CaregiverEntity
import com.mamalink.app.data.local.entity.ChildEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppointmentDaoTest {
    @get:Rule val instantExecutor = InstantTaskExecutorRule()
    private lateinit var database: MamaLinkDatabase
    private lateinit var caregiverDao: CaregiverDao
    private lateinit var childDao: ChildDao
    private lateinit var appointmentDao: AppointmentDao

    private var caregiverId: Long = 0
    private var childId: Long = 0

    @Before fun openDatabase() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), MamaLinkDatabase::class.java,
        ).allowMainThreadQueries().build()
        caregiverDao = database.caregiverDao()
        childDao = database.childDao()
        appointmentDao = database.appointmentDao()

        caregiverId = caregiverDao.insertCaregiver(
            CaregiverEntity(
                fullName = "Jane Doe",
                phoneNumber = "+15550199",
                email = "jane.doe@example.com",
                address = "123 Hope Street",
            ),
        )

        childId = childDao.insertChild(
            ChildEntity(
                caregiverId = caregiverId,
                fullName = "Baby Doe",
                dateOfBirth = "2024-06-01",
                gender = "Female",
            ),
        )
    }

    @After fun closeDatabase() { database.close() }

    @Test fun `insert and query appointment`() = runTest {
        val appointment = AppointmentEntity(
            caregiverId = caregiverId,
            childId = childId,
            title = "Postnatal Checkup",
            description = "6-month routine growth check",
            appointmentDateTime = System.currentTimeMillis() + 86400000,
            location = "Community Clinic A",
            status = "SCHEDULED",
        )

        val id = appointmentDao.insertAppointment(appointment)
        val fetched = appointmentDao.getAppointmentById(id)

        assertNotNull(fetched)
        assertEquals("Postnatal Checkup", fetched?.title)
        assertEquals("Community Clinic A", fetched?.location)
    }

    @Test fun `deleting child sets appointment childId to null`() = runTest {
        val appointmentId = appointmentDao.insertAppointment(
            AppointmentEntity(
                caregiverId = caregiverId,
                childId = childId,
                title = "Vaccination Visit",
                appointmentDateTime = System.currentTimeMillis() + 86400000,
                location = "Health Center B",
            ),
        )

        childDao.deleteChildById(childId)

        val appointment = appointmentDao.getAppointmentById(appointmentId)
        assertNotNull(appointment)
        assertNull(appointment?.childId)
    }

    @Test fun `deleting caregiver cascades to appointments`() = runTest {
        val appointmentId = appointmentDao.insertAppointment(
            AppointmentEntity(
                caregiverId = caregiverId,
                childId = childId,
                title = "Growth Monitoring",
                appointmentDateTime = System.currentTimeMillis() + 86400000,
                location = "Clinic C",
            ),
        )

        caregiverDao.deleteCaregiverById(caregiverId)

        assertNull(appointmentDao.getAppointmentById(appointmentId))
    }
}
