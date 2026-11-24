package com.mamalink.app.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.mamalink.app.data.local.dao.CaregiverDao
import com.mamalink.app.data.local.entity.toDomain
import com.mamalink.app.data.local.entity.toEntity
import com.mamalink.app.data.model.Caregiver
import com.mamalink.app.data.model.CaregiverWithChildren

class RoomCaregiverRepository(private val dao: CaregiverDao) : CaregiverRepository {
    override fun observeAllCaregivers(): LiveData<List<Caregiver>> =
        dao.observeAllCaregivers().map { list -> list.map { it.toDomain() } }

    override fun observeCaregiverById(id: Long): LiveData<Caregiver?> =
        dao.observeCaregiverById(id).map { it?.toDomain() }

    override fun observeCaregiverWithChildren(id: Long): LiveData<CaregiverWithChildren?> =
        dao.observeCaregiverWithChildren(id).map { it?.toDomain() }

    override suspend fun getCaregiverById(id: Long): Caregiver? =
        dao.getCaregiverById(id)?.toDomain()

    override suspend fun getCaregiverWithChildren(id: Long): CaregiverWithChildren? =
        dao.getCaregiverWithChildren(id)?.toDomain()

    override suspend fun insertCaregiver(caregiver: Caregiver): Long =
        dao.insertCaregiver(caregiver.toEntity())

    override suspend fun updateCaregiver(caregiver: Caregiver) =
        dao.updateCaregiver(caregiver.toEntity())

    override suspend fun deleteCaregiver(caregiver: Caregiver) =
        dao.deleteCaregiver(caregiver.toEntity())

    override suspend fun deleteCaregiverById(id: Long) =
        dao.deleteCaregiverById(id)
}
