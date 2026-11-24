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

class MamaLinkApplication : Application() {
    // Application-scoped dependencies; no Activity or Fragment is retained here.
    val database by lazy { MamaLinkDatabase.create(this) }
    val workspaceRepository by lazy { RoomWorkspaceRepository(database.workspaceDao()) }
    val caregiverRepository: CaregiverRepository by lazy { RoomCaregiverRepository(database.caregiverDao()) }
    val childRepository: ChildRepository by lazy { RoomChildRepository(database.childDao()) }
    val appointmentRepository: AppointmentRepository by lazy { RoomAppointmentRepository(database.appointmentDao()) }
}
