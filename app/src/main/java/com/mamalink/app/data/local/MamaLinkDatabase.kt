package com.mamalink.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WorkspaceMetadata::class], version = 1, exportSchema = true)
abstract class MamaLinkDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao

    companion object {
        fun create(context: Context): MamaLinkDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                MamaLinkDatabase::class.java,
                "mamalink.db",
            ).build()
        // Add explicit migrations with future schema changes; never silently erase data.
    }
}
