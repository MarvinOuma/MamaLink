package com.mamalink.app.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mamalink.app.data.local.entity.ChildEntity

@Dao
interface ChildDao {
    @Query("SELECT * FROM children ORDER BY full_name ASC")
    fun observeAllChildren(): LiveData<List<ChildEntity>>

    @Query("SELECT * FROM children WHERE caregiver_id = :caregiverId ORDER BY full_name ASC")
    fun observeChildrenByCaregiverId(caregiverId: Long): LiveData<List<ChildEntity>>

    @Query("SELECT * FROM children WHERE id = :id")
    fun observeChildById(id: Long): LiveData<ChildEntity?>

    @Query("SELECT * FROM children WHERE id = :id")
    suspend fun getChildById(id: Long): ChildEntity?

    @Query("SELECT * FROM children WHERE caregiver_id = :caregiverId ORDER BY full_name ASC")
    suspend fun getChildrenByCaregiverId(caregiverId: Long): List<ChildEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChild(child: ChildEntity): Long

    @Update
    suspend fun updateChild(child: ChildEntity)

    @Delete
    suspend fun deleteChild(child: ChildEntity)

    @Query("DELETE FROM children WHERE id = :id")
    suspend fun deleteChildById(id: Long)
}
