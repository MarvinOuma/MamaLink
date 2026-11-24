package com.mamalink.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class CaregiverWithChildrenRelation(
    @Embedded val caregiver: CaregiverEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "caregiver_id",
    )
    val children: List<ChildEntity>,
)
