package com.mamalink.app.ui.appointment

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mamalink.app.domain.model.Appointment
import com.mamalink.app.domain.repository.AppointmentRepository
import com.mamalink.app.ui.common.RepositoryOperationState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Manages appointments for a caregiver and exposes their repository observation. */
class AppointmentViewModel(private val repository: AppointmentRepository) : ViewModel() {
    private val _operationState = MutableLiveData(RepositoryOperationState())
    val operationState: LiveData<RepositoryOperationState> = _operationState

    fun observeAppointments(caregiverId: Long): LiveData<List<Appointment>> =
        repository.observeByCaregiver(caregiverId)

    fun observeAppointment(id: Long): LiveData<Appointment?> = repository.observeById(id)

    fun addAppointment(appointment: Appointment) = perform(RepositoryOperationState.Operation.INSERT) {
        repository.insert(appointment.copy(id = 0L))
    }

    fun updateAppointment(appointment: Appointment) = perform(RepositoryOperationState.Operation.UPDATE) {
        repository.update(appointment).also { check(it > 0) { "Appointment was not found." } }
        appointment.id
    }

    fun deleteAppointment(id: Long) = perform(RepositoryOperationState.Operation.DELETE) {
        repository.deleteById(id).also { check(it > 0) { "Appointment was not found." } }
        id
    }

    private fun perform(operation: RepositoryOperationState.Operation, action: suspend () -> Long) {
        viewModelScope.launch {
            _operationState.value = RepositoryOperationState(inProgress = true)
            try {
                val id = action()
                _operationState.value = RepositoryOperationState(completedOperation = operation, affectedId = id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _operationState.value = RepositoryOperationState(error = failure.message ?: "Unable to save appointment.")
            }
        }
    }

    class Factory(private val repository: AppointmentRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == AppointmentViewModel::class.java) { "Unknown ViewModel: $modelClass" }
            @Suppress("UNCHECKED_CAST")
            return AppointmentViewModel(repository) as T
        }
    }
}
