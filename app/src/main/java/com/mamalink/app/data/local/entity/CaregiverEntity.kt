package com.mamalink.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mamalink.app.data.model.Caregiver

@Entity(tableName = "caregivers")
data class CaregiverEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "full_name") val fullName: String,
    @ColumnInfo(name = "phone_number") val phoneNumber: String,
    @ColumnInfo(name = "email") val email: String,
    @ColumnInfo(name = "address") val address: String,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

fun CaregiverEntity.toDomain(): Caregiver = Caregiver(
    id = id,
    fullName = fullName,
    phoneNumber = phoneNumber,
    email = email,
    address = address,
    notes = notes,
    createdAt = createdAt,
)

fun Caregiver.toEntity(): CaregiverEntity = CaregiverEntity(
    id = id,
    fullName = fullName,
    phoneNumber = phoneNumber,
    email = email,
    address = address,
    notes = notes,
    createdAt = createdAt,
)
