package com.mamalink.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mamalink.app.data.model.Child

@Entity(
    tableName = "children",
    foreignKeys = [
        ForeignKey(
            entity = CaregiverEntity::class,
            parentColumns = ["id"],
            childColumns = ["caregiver_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["caregiver_id"]),
    ],
)
data class ChildEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "caregiver_id") val caregiverId: Long,
    @ColumnInfo(name = "full_name") val fullName: String,
    @ColumnInfo(name = "date_of_birth") val dateOfBirth: String,
    @ColumnInfo(name = "gender") val gender: String,
    @ColumnInfo(name = "medical_notes") val medicalNotes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

fun ChildEntity.toDomain(): Child = Child(
    id = id,
    caregiverId = caregiverId,
    fullName = fullName,
    dateOfBirth = dateOfBirth,
    gender = gender,
    medicalNotes = medicalNotes,
    createdAt = createdAt,
)

fun Child.toEntity(): ChildEntity = ChildEntity(
    id = id,
    caregiverId = caregiverId,
    fullName = fullName,
    dateOfBirth = dateOfBirth,
    gender = gender,
    medicalNotes = medicalNotes,
    createdAt = createdAt,
)
