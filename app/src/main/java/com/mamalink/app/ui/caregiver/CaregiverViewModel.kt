package com.mamalink.app.ui.caregiver

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mamalink.app.domain.model.Caregiver
import com.mamalink.app.domain.repository.CaregiverRepository
import com.mamalink.app.ui.common.RepositoryOperationState
import com.mamalink.app.ui.common.OperationRejected
import com.mamalink.app.ui.common.validateInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** Manages caregiver observation and writes without retaining any Android UI object. */
class CaregiverViewModel(private val repository: CaregiverRepository) : ViewModel() {
    val caregivers: LiveData<List<Caregiver>> = repository.observeAll()

    private val _operationState = MutableLiveData(RepositoryOperationState())
    val operationState: LiveData<RepositoryOperationState> = _operationState

    fun observeCaregiver(id: Long): LiveData<Caregiver?> = repository.observeById(id)

    fun addCaregiver(caregiver: Caregiver) = perform(RepositoryOperationState.Operation.INSERT) {
        validateInput(caregiver.fullName.isNotBlank(), "Caregiver name is required.")
        repository.insert(caregiver.copy(id = 0L))
    }

    fun updateCaregiver(caregiver: Caregiver) = perform(RepositoryOperationState.Operation.UPDATE) {
        validateInput(caregiver.id > 0, "A saved caregiver is required.")
        validateInput(caregiver.fullName.isNotBlank(), "Caregiver name is required.")
        validateInput(repository.update(caregiver) > 0, "Caregiver was not found.")
        caregiver.id
    }

    fun deleteCaregiver(id: Long) = perform(RepositoryOperationState.Operation.DELETE) {
        validateInput(id > 0, "A saved caregiver is required.")
        validateInput(repository.deleteById(id) > 0, "Caregiver was not found.")
        id
    }

    private fun perform(operation: RepositoryOperationState.Operation, action: suspend () -> Long) {
        if (_operationState.value?.inProgress == true) return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            _operationState.value = RepositoryOperationState(inProgress = true)
            try {
                val id = action()
                check(id > 0) { "Repository returned an invalid identifier" }
                _operationState.value = RepositoryOperationState(
                    completedOperation = operation,
                    affectedId = id,
                )
            } catch (cancelled: CancellationException) {
                _operationState.value = RepositoryOperationState()
                throw cancelled
            } catch (rejected: OperationRejected) {
                _operationState.value = RepositoryOperationState(error = rejected.message)
            } catch (failure: Exception) {
                _operationState.value = RepositoryOperationState(error = "Unable to change caregiver. Check related records and try again.")
            }
        }
    }

    class Factory(private val repository: CaregiverRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == CaregiverViewModel::class.java) { "Unknown ViewModel: $modelClass" }
            @Suppress("UNCHECKED_CAST")
            return CaregiverViewModel(repository) as T
        }
    }
}
