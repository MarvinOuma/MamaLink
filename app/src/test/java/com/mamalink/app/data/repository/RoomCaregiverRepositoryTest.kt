package com.mamalink.app.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomCaregiverRepositoryTest {
    @get:Rule val instantExecutor = InstantTaskExecutorRule()
    private lateinit var database: MamaLinkDatabase
    private lateinit var repository: CaregiverRepository
    private lateinit var childRepository: ChildRepository

    @Before fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), MamaLinkDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomCaregiverRepository(database.caregiverDao())
        childRepository = RoomChildRepository(database.childDao())
    }

    @After fun closeDatabase() { database.close() }

    @Test fun `insert and get caregiver via repository`() = runTest {
        val caregiver = Caregiver(
            fullName = "Sarah Connor",
            phoneNumber = "+15550188",
            email = "sarah@example.com",
            address = "789 Skyline Drive",
        )
        val id = repository.insertCaregiver(caregiver)
        val fetched = repository.getCaregiverById(id)

        assertNotNull(fetched)
        assertEquals("Sarah Connor", fetched?.fullName)
    }

    @Test fun `observe all caregivers emits inserted values`() = runTest {
        repository.insertCaregiver(
            Caregiver(
                fullName = "Alice Smith",
                phoneNumber = "+15550100",
                email = "alice@example.com",
                address = "1 First St",
            ),
        )

        val liveData = repository.observeAllCaregivers()
        val latch = CountDownLatch(1)
        var resultList: List<Caregiver>? = null
        val observer = Observer<List<Caregiver>> { list ->
            resultList = list
            if (list.isNotEmpty()) latch.countDown()
        }
        liveData.observeForever(observer)
        try {
            assertTrue("LiveData did not deliver caregivers", latch.await(2, TimeUnit.SECONDS))
            assertEquals(1, resultList?.size)
            assertEquals("Alice Smith", resultList?.first()?.fullName)
        } finally {
            liveData.removeObserver(observer)
        }
    }

    @Test fun `get caregiver with children returns nested domain models`() = runTest {
        val cId = repository.insertCaregiver(
            Caregiver(
                fullName = "Mary Johnson",
                phoneNumber = "+15550200",
                email = "mary@example.com",
                address = "2 Second St",
            ),
        )
        childRepository.insertChild(
            Child(
                caregiverId = cId,
                fullName = "Child One",
                dateOfBirth = "2023-01-01",
                gender = "Female",
            ),
        )

        val caregiverWithChildren = repository.getCaregiverWithChildren(cId)
        assertNotNull(caregiverWithChildren)
        assertEquals("Mary Johnson", caregiverWithChildren?.caregiver?.fullName)
        assertEquals(1, caregiverWithChildren?.children?.size)
        assertEquals("Child One", caregiverWithChildren?.children?.first()?.fullName)
    }

    @Test fun `delete caregiver by id`() = runTest {
        val id = repository.insertCaregiver(
            Caregiver(
                fullName = "ToDelete",
                phoneNumber = "+15550000",
                email = "delete@example.com",
                address = "0 Nowhere St",
            ),
        )

        repository.deleteCaregiverById(id)
        assertNull(repository.getCaregiverById(id))
    }
}
