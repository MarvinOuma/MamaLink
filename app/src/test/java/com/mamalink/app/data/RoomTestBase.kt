package com.mamalink.app.data

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamalink.app.data.local.MamaLinkDatabase
import org.junit.After
import org.junit.Before
import org.junit.Rule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class RoomTestBase {
    @get:Rule val instantExecutor = InstantTaskExecutorRule()
    protected lateinit var database: MamaLinkDatabase

    @Before fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), MamaLinkDatabase::class.java,
        ).build()
    }

    @After fun closeDatabase() {
        if (::database.isInitialized) database.close()
    }
}

fun <T> LiveData<T>.awaitValue(predicate: (T) -> Boolean = { true }): T {
    val latch = CountDownLatch(1)
    var result: T? = null
    val observer = Observer<T> { value ->
        if (predicate(value)) {
            result = value
            latch.countDown()
        }
    }
    observeForever(observer)
    try {
        check(latch.await(10, TimeUnit.SECONDS)) { "Expected Room LiveData value was not observed" }
        @Suppress("UNCHECKED_CAST")
        return result as T
    } finally {
        removeObserver(observer)
    }
}
