package com.mamalink.app.ui.child

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mamalink.app.domain.model.Child
import com.mamalink.app.domain.repository.ChildRepository
import com.mamalink.app.ui.common.RepositoryOperationState
import com.mamalink.app.ui.common.OperationRejected
import com.mamalink.app.ui.common.validateInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** Manages child profiles belonging to a caregiver and their repository writes. */
class ChildViewModel(
    private val repository: ChildRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val _operationState = MutableLiveData(RepositoryOperationState())
    val operationState: LiveData<RepositoryOperationState> = _operationState

    fun observeChildren(caregiverId: Long): LiveData<List<Child>> =
        repository.observeByCaregiver(caregiverId)

    fun observeChild(id: Long): LiveData<Child?> = repository.observeById(id)

    fun addChild(child: Child) = perform(RepositoryOperationState.Operation.INSERT) {
        validate(child)
        repository.insert(child.copy(id = 0L))
    }

    fun updateChild(child: Child) = perform(RepositoryOperationState.Operation.UPDATE) {
        validateInput(child.id > 0, "A saved child is required.")
        validate(child)
        validateInput(repository.update(child) > 0, "Child was not found.")
        child.id
    }

    fun deleteChild(id: Long) = perform(RepositoryOperationState.Operation.DELETE) {
        validateInput(id > 0, "A saved child is required.")
        validateInput(repository.deleteById(id) > 0, "Child was not found.")
        id
    }

    private fun validate(child: Child) {
        validateInput(child.name.isNotBlank(), "Child name is required.")
        validateInput(child.caregiverId > 0, "A caregiver is required.")
        validateInput(!child.dateOfBirth.isAfter(LocalDate.now(clock)), "Birth date cannot be in the future.")
    }

    private fun perform(operation: RepositoryOperationState.Operation, action: suspend () -> Long) {
        if (_operationState.value?.inProgress == true) return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            _operationState.value = RepositoryOperationState(inProgress = true)
            try {
                val id = action()
                check(id > 0) { "Repository returned an invalid identifier" }
                _operationState.value = RepositoryOperationState(completedOperation = operation, affectedId = id)
            } catch (cancelled: CancellationException) {
                _operationState.value = RepositoryOperationState()
                throw cancelled
            } catch (rejected: OperationRejected) {
                _operationState.value = RepositoryOperationState(error = rejected.message)
            } catch (failure: Exception) {
                _operationState.value = RepositoryOperationState(error = "Unable to change child. Check related records and try again.")
            }
        }
    }

    class Factory(private val repository: ChildRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == ChildViewModel::class.java) { "Unknown ViewModel: $modelClass" }
            @Suppress("UNCHECKED_CAST")
            return ChildViewModel(repository) as T
        }
    }
}
