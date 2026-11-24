package com.mamalink.app.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mamalink.app.data.local.entity.AppointmentEntity

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments ORDER BY appointment_date_time ASC")
    fun observeAllAppointments(): LiveData<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE caregiver_id = :caregiverId ORDER BY appointment_date_time ASC")
    fun observeAppointmentsByCaregiverId(caregiverId: Long): LiveData<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE child_id = :childId ORDER BY appointment_date_time ASC")
    fun observeAppointmentsByChildId(childId: Long): LiveData<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE id = :id")
    fun observeAppointmentById(id: Long): LiveData<AppointmentEntity?>

    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun getAppointmentById(id: Long): AppointmentEntity?

    @Query("SELECT * FROM appointments ORDER BY appointment_date_time ASC")
    suspend fun getAllAppointments(): List<AppointmentEntity>

    @Query("SELECT * FROM appointments WHERE caregiver_id = :caregiverId ORDER BY appointment_date_time ASC")
    suspend fun getAppointmentsByCaregiverId(caregiverId: Long): List<AppointmentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAppointment(appointment: AppointmentEntity): Long

    @Update
    suspend fun updateAppointment(appointment: AppointmentEntity): Int

    @Delete
    suspend fun deleteAppointment(appointment: AppointmentEntity): Int

    @Query("DELETE FROM appointments WHERE id = :id")
    suspend fun deleteAppointmentById(id: Long): Int
}
