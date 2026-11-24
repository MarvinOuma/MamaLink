package com.mamalink.app.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.mamalink.app.data.local.dao.AppointmentDao
import com.mamalink.app.data.local.entity.toDomain
import com.mamalink.app.data.local.entity.toEntity
import com.mamalink.app.domain.model.Appointment
import com.mamalink.app.domain.repository.AppointmentRepository

class RoomAppointmentRepository(private val dao: AppointmentDao) : AppointmentRepository {
    override fun observeByCaregiver(caregiverId: Long): LiveData<List<Appointment>> =
        dao.observeAppointmentsByCaregiverId(caregiverId).map { list -> list.map { it.toDomain() } }

    override fun observeById(id: Long): LiveData<Appointment?> =
        dao.observeAppointmentById(id).map { it?.toDomain() }

    override suspend fun insert(appointment: Appointment): Long {
        require(appointment.id == 0L) { "New records must have id=0" }
        require(appointment.title.isNotBlank()) { "A name or title is required" }
        return dao.insertAppointment(appointment.toEntity())
    }

    override suspend fun update(appointment: Appointment): Int {
        require(appointment.title.isNotBlank()) { "A name or title is required" }
        return dao.updateAppointment(appointment.toEntity())
    }

    override suspend fun deleteById(id: Long): Int = dao.deleteAppointmentById(id)
}
