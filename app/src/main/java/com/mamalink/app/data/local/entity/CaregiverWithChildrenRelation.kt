package com.mamalink.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation
import com.mamalink.app.data.model.CaregiverWithChildren

data class CaregiverWithChildrenRelation(
    @Embedded val caregiver: CaregiverEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "caregiver_id",
    )
    val children: List<ChildEntity>,
)

fun CaregiverWithChildrenRelation.toDomain(): CaregiverWithChildren = CaregiverWithChildren(
    caregiver = caregiver.toDomain(),
    children = children.map { it.toDomain() },
)
