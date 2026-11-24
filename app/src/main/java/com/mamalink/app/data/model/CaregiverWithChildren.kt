package com.mamalink.app.data.model

data class CaregiverWithChildren(
    val caregiver: Caregiver,
    val children: List<Child>,
)
