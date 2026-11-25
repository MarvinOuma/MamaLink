package com.mamalink.app

import android.app.Application
import com.mamalink.app.data.local.MamaLinkDatabase
import com.mamalink.app.data.repository.RoomAppointmentRepository
import com.mamalink.app.data.repository.RoomCaregiverRepository
import com.mamalink.app.data.repository.RoomChildRepository
import com.mamalink.app.data.repository.RoomWorkspaceRepository
import com.mamalink.app.domain.repository.AppointmentRepository
import com.mamalink.app.domain.repository.CaregiverRepository
import com.mamalink.app.domain.repository.ChildRepository
import com.mamalink.app.notifications.AppointmentNotificationHelper
import com.mamalink.app.notifications.AppointmentReminderScheduler
import com.mamalink.app.notifications.WorkManagerAppointmentReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MamaLinkApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Application-scoped dependencies; no Activity or Fragment is retained here.
    val database by lazy { MamaLinkDatabase.create(this) }
    val workspaceRepository by lazy { RoomWorkspaceRepository(database.workspaceDao()) }
    val caregiverRepository: CaregiverRepository by lazy { RoomCaregiverRepository(database.caregiverDao()) }
    val childRepository: ChildRepository by lazy { RoomChildRepository(database.childDao()) }
    val appointmentRepository: AppointmentRepository by lazy { RoomAppointmentRepository(database.appointmentDao()) }
    val reminderScheduler: AppointmentReminderScheduler by lazy {
        WorkManagerAppointmentReminderScheduler(this)
    }

    override fun onCreate() {
        super.onCreate()
        AppointmentNotificationHelper.createNotificationChannel(this)
    }

    fun reconcileAppointmentReminders() {
        applicationScope.launch {
            runCatching {
                reminderScheduler.reconcileReminders(appointmentRepository.getAll())
            }
        }
    }
}
