package com.mamalink.app.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamalink.app.data.local.MamaLinkDatabase
import com.mamalink.app.data.model.Appointment
import com.mamalink.app.data.model.Caregiver
import com.mamalink.app.data.model.Child
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
class RoomAppointmentRepositoryTest {
    @get:Rule val instantExecutor = InstantTaskExecutorRule()
    private lateinit var database: MamaLinkDatabase
    private lateinit var caregiverRepository: CaregiverRepository
    private lateinit var childRepository: ChildRepository
    private lateinit var appointmentRepository: AppointmentRepository
    private var caregiverId: Long = 0
    private var childId: Long = 0

    @Before fun openDatabase() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), MamaLinkDatabase::class.java,
        ).allowMainThreadQueries().build()
        caregiverRepository = RoomCaregiverRepository(database.caregiverDao())
        childRepository = RoomChildRepository(database.childDao())
        appointmentRepository = RoomAppointmentRepository(database.appointmentDao())

        caregiverId = caregiverRepository.insertCaregiver(
            Caregiver(
                fullName = "Caregiver Test",
                phoneNumber = "+15551111",
                email = "test@example.com",
                address = "100 Main St",
            ),
        )
        childId = childRepository.insertChild(
            Child(
                caregiverId = caregiverId,
                fullName = "Child Test",
                dateOfBirth = "2024-01-01",
                gender = "Female",
            ),
        )
    }

    @After fun closeDatabase() { database.close() }

    @Test fun `insert and retrieve appointment`() = runTest {
        val appointment = Appointment(
            caregiverId = caregiverId,
            childId = childId,
            title = "Prenatal Visit",
            description = "Monthly routine check",
            appointmentDateTime = System.currentTimeMillis() + 100000,
            location = "General Hospital Room 202",
        )
        val id = appointmentRepository.insertAppointment(appointment)
        val fetched = appointmentRepository.getAppointmentById(id)

        assertNotNull(fetched)
        assertEquals("Prenatal Visit", fetched?.title)
        assertEquals("General Hospital Room 202", fetched?.location)
    }

    @Test fun `get appointments by caregiver id`() = runTest {
        appointmentRepository.insertAppointment(
            Appointment(
                caregiverId = caregiverId,
                childId = childId,
                title = "Appt 1",
                appointmentDateTime = 1000L,
                location = "Loc A",
            ),
        )
        appointmentRepository.insertAppointment(
            Appointment(
                caregiverId = caregiverId,
                childId = childId,
                title = "Appt 2",
                appointmentDateTime = 2000L,
                location = "Loc B",
            ),
        )

        val list = appointmentRepository.getAppointmentsByCaregiverId(caregiverId)
        assertEquals(2, list.size)
    }

    @Test fun `update and delete appointment`() = runTest {
        val id = appointmentRepository.insertAppointment(
            Appointment(
                caregiverId = caregiverId,
                childId = childId,
                title = "Initial Title",
                appointmentDateTime = 1000L,
                location = "Loc A",
            ),
        )

        val appt = appointmentRepository.getAppointmentById(id)!!
        appointmentRepository.updateAppointment(appt.copy(title = "Updated Title", status = "COMPLETED"))

        val updated = appointmentRepository.getAppointmentById(id)
        assertEquals("Updated Title", updated?.title)
        assertEquals("COMPLETED", updated?.status)

        appointmentRepository.deleteAppointmentById(id)
        assertNull(appointmentRepository.getAppointmentById(id))
    }
}
