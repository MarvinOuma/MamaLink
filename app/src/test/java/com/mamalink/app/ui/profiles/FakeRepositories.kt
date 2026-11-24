package com.mamalink.app.ui.profiles

import androidx.lifecycle.MutableLiveData
import com.mamalink.app.domain.model.Appointment
import com.mamalink.app.domain.model.Caregiver
import com.mamalink.app.domain.model.Child
import com.mamalink.app.domain.repository.AppointmentRepository
import com.mamalink.app.domain.repository.CaregiverRepository
import com.mamalink.app.domain.repository.ChildRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/** Controllable repository boundary: no database, validation, or ViewModel logic. */
open class FakeWrites<T> {
    val list = MutableLiveData<List<T>>(emptyList())
    val single = MutableLiveData<T?>(null)
    var observedId: Long? = null
    var observedCaregiver: Long? = null
    var lastValue: T? = null
    var lastDeletedId: Long? = null
    var calls = 0
    var generatedId = 71L
    var rows = 1
    var failure: Exception? = null
    var gate: CompletableDeferred<Unit>? = null
    var cancelled = false

    private suspend fun beforeWrite() {
        calls++
        try {
            gate?.await()
            failure?.let { throw it }
        } catch (cancel: CancellationException) {
            cancelled = true
            throw cancel
        }
    }
    suspend fun insertValue(value: T): Long {
        lastValue = value
        beforeWrite()
        return generatedId
    }
    suspend fun updateValue(value: T): Int {
        lastValue = value
        beforeWrite()
        return rows
    }
    suspend fun deleteValue(id: Long): Int {
        lastDeletedId = id
        beforeWrite()
        return rows
    }
}

class FakeCaregivers : FakeWrites<Caregiver>(), CaregiverRepository {
    override fun observeAll() = list
    override fun observeById(id: Long) = single.also { observedId = id }
    override suspend fun insert(caregiver: Caregiver) = insertValue(caregiver)
    override suspend fun update(caregiver: Caregiver) = updateValue(caregiver)
    override suspend fun deleteById(id: Long) = deleteValue(id)
}

class FakeChildren : FakeWrites<Child>(), ChildRepository {
    override fun observeByCaregiver(caregiverId: Long) = list.also { observedCaregiver = caregiverId }
    override fun observeById(id: Long) = single.also { observedId = id }
    override suspend fun insert(child: Child) = insertValue(child)
    override suspend fun update(child: Child) = updateValue(child)
    override suspend fun deleteById(id: Long) = deleteValue(id)
}

class FakeAppointments : FakeWrites<Appointment>(), AppointmentRepository {
    override fun observeByCaregiver(caregiverId: Long) = list.also { observedCaregiver = caregiverId }
    override fun observeById(id: Long) = single.also { observedId = id }
    override suspend fun getById(id: Long) = single.value
    override suspend fun getAll() = list.value.orEmpty()
    override suspend fun insert(appointment: Appointment) = insertValue(appointment)
    override suspend fun update(appointment: Appointment) = updateValue(appointment)
    override suspend fun deleteById(id: Long) = deleteValue(id)
}
