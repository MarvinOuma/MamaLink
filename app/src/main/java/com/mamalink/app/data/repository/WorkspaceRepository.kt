package com.mamalink.app.data.repository

import androidx.lifecycle.LiveData

/** Boundary between presentation state and local persistence. */
interface WorkspaceRepository {
    fun observeLabel(): LiveData<String?>
}
