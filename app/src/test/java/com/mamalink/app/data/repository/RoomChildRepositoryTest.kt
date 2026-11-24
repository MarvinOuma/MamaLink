package com.mamalink.app.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamalink.app.data.local.MamaLinkDatabase
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
class RoomChildRepositoryTest {
    @get:Rule val instantExecutor = InstantTaskExecutorRule()
    private lateinit var database: MamaLinkDatabase
    private lateinit var caregiverRepository: CaregiverRepository
    private lateinit var childRepository: ChildRepository
    private var caregiverId: Long = 0

    @Before fun openDatabase() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), MamaLinkDatabase::class.java,
        ).allowMainThreadQueries().build()
        caregiverRepository = RoomCaregiverRepository(database.caregiverDao())
        childRepository = RoomChildRepository(database.childDao())

        caregiverId = caregiverRepository.insertCaregiver(
            Caregiver(
                fullName = "Caregiver Test",
                phoneNumber = "+15551111",
                email = "test@example.com",
                address = "100 Main St",
            ),
        )
    }

    @After fun closeDatabase() { database.close() }

    @Test fun `insert and get child via repository`() = runTest {
        val child = Child(
            caregiverId = caregiverId,
            fullName = "Tommy Test",
            dateOfBirth = "2023-11-20",
            gender = "Male",
        )
        val id = childRepository.insertChild(child)
        val fetched = childRepository.getChildById(id)

        assertNotNull(fetched)
        assertEquals("Tommy Test", fetched?.fullName)
    }

    @Test fun `get children by caregiver id returns correct list`() = runTest {
        childRepository.insertChild(
            Child(
                caregiverId = caregiverId,
                fullName = "Child A",
                dateOfBirth = "2022-01-01",
                gender = "Female",
            ),
        )
        childRepository.insertChild(
            Child(
                caregiverId = caregiverId,
                fullName = "Child B",
                dateOfBirth = "2023-05-05",
                gender = "Male",
            ),
        )

        val children = childRepository.getChildrenByCaregiverId(caregiverId)
        assertEquals(2, children.size)
    }

    @Test fun `update and delete child via repository`() = runTest {
        val id = childRepository.insertChild(
            Child(
                caregiverId = caregiverId,
                fullName = "Original Name",
                dateOfBirth = "2024-01-01",
                gender = "Female",
            ),
        )

        val child = childRepository.getChildById(id)!!
        childRepository.updateChild(child.copy(fullName = "Updated Name"))

        val updated = childRepository.getChildById(id)
        assertEquals("Updated Name", updated?.fullName)

        childRepository.deleteChildById(id)
        assertNull(childRepository.getChildById(id))
    }
}
