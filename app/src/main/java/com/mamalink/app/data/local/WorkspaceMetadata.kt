package com.mamalink.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Non-clinical metadata proving the Room foundation. No patient schema in Stage 1. */
@Entity(tableName = "workspace_metadata")
data class WorkspaceMetadata(
    @PrimaryKey val id: Int = 1,
    val label: String,
)
