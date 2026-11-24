package com.mamalink.app.data.repository

import androidx.lifecycle.LiveData
import com.mamalink.app.data.model.Caregiver
import com.mamalink.app.data.model.CaregiverWithChildren

interface CaregiverRepository {
    fun observeAllCaregivers(): LiveData<List<Caregiver>>
    fun observeCaregiverById(id: Long): LiveData<Caregiver?>
    fun observeCaregiverWithChildren(id: Long): LiveData<CaregiverWithChildren?>
    suspend fun getCaregiverById(id: Long): Caregiver?
    suspend fun getCaregiverWithChildren(id: Long): CaregiverWithChildren?
    suspend fun insertCaregiver(caregiver: Caregiver): Long
    suspend fun updateCaregiver(caregiver: Caregiver)
    suspend fun deleteCaregiver(caregiver: Caregiver)
    suspend fun deleteCaregiverById(id: Long)
}
