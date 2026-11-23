package com.mamalink.app

import android.app.Application
import com.mamalink.app.data.local.MamaLinkDatabase
import com.mamalink.app.data.repository.RoomWorkspaceRepository

class MamaLinkApplication : Application() {
    // Application-scoped dependencies; no Activity or Fragment is retained here.
    private val database by lazy { MamaLinkDatabase.create(this) }
    val workspaceRepository by lazy { RoomWorkspaceRepository(database.workspaceDao()) }
}
