package com.mamalink.app.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.mamalink.app.data.local.dao.CaregiverDao
import com.mamalink.app.data.local.entity.toDomain
import com.mamalink.app.data.local.entity.toEntity
import com.mamalink.app.domain.model.Caregiver
import com.mamalink.app.domain.repository.CaregiverRepository

class RoomCaregiverRepository(private val dao: CaregiverDao) : CaregiverRepository {
    override fun observeAll(): LiveData<List<Caregiver>> =
        dao.observeAllCaregivers().map { list -> list.map { it.toDomain() } }

    override fun observeById(id: Long): LiveData<Caregiver?> =
        dao.observeCaregiverById(id).map { it?.toDomain() }

    override suspend fun insert(caregiver: Caregiver): Long {
        require(caregiver.id == 0L) { "New records must have id=0" }
        require(caregiver.fullName.isNotBlank()) { "A name or title is required" }
        return dao.insertCaregiver(caregiver.toEntity())
    }

    override suspend fun update(caregiver: Caregiver): Int {
        require(caregiver.fullName.isNotBlank()) { "A name or title is required" }
        return dao.updateCaregiver(caregiver.toEntity())
    }

    override suspend fun deleteById(id: Long): Int = dao.deleteCaregiverById(id)
}
