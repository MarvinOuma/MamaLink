package com.mamalink.app.notifications

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.lifecycle.MutableLiveData
import androidx.work.Operation
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkOperationTest {
    private class PendingOperation : Operation {
        lateinit var completion: CallbackToFutureAdapter.Completer<Operation.State.SUCCESS>
        private val future = CallbackToFutureAdapter.getFuture<Operation.State.SUCCESS> { completion = it; "pending operation" }
        override fun getResult() = future
        override fun getState() = MutableLiveData<Operation.State>(Operation.IN_PROGRESS)
    }

    @Test fun `await suspends until persistence succeeds`() = runBlocking {
        val operation = PendingOperation()
        val task = async(start = CoroutineStart.UNDISPATCHED) { operation.awaitPersistence() }
        assertFalse(task.isCompleted)
        operation.completion.set(Operation.SUCCESS)
        task.await()
        assertTrue(task.isCompleted)
    }

    @Test fun `asynchronous persistence failure reaches caller`() = runBlocking {
        val operation = PendingOperation()
        val task = async(start = CoroutineStart.UNDISPATCHED) {
            try { operation.awaitPersistence(); null } catch (failure: IllegalStateException) { failure.message }
        }
        assertFalse(task.isCompleted)
        operation.completion.setException(IllegalStateException("WorkManager database failed"))
        assertEquals("WorkManager database failed", task.await())
    }

    @Test fun `cancelling caller does not cancel submitted durable operation`() = runBlocking {
        val operation = PendingOperation()
        val task = launch(start = CoroutineStart.UNDISPATCHED) { operation.awaitPersistence() }
        task.cancelAndJoin()
        assertFalse(operation.result.isCancelled)
        assertTrue(operation.completion.set(Operation.SUCCESS))
    }
}
