package com.mamalink.app.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WorkspaceDao {
    @Query("SELECT label FROM workspace_metadata WHERE id = 1")
    fun observeLabel(): LiveData<String?>

    @Query("SELECT * FROM workspace_metadata WHERE id = 1")
    suspend fun getMetadata(): WorkspaceMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: WorkspaceMetadata)
}
