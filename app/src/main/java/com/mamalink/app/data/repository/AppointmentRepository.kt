package com.mamalink.app.data.repository

import androidx.lifecycle.LiveData
import com.mamalink.app.data.model.Appointment

interface AppointmentRepository {
    fun observeAllAppointments(): LiveData<List<Appointment>>
    fun observeAppointmentsByCaregiverId(caregiverId: Long): LiveData<List<Appointment>>
    fun observeAppointmentsByChildId(childId: Long): LiveData<List<Appointment>>
    fun observeAppointmentById(id: Long): LiveData<Appointment?>
    suspend fun getAppointmentById(id: Long): Appointment?
    suspend fun getAppointmentsByCaregiverId(caregiverId: Long): List<Appointment>
    suspend fun insertAppointment(appointment: Appointment): Long
    suspend fun updateAppointment(appointment: Appointment)
    suspend fun deleteAppointment(appointment: Appointment)
    suspend fun deleteAppointmentById(id: Long)
}
