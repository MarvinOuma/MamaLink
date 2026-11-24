package com.mamalink.app.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.mamalink.app.data.local.dao.ChildDao
import com.mamalink.app.data.local.entity.toDomain
import com.mamalink.app.data.local.entity.toEntity
import com.mamalink.app.data.model.Child

class RoomChildRepository(private val dao: ChildDao) : ChildRepository {
    override fun observeAllChildren(): LiveData<List<Child>> =
        dao.observeAllChildren().map { list -> list.map { it.toDomain() } }

    override fun observeChildrenByCaregiverId(caregiverId: Long): LiveData<List<Child>> =
        dao.observeChildrenByCaregiverId(caregiverId).map { list -> list.map { it.toDomain() } }

    override fun observeChildById(id: Long): LiveData<Child?> =
        dao.observeChildById(id).map { it?.toDomain() }

    override suspend fun getChildById(id: Long): Child? =
        dao.getChildById(id)?.toDomain()

    override suspend fun getChildrenByCaregiverId(caregiverId: Long): List<Child> =
        dao.getChildrenByCaregiverId(caregiverId).map { it.toDomain() }

    override suspend fun insertChild(child: Child): Long =
        dao.insertChild(child.toEntity())

    override suspend fun updateChild(child: Child) =
        dao.updateChild(child.toEntity())

    override suspend fun deleteChild(child: Child) =
        dao.deleteChild(child.toEntity())

    override suspend fun deleteChildById(id: Long) =
        dao.deleteChildById(id)
}
