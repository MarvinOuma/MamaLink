package com.mamalink.app.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.mamalink.app.data.local.dao.ChildDao
import com.mamalink.app.data.local.entity.toDomain
import com.mamalink.app.data.local.entity.toEntity
import com.mamalink.app.domain.model.Child
import com.mamalink.app.domain.repository.ChildRepository

class RoomChildRepository(private val dao: ChildDao) : ChildRepository {
    override fun observeByCaregiver(caregiverId: Long): LiveData<List<Child>> =
        dao.observeChildrenByCaregiverId(caregiverId).map { list -> list.map { it.toDomain() } }

    override fun observeById(id: Long): LiveData<Child?> =
        dao.observeChildById(id).map { it?.toDomain() }

    override suspend fun insert(child: Child): Long {
        require(child.id == 0L) { "New records must have id=0" }
        require(child.name.isNotBlank()) { "A name or title is required" }
        return dao.insertChild(child.toEntity())
    }

    override suspend fun update(child: Child): Int {
        require(child.name.isNotBlank()) { "A name or title is required" }
        return dao.updateChild(child.toEntity())
    }

    override suspend fun deleteById(id: Long): Int = dao.deleteChildById(id)
}
