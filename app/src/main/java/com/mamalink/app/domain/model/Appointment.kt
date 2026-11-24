package com.mamalink.app.domain.model

import java.time.Instant

/** A planned event; childId may be null for caregiver-only appointments. */
data class Appointment(
    val id: Long,
    val caregiverId: Long,
    val childId: Long? = null,
    val title: String,
    val scheduledAt: Instant,
    val location: String? = null,
)
