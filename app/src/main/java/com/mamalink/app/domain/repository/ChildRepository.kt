package com.mamalink.app.domain.repository

import androidx.lifecycle.LiveData
import com.mamalink.app.domain.model.Child

interface ChildRepository {
    fun observeByCaregiver(caregiverId: Long): LiveData<List<Child>>
    fun observeById(id: Long): LiveData<Child?>
    /** Returns the generated ID. The caller passes id=0 for a new record. */
    suspend fun insert(child: Child): Long
    /** Returns rows affected; zero means the ID was not found. */
    suspend fun update(child: Child): Int
    suspend fun deleteById(id: Long): Int
}
