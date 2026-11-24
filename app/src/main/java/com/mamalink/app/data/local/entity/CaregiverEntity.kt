package com.mamalink.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mamalink.app.domain.model.Caregiver

@Entity(tableName = "caregivers")
data class CaregiverEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "full_name") val fullName: String,
    @ColumnInfo(name = "phone_number") val phoneNumber: String? = null,
)

fun CaregiverEntity.toDomain(): Caregiver = Caregiver(id, fullName, phoneNumber)
fun Caregiver.toEntity(): CaregiverEntity = CaregiverEntity(id, fullName, phoneNumber)
