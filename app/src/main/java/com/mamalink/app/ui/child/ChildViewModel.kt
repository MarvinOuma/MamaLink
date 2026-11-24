package com.mamalink.app.ui.child

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mamalink.app.domain.model.Child
import com.mamalink.app.domain.repository.ChildRepository
import com.mamalink.app.ui.common.RepositoryOperationState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Manages child profiles belonging to a caregiver and their repository writes. */
class ChildViewModel(private val repository: ChildRepository) : ViewModel() {
    private val _operationState = MutableLiveData(RepositoryOperationState())
    val operationState: LiveData<RepositoryOperationState> = _operationState

    fun observeChildren(caregiverId: Long): LiveData<List<Child>> =
        repository.observeByCaregiver(caregiverId)

    fun observeChild(id: Long): LiveData<Child?> = repository.observeById(id)

    fun addChild(child: Child) = perform(RepositoryOperationState.Operation.INSERT) {
        repository.insert(child.copy(id = 0L))
    }

    fun updateChild(child: Child) = perform(RepositoryOperationState.Operation.UPDATE) {
        repository.update(child).also { check(it > 0) { "Child was not found." } }
        child.id
    }

    fun deleteChild(id: Long) = perform(RepositoryOperationState.Operation.DELETE) {
        repository.deleteById(id).also { check(it > 0) { "Child was not found." } }
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
                _operationState.value = RepositoryOperationState(error = failure.message ?: "Unable to save child.")
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
