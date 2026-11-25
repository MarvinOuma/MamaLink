package com.mamalink.app.notifications

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.testing.TestListenableWorkerBuilder
import com.mamalink.app.domain.model.Appointment
import com.mamalink.app.domain.repository.AppointmentRepository
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Review reproductions exercise real WorkManager and production delivery, not a fake scheduler. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Stage5WorkManagerReviewProbeTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val executions = AtomicInteger()
    private val now = Instant.parse("2026-11-25T12:00:00Z")
    private val appointment = Appointment(9, 1, null, "Visit", now.plusSeconds(12 * 3600))
    private val records = linkedMapOf<Long, Appointment>()
    private var failWrite = false
    private val repository = object : AppointmentRepository {
        override fun observeByCaregiver(caregiverId: Long) = MutableLiveData<List<Appointment>>()
        override fun observeById(id: Long) = MutableLiveData<Appointment?>()
        override suspend fun getById(id: Long) = records[id]
        override suspend fun getAll() = records.values.toList()
        override suspend fun insert(appointment: Appointment): Long = error("unused")
        override suspend fun update(appointment: Appointment): Int {
            check(!failWrite) { "Room write failed" }; records[appointment.id] = appointment; return 1
        }
        override suspend fun deleteById(id: Long): Int {
            check(!failWrite) { "Room write failed" }; return if (records.remove(id) != null) 1 else 0
        }
    }
    private fun scheduler(queue: ReminderWorkQueue = AndroidReminderWorkQueue(context)) =
        WorkManagerAppointmentReminderScheduler(context, Clock.fixed(now, ZoneOffset.UTC), repository, queue)
    private val manager get() = WorkManager.getInstance(context)
    private fun work() = manager.getWorkInfosForUniqueWork(WorkManagerAppointmentReminderScheduler.getWorkName(9)).get(10, TimeUnit.SECONDS)
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    @Before fun init() {
        context.getSharedPreferences(ReminderState.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor())
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, params: WorkerParameters): ListenableWorker =
                    object : CoroutineWorker(appContext, params) {
                        override suspend fun doWork(): Result {
                            if (workerClassName == AppointmentReminderWorker::class.java.name) {
                                executions.incrementAndGet()
                                scheduler().deliver(inputData.getLong(AppointmentReminderWorker.KEY_APPOINTMENT_ID, -1),
                                    inputData.getLong(AppointmentReminderWorker.KEY_SCHEDULED_AT, -1))
                            }
                            return Result.success()
                        }
                    }
            }).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        records[9] = appointment
    }
    @After fun close() = WorkManagerTestInitHelper.closeWorkDatabase()

    @Test fun `reconciliation does not replay a completed reminder`() = runBlocking {
        scheduler().scheduleReminder(appointment)
        withTimeout(10_000) { while (work().single().state != WorkInfo.State.SUCCEEDED) yield() }
        assertEquals(1, executions.get())
        assertEquals(1, notifications.allNotifications.size)
        assertTrue(ReminderState(context).wasDelivered(9, appointment.scheduledAt.toEpochMilli()))
        context.getSystemService(NotificationManager::class.java).cancelAll()
        repeat(3) { scheduler().reconcileReminders(listOf(appointment)) }
        assertEquals(1, executions.get())
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test fun `cancelling appointment withdraws an already posted notification`() = runBlocking {
        AppointmentNotificationHelper.showAppointmentNotification(context, 9, "Visit", "Tomorrow")
        assertEquals(1, notifications.allNotifications.size)
        val scheduler = scheduler()
        scheduler.persistChange { repository.deleteById(9) }
        scheduler.cancelReminder(9)
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test fun `stale reconciliation cannot cancel a newer saved appointment reminder`() = runBlocking {
        val oldSnapshot = appointment.copy(scheduledAt = now.plusSeconds(48 * 3600))
        val updated = appointment.copy(scheduledAt = now.plusSeconds(72 * 3600))
        records[9] = updated
        scheduler().scheduleReminder(updated)
        val updatedWork = work().single()
        scheduler().reconcileReminders(listOf(oldSnapshot))
        assertEquals(WorkInfo.State.ENQUEUED, manager.getWorkInfoById(updatedWork.id).get()!!.state)
        scheduler().scheduleReminder(oldSnapshot)
        assertEquals(updatedWork.id, work().single().id)
        assertEquals(updated.scheduledAt.toEpochMilli(), ReminderState(context).scheduledRevision(9))
    }

    @Test fun `failed writes leave both notification and pending work unchanged`() = runBlocking {
        val saved = appointment.copy(scheduledAt = now.plusSeconds(72 * 3600))
        records[9] = saved
        val scheduler = scheduler()
        scheduler.scheduleReminder(saved)
        val pending = work().single().id
        AppointmentNotificationHelper.showAppointmentNotification(context, 9, "Visit", "Later")
        failWrite = true
        try { scheduler.persistChange { repository.update(saved.copy(scheduledAt = now.plusSeconds(3600))) }; fail() }
        catch (_: IllegalStateException) { }
        try { scheduler.persistChange { repository.deleteById(9) }; fail() }
        catch (_: IllegalStateException) { }
        assertEquals(pending, work().single().id)
        assertEquals(1, notifications.allNotifications.size)
        scheduler.reconcileReminders(emptyList())
        assertEquals(pending, work().single().id)
        assertEquals(1, notifications.allNotifications.size)
    }

    @Test fun `real Room failure preserves reminders while successful edit and delete withdraw them`() = runBlocking {
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context,
            com.mamalink.app.data.local.MamaLinkDatabase::class.java).build()
        try {
            val owner = database.caregiverDao().insertCaregiver(
                com.mamalink.app.data.local.entity.CaregiverEntity(fullName = "Test owner"))
            val room = com.mamalink.app.data.repository.RoomAppointmentRepository(database.appointmentDao())
            val value = appointment.copy(id = 0, caregiverId = owner, scheduledAt = now.plusSeconds(72 * 3600))
            val id = room.insert(value)
            val saved = value.copy(id = id)
            val scheduler = WorkManagerAppointmentReminderScheduler(context, Clock.fixed(now, ZoneOffset.UTC), room)
            scheduler.scheduleReminder(saved)
            fun currentWork() = manager.getWorkInfosForUniqueWork(WorkManagerAppointmentReminderScheduler.getWorkName(id)).get()
            val pending = currentWork().single().id
            AppointmentNotificationHelper.showAppointmentNotification(context, id, "Visit", "Later")
            try {
                scheduler.persistChange { room.update(saved.copy(caregiverId = owner + 999, scheduledAt = now.plusSeconds(3600))) }
                fail("Foreign key write must fail")
            } catch (_: android.database.sqlite.SQLiteConstraintException) { }
            assertEquals(saved, room.getById(id))
            assertEquals(pending, currentWork().single().id)
            assertEquals(1, notifications.allNotifications.size)
            scheduler.reconcileReminders(emptyList())
            assertEquals(pending, currentWork().single().id)
            assertEquals(1, notifications.allNotifications.size)
            val updated = saved.copy(scheduledAt = now.plusSeconds(96 * 3600))
            scheduler.persistChange { room.update(updated) }
            scheduler.scheduleReminder(updated)
            assertTrue(notifications.allNotifications.isEmpty())
            AppointmentNotificationHelper.showAppointmentNotification(context, id, "Visit", "Later")
            scheduler.persistChange { room.deleteById(id) }
            scheduler.cancelReminder(id)
            assertTrue(notifications.allNotifications.isEmpty())
            assertTrue(currentWork().all { it.state == WorkInfo.State.CANCELLED })
        } finally { database.close() }
    }

    @Test fun `reschedule withdraws notification and old worker cannot post`() = runBlocking {
        val scheduler = scheduler()
        scheduler.deliver(9, appointment.scheduledAt.toEpochMilli())
        assertEquals(1, notifications.allNotifications.size)
        val updated = appointment.copy(scheduledAt = now.plusSeconds(72 * 3600))
        scheduler.persistChange { repository.update(updated) }
        scheduler.scheduleReminder(updated)
        assertTrue(notifications.allNotifications.isEmpty())
        scheduler.deliver(9, appointment.scheduledAt.toEpochMilli())
        assertTrue(notifications.allNotifications.isEmpty())
        assertEquals(WorkInfo.State.ENQUEUED, work().single().state)
    }

    @Test fun `failed write before first reconciliation does not withdraw existing notification`() = runBlocking {
        records[9] = appointment.copy(scheduledAt = now.plusSeconds(72 * 3600))
        AppointmentNotificationHelper.showAppointmentNotification(context, 9, "Existing visit", "Later")
        failWrite = true
        try { scheduler().persistChange { repository.update(appointment) }; fail() } catch (_: IllegalStateException) { }
        scheduler().reconcileReminders(emptyList())
        assertEquals(1, notifications.allNotifications.size)
    }

    @Test fun `reconciliation and mutation cannot interleave their persisted revisions`() = runBlocking {
        val old = appointment.copy(scheduledAt = now.plusSeconds(48 * 3600))
        val updated = appointment.copy(scheduledAt = now.plusSeconds(72 * 3600))
        records[9] = old
        val snapshotRead = CompletableDeferred<Unit>()
        val releaseSnapshot = CompletableDeferred<Unit>()
        val gatedRepository = object : AppointmentRepository by repository {
            override suspend fun getAll(): List<Appointment> {
                val snapshot = records.values.toList()
                snapshotRead.complete(Unit)
                releaseSnapshot.await()
                return snapshot
            }
        }
        val reconciling = WorkManagerAppointmentReminderScheduler(context, Clock.fixed(now, ZoneOffset.UTC), gatedRepository)
        val reader = async { reconciling.reconcileReminders(emptyList()) }
        snapshotRead.await()
        val writer = async(start = CoroutineStart.UNDISPATCHED) {
            scheduler().persistChange { repository.update(updated) }
            scheduler().scheduleReminder(updated)
        }
        assertFalse(writer.isCompleted)
        assertEquals(old, records[9])
        releaseSnapshot.complete(Unit)
        reader.await(); writer.await()
        assertEquals(updated.scheduledAt.toEpochMilli(), ReminderState(context).scheduledRevision(9))
        assertEquals(1, work().count { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test fun `returning to an already delivered revision does not alert again`() = runBlocking {
        scheduler().deliver(9, appointment.scheduledAt.toEpochMilli())
        val updated = appointment.copy(scheduledAt = now.plusSeconds(72 * 3600))
        scheduler().persistChange { repository.update(updated) }
        scheduler().scheduleReminder(updated)
        scheduler().persistChange { repository.update(appointment) }
        scheduler().scheduleReminder(appointment)
        scheduler().deliver(9, appointment.scheduledAt.toEpochMilli())
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test @Config(sdk = [35])
    fun `denied permission does not consume delivery and grant permits one reminder`() = runBlocking {
        shadowOf(context).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        scheduler().deliver(9, appointment.scheduledAt.toEpochMilli())
        assertFalse(ReminderState(context).wasDelivered(9, appointment.scheduledAt.toEpochMilli()))
        shadowOf(context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        scheduler().deliver(9, appointment.scheduledAt.toEpochMilli())
        assertTrue(ReminderState(context).wasDelivered(9, appointment.scheduledAt.toEpochMilli()))
        assertEquals(1, notifications.allNotifications.size)
    }

    @Test fun `enqueue failure retains durable intent and worker repairs without reopening app`() = runBlocking {
        val real = AndroidReminderWorkQueue(context)
        val failing = object : ReminderWorkQueue by real {
            override suspend fun schedule(appointment: Appointment, delayMillis: Long, replace: Boolean) = error("enqueue failed")
        }
        val updated = appointment.copy(scheduledAt = now.plusSeconds(72 * 3600))
        val scheduler = scheduler(failing)
        scheduler.persistChange { repository.update(updated) }
        try { scheduler.scheduleReminder(updated); fail() } catch (_: IllegalStateException) { }
        assertTrue(ReminderState(context).isDirty())
        assertTrue(manager.getWorkInfosForUniqueWork(AndroidReminderWorkQueue.RECOVERY_NAME).get().isNotEmpty())
        val worker = TestListenableWorkerBuilder<ReminderReconciliationWorker>(context).build()
        assertEquals(ListenableWorker.Result.success(), worker.reconcile(scheduler()))
        assertFalse(ReminderState(context).isDirty())
        assertEquals(WorkInfo.State.ENQUEUED, work().single().state)
        assertEquals(updated.scheduledAt.toEpochMilli(), ReminderState(context).scheduledRevision(9))
    }

    @Test fun `cancel failure remains tracked for durable recovery`() = runBlocking {
        records[9] = appointment.copy(scheduledAt = now.plusSeconds(72 * 3600))
        scheduler().scheduleReminder(records.getValue(9))
        val real = AndroidReminderWorkQueue(context)
        val failing = object : ReminderWorkQueue by real {
            override suspend fun cancel(id: Long) = error("cancel failed")
        }
        val scheduler = scheduler(failing)
        scheduler.persistChange { repository.deleteById(9) }
        try { scheduler.cancelReminder(9); fail() } catch (_: IllegalStateException) { }
        assertTrue(ReminderState(context).knownIds().contains(9L))
        scheduler().reconcileReminders(emptyList())
        assertTrue(work().all { it.state == WorkInfo.State.CANCELLED })
        assertFalse(ReminderState(context).knownIds().contains(9L))
    }

    @Test fun `unavailable recovery prevents committing an unprotected write`() = runBlocking {
        val real = AndroidReminderWorkQueue(context)
        val failing = object : ReminderWorkQueue by real {
            override suspend fun ensureRecovery() = error("recovery unavailable")
        }
        var wrote = false
        try { scheduler(failing).persistChange { wrote = true }; fail() } catch (_: IllegalStateException) { }
        assertFalse(wrote)
    }

    @Test fun `reconciliation worker returns retry on scheduling failure`() = runBlocking {
        val real = AndroidReminderWorkQueue(context)
        val failing = object : ReminderWorkQueue by real {
            override suspend fun schedule(appointment: Appointment, delayMillis: Long, replace: Boolean) = error("enqueue failed")
        }
        val worker = TestListenableWorkerBuilder<ReminderReconciliationWorker>(context).build()
        assertEquals(ListenableWorker.Result.retry(), worker.reconcile(scheduler(failing)))
        assertTrue(ReminderState(context).isDirty())
    }

    @Test fun `boot waits for durable work and propagates enqueue failure`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val real = AndroidReminderWorkQueue(context)
        val gated = object : ReminderWorkQueue by real {
            override suspend fun requestReconciliation() { entered.complete(Unit); gate.await(); real.requestReconciliation() }
        }
        val task = async { BootReceiver().enqueueRecovery(gated) }
        entered.await()
        assertFalse(task.isCompleted)
        gate.complete(Unit); task.await()
        assertTrue(manager.getWorkInfosForUniqueWork(AndroidReminderWorkQueue.RECOVERY_NAME).get().isNotEmpty())
        assertTrue(manager.getWorkInfosForUniqueWork(AndroidReminderWorkQueue.RECONCILE_NAME).get().isNotEmpty())
        val failing = object : ReminderWorkQueue by real {
            override suspend fun requestReconciliation() = error("persist failed")
        }
        try { BootReceiver().enqueueRecovery(failing); fail() } catch (_: IllegalStateException) { }
    }
}
