package com.mamalink.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mamalink.app.domain.model.Appointment
import java.time.Instant

@Entity(
    tableName = "appointments",
    foreignKeys = [
        ForeignKey(
            entity = CaregiverEntity::class,
            parentColumns = ["id"],
            childColumns = ["caregiver_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
        // A child must belong to the appointment's caregiver, even for direct DAO writes.
        ForeignKey(
            entity = ChildEntity::class,
            parentColumns = ["id", "caregiver_id"],
            childColumns = ["child_id", "caregiver_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["caregiver_id"]),
        Index(value = ["child_id", "caregiver_id"]),
    ],
)
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "caregiver_id") val caregiverId: Long,
    @ColumnInfo(name = "child_id") val childId: Long? = null,
    val title: String,
    @ColumnInfo(name = "appointment_date_time") val scheduledAt: Instant,
    val location: String? = null,
)

fun AppointmentEntity.toDomain(): Appointment =
    Appointment(id, caregiverId, childId, title, scheduledAt, location)
fun Appointment.toEntity(): AppointmentEntity =
    AppointmentEntity(id, caregiverId, childId, title, scheduledAt, location)
