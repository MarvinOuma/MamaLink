package com.mamalink.app.domain.model

/** One caregiver's basic profile. This domain model is not a Room entity. */
data class Caregiver(
    val id: Long,
    val fullName: String,
    val phoneNumber: String? = null,
)
