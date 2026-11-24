package com.mamalink.app.domain.model

import java.time.LocalDate

/** Child profile linked to exactly one caregiver. Not a Room entity. */
data class Child(
    val id: Long,
    val caregiverId: Long,
    val name: String,
    val dateOfBirth: LocalDate,
)
