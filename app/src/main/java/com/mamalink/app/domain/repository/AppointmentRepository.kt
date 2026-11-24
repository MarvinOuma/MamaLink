package com.mamalink.app.domain.repository

import androidx.lifecycle.LiveData
import com.mamalink.app.domain.model.Appointment

interface AppointmentRepository {
    /** Appointments sorted chronologically, earliest first. */
    fun observeByCaregiver(caregiverId: Long): LiveData<List<Appointment>>
    fun observeById(id: Long): LiveData<Appointment?>
    /** Returns the generated ID. The caller passes id=0 for a new record. */
    suspend fun insert(appointment: Appointment): Long
    /** Returns rows affected; zero means the ID was not found. */
    suspend fun update(appointment: Appointment): Int
    suspend fun deleteById(id: Long): Int
}
