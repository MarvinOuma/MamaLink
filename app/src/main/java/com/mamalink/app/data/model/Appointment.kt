package com.mamalink.app.data.model

data class Appointment(
    val id: Long = 0,
    val caregiverId: Long,
    val childId: Long? = null,
    val title: String,
    val description: String? = null,
    val appointmentDateTime: Long,
    val location: String,
    val status: String = "SCHEDULED",
    val createdAt: Long = System.currentTimeMillis(),
)
