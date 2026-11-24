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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CaregiverDaoTest {
    @get:Rule val instantExecutor = InstantTaskExecutorRule()
    private lateinit var database: MamaLinkDatabase
    private lateinit var caregiverDao: CaregiverDao
    private lateinit var childDao: ChildDao

    @Before fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), MamaLinkDatabase::class.java,
        ).allowMainThreadQueries().build()
        caregiverDao = database.caregiverDao()
        childDao = database.childDao()
    }

    @After fun closeDatabase() { database.close() }

    @Test fun `insert and retrieve caregiver`() = runTest {
        val caregiver = CaregiverEntity(
            fullName = "Jane Doe",
            phoneNumber = "+15550199",
            email = "jane.doe@example.com",
            address = "123 Hope Street",
            notes = "Fictional caregiver test record",
        )
        val id = caregiverDao.insertCaregiver(caregiver)
        val fetched = caregiverDao.getCaregiverById(id)

        assertNotNull(fetched)
        assertEquals("Jane Doe", fetched?.fullName)
        assertEquals("+15550199", fetched?.phoneNumber)
    }

    @Test fun `update caregiver`() = runTest {
        val caregiver = CaregiverEntity(
            fullName = "Jane Doe",
            phoneNumber = "+15550199",
            email = "jane.doe@example.com",
            address = "123 Hope Street",
        )
        val id = caregiverDao.insertCaregiver(caregiver)
        val existing = caregiverDao.getCaregiverById(id)!!

        val updated = existing.copy(address = "456 Peace Lane")
        caregiverDao.updateCaregiver(updated)

        val refetched = caregiverDao.getCaregiverById(id)
        assertEquals("456 Peace Lane", refetched?.address)
    }

    @Test fun `delete caregiver cascades to children`() = runTest {
        val caregiverId = caregiverDao.insertCaregiver(
            CaregiverEntity(
                fullName = "Jane Doe",
                phoneNumber = "+15550199",
                email = "jane.doe@example.com",
                address = "123 Hope Street",
            ),
        )

        val childId = childDao.insertChild(
            ChildEntity(
                caregiverId = caregiverId,
                fullName = "Baby Doe",
                dateOfBirth = "2024-01-15",
                gender = "Female",
            ),
        )

        assertNotNull(childDao.getChildById(childId))

        caregiverDao.deleteCaregiverById(caregiverId)

        assertNull(caregiverDao.getCaregiverById(caregiverId))
        assertNull(childDao.getChildById(childId))
    }

    @Test fun `get caregiver with children relation`() = runTest {
        val caregiverId = caregiverDao.insertCaregiver(
            CaregiverEntity(
                fullName = "Jane Doe",
                phoneNumber = "+15550199",
                email = "jane.doe@example.com",
                address = "123 Hope Street",
            ),
        )

        childDao.insertChild(
            ChildEntity(
                caregiverId = caregiverId,
                fullName = "Child One",
                dateOfBirth = "2022-05-10",
                gender = "Male",
            ),
        )

        childDao.insertChild(
            ChildEntity(
                caregiverId = caregiverId,
                fullName = "Child Two",
                dateOfBirth = "2024-01-15",
                gender = "Female",
            ),
        )

        val relation = caregiverDao.getCaregiverWithChildren(caregiverId)
        assertNotNull(relation)
        assertEquals("Jane Doe", relation?.caregiver?.fullName)
        assertEquals(2, relation?.children?.size)
    }
}
