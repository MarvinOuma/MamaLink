package com.mamalink.app.data.model

data class Caregiver(
    val id: Long = 0,
    val fullName: String,
    val phoneNumber: String,
    val email: String,
    val address: String,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
