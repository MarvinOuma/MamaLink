package com.mamalink.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mamalink.app.data.model.Appointment

@Entity(
    tableName = "appointments",
    foreignKeys = [
        ForeignKey(
            entity = CaregiverEntity::class,
            parentColumns = ["id"],
            childColumns = ["caregiver_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChildEntity::class,
            parentColumns = ["id"],
            childColumns = ["child_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["caregiver_id"]),
        Index(value = ["child_id"]),
    ],
)
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "caregiver_id") val caregiverId: Long,
    @ColumnInfo(name = "child_id") val childId: Long? = null,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "description") val description: String? = null,
    @ColumnInfo(name = "appointment_date_time") val appointmentDateTime: Long,
    @ColumnInfo(name = "location") val location: String,
    @ColumnInfo(name = "status") val status: String = "SCHEDULED",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

fun AppointmentEntity.toDomain(): Appointment = Appointment(
    id = id,
    caregiverId = caregiverId,
    childId = childId,
    title = title,
    description = description,
    appointmentDateTime = appointmentDateTime,
    location = location,
    status = status,
    createdAt = createdAt,
)

fun Appointment.toEntity(): AppointmentEntity = AppointmentEntity(
    id = id,
    caregiverId = caregiverId,
    childId = childId,
    title = title,
    description = description,
    appointmentDateTime = appointmentDateTime,
    location = location,
    status = status,
    createdAt = createdAt,
)
