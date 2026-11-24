package com.mamalink.app.data.model

data class Child(
    val id: Long = 0,
    val caregiverId: Long,
    val fullName: String,
    val dateOfBirth: String,
    val gender: String,
    val medicalNotes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
