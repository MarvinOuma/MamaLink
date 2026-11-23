package com.mamalink.app.data.repository

import androidx.lifecycle.LiveData
import com.mamalink.app.data.local.WorkspaceDao

class RoomWorkspaceRepository(private val dao: WorkspaceDao) : WorkspaceRepository {
    override fun observeLabel(): LiveData<String?> = dao.observeLabel()
}
