package com.mamalink.app.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mamalink.app.data.local.entity.CaregiverEntity
import com.mamalink.app.data.local.entity.CaregiverWithChildrenRelation

@Dao
interface CaregiverDao {
    @Query("SELECT * FROM caregivers ORDER BY full_name ASC")
    fun observeAllCaregivers(): LiveData<List<CaregiverEntity>>

    @Query("SELECT * FROM caregivers WHERE id = :id")
    fun observeCaregiverById(id: Long): LiveData<CaregiverEntity?>

    @Transaction
    @Query("SELECT * FROM caregivers WHERE id = :id")
    fun observeCaregiverWithChildren(id: Long): LiveData<CaregiverWithChildrenRelation?>

    @Query("SELECT * FROM caregivers WHERE id = :id")
    suspend fun getCaregiverById(id: Long): CaregiverEntity?

    @Transaction
    @Query("SELECT * FROM caregivers WHERE id = :id")
    suspend fun getCaregiverWithChildren(id: Long): CaregiverWithChildrenRelation?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCaregiver(caregiver: CaregiverEntity): Long

    @Update
    suspend fun updateCaregiver(caregiver: CaregiverEntity): Int

    @Delete
    suspend fun deleteCaregiver(caregiver: CaregiverEntity): Int

    @Query("DELETE FROM caregivers WHERE id = :id")
    suspend fun deleteCaregiverById(id: Long): Int
}
