package com.mamalink.app.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamalink.app.data.repository.RoomWorkspaceRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class MamaLinkDatabaseTest {
    @get:Rule val instantExecutor = InstantTaskExecutorRule()
    private lateinit var database: MamaLinkDatabase

    @Before fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), MamaLinkDatabase::class.java,
        ).build()
    }

    @After fun closeDatabase() { database.close() }

    @Test fun `database starts empty and replaces non-clinical metadata`() = runTest {
        val dao = database.workspaceDao()
        assertNull(dao.getMetadata())
        dao.upsert(WorkspaceMetadata(label = "Demo workspace"))
        assertEquals("Demo workspace", dao.getMetadata()?.label)
        dao.upsert(WorkspaceMetadata(label = "Updated workspace"))
        assertEquals(WorkspaceMetadata(label = "Updated workspace"), dao.getMetadata())
    }

    @Test fun `repository emits metadata stored through Room`() = runTest {
        val dao = database.workspaceDao()
        dao.upsert(WorkspaceMetadata(label = "Demo workspace"))
        val label = RoomWorkspaceRepository(dao).observeLabel()
        val delivered = CountDownLatch(1)
        val observer = Observer<String?> { if (it == "Demo workspace") delivered.countDown() }
        label.observeForever(observer)
        try {
            org.junit.Assert.assertTrue("Room label was not observed", delivered.await(5, TimeUnit.SECONDS))
        } finally {
            label.removeObserver(observer)
        }
    }
}
