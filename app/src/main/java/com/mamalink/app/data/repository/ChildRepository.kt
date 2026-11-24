package com.mamalink.app.data.repository

import androidx.lifecycle.LiveData
import com.mamalink.app.data.model.Child

interface ChildRepository {
    fun observeAllChildren(): LiveData<List<Child>>
    fun observeChildrenByCaregiverId(caregiverId: Long): LiveData<List<Child>>
    fun observeChildById(id: Long): LiveData<Child?>
    suspend fun getChildById(id: Long): Child?
    suspend fun getChildrenByCaregiverId(caregiverId: Long): List<Child>
    suspend fun insertChild(child: Child): Long
    suspend fun updateChild(child: Child)
    suspend fun deleteChild(child: Child)
    suspend fun deleteChildById(id: Long)
}
