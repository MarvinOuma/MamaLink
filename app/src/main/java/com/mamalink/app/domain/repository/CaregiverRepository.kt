package com.mamalink.app.domain.repository

import androidx.lifecycle.LiveData
import com.mamalink.app.domain.model.Caregiver

/** Contract for Stage 2 ViewModels; Stage 3 supplies the Room implementation. */
interface CaregiverRepository {
    fun observeAll(): LiveData<List<Caregiver>>
    fun observeById(id: Long): LiveData<Caregiver?>
    /** Returns the generated ID. The caller passes id=0 for a new record. */
    suspend fun insert(caregiver: Caregiver): Long
    /** Returns the number of rows changed (0 if the ID was absent). */
    suspend fun update(caregiver: Caregiver): Int
    /** Returns the number of rows deleted (0 if the ID was absent). */
    suspend fun deleteById(id: Long): Int
}
