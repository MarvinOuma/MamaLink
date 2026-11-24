package com.mamalink.app.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamalink.app.data.local.dao.CaregiverDao
import com.mamalink.app.data.local.dao.ChildDao
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
class ChildDaoTest {
    @get:Rule val instantExecutor = InstantTaskExecutorRule()
    private lateinit var database: MamaLinkDatabase
    private lateinit var caregiverDao: CaregiverDao
    private lateinit var childDao: ChildDao
    private var caregiverId: Long = 0

    @Before fun openDatabase() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), MamaLinkDatabase::class.java,
        ).allowMainThreadQueries().build()
        caregiverDao = database.caregiverDao()
        childDao = database.childDao()

        caregiverId = caregiverDao.insertCaregiver(
            CaregiverEntity(
                fullName = "Jane Doe",
                phoneNumber = "+15550199",
                email = "jane.doe@example.com",
                address = "123 Hope Street",
            ),
        )
    }

    @After fun closeDatabase() { database.close() }

    @Test fun `insert and retrieve child record`() = runTest {
        val child = ChildEntity(
            caregiverId = caregiverId,
            fullName = "Baby Doe",
            dateOfBirth = "2024-06-01",
            gender = "Female",
            medicalNotes = "Routine immunizations up to date",
        )
        val id = childDao.insertChild(child)
        val fetched = childDao.getChildById(id)

        assertNotNull(fetched)
        assertEquals("Baby Doe", fetched?.fullName)
        assertEquals(caregiverId, fetched?.caregiverId)
    }

    @Test fun `get children by caregiver id`() = runTest {
        childDao.insertChild(
            ChildEntity(
                caregiverId = caregiverId,
                fullName = "Child A",
                dateOfBirth = "2021-03-01",
                gender = "Male",
            ),
        )
        childDao.insertChild(
            ChildEntity(
                caregiverId = caregiverId,
                fullName = "Child B",
                dateOfBirth = "2023-08-12",
                gender = "Female",
            ),
        )

        val children = childDao.getChildrenByCaregiverId(caregiverId)
        assertEquals(2, children.size)
    }

    @Test fun `update child details`() = runTest {
        val id = childDao.insertChild(
            ChildEntity(
                caregiverId = caregiverId,
                fullName = "Baby Doe",
                dateOfBirth = "2024-06-01",
                gender = "Female",
            ),
        )

        val child = childDao.getChildById(id)!!
        childDao.updateChild(child.copy(medicalNotes = "Allergy noted: peanuts"))

        val updated = childDao.getChildById(id)
        assertEquals("Allergy noted: peanuts", updated?.medicalNotes)
    }

    @Test fun `delete child record`() = runTest {
        val id = childDao.insertChild(
            ChildEntity(
                caregiverId = caregiverId,
                fullName = "Baby Doe",
                dateOfBirth = "2024-06-01",
                gender = "Female",
            ),
        )

        childDao.deleteChildById(id)
        assertNull(childDao.getChildById(id))
    }
}
