package com.mamalink.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mamalink.app.domain.model.Child
import java.time.LocalDate

@Entity(
    tableName = "children",
    foreignKeys = [
        ForeignKey(
            entity = CaregiverEntity::class,
            parentColumns = ["id"],
            childColumns = ["caregiver_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["caregiver_id"]),
        // Parent key for appointments' same-caregiver composite foreign key.
        Index(value = ["id", "caregiver_id"], unique = true),
    ],
)
data class ChildEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "caregiver_id") val caregiverId: Long,
    val name: String,
    @ColumnInfo(name = "date_of_birth") val dateOfBirth: LocalDate,
)

fun ChildEntity.toDomain(): Child = Child(id, caregiverId, name, dateOfBirth)
fun Child.toEntity(): ChildEntity = ChildEntity(id, caregiverId, name, dateOfBirth)
