package com.mamalink.app.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.mamalink.app.data.local.dao.AppointmentDao
import com.mamalink.app.data.local.entity.toDomain
import com.mamalink.app.data.local.entity.toEntity
import com.mamalink.app.data.model.Appointment

class RoomAppointmentRepository(private val dao: AppointmentDao) : AppointmentRepository {
    override fun observeAllAppointments(): LiveData<List<Appointment>> =
        dao.observeAllAppointments().map { list -> list.map { it.toDomain() } }

    override fun observeAppointmentsByCaregiverId(caregiverId: Long): LiveData<List<Appointment>> =
        dao.observeAppointmentsByCaregiverId(caregiverId).map { list -> list.map { it.toDomain() } }

    override fun observeAppointmentsByChildId(childId: Long): LiveData<List<Appointment>> =
        dao.observeAppointmentsByChildId(childId).map { list -> list.map { it.toDomain() } }

    override fun observeAppointmentById(id: Long): LiveData<Appointment?> =
        dao.observeAppointmentById(id).map { it?.toDomain() }

    override suspend fun getAppointmentById(id: Long): Appointment? =
        dao.getAppointmentById(id)?.toDomain()

    override suspend fun getAppointmentsByCaregiverId(caregiverId: Long): List<Appointment> =
        dao.getAppointmentsByCaregiverId(caregiverId).map { it.toDomain() }

    override suspend fun insertAppointment(appointment: Appointment): Long =
        dao.insertAppointment(appointment.toEntity())

    override suspend fun updateAppointment(appointment: Appointment) =
        dao.updateAppointment(appointment.toEntity())

    override suspend fun deleteAppointment(appointment: Appointment) =
        dao.deleteAppointment(appointment.toEntity())

    override suspend fun deleteAppointmentById(id: Long) =
        dao.deleteAppointmentById(id)
}
