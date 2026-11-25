package com.mamalink.app.ui.appointment

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mamalink.app.domain.model.Appointment
import com.mamalink.app.domain.repository.AppointmentRepository
import com.mamalink.app.notifications.AppointmentReminderScheduler
import com.mamalink.app.ui.common.RepositoryOperationState
import com.mamalink.app.ui.common.OperationRejected
import com.mamalink.app.ui.common.validateInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import java.time.Clock

/** Manages appointments for a caregiver and exposes their repository observation. */
class AppointmentViewModel(
    private val repository: AppointmentRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val reminderScheduler: AppointmentReminderScheduler? = null,
) : ViewModel() {
    private val _operationState = MutableLiveData(RepositoryOperationState())
    val operationState: LiveData<RepositoryOperationState> = _operationState

    fun observeAppointments(caregiverId: Long): LiveData<List<Appointment>> =
        repository.observeByCaregiver(caregiverId)

    fun observeAppointment(id: Long): LiveData<Appointment?> = repository.observeById(id)

    fun addAppointment(appointment: Appointment) = perform(RepositoryOperationState.Operation.INSERT) {
        validate(appointment)
        validateInput(!appointment.scheduledAt.isBefore(clock.instant()), "New appointment time cannot be in the past.")
        val id = persistChange { repository.insert(appointment.copy(id = 0L)) }
        if (id > 0L) scheduleReminder(appointment.copy(id = id))
        id
    }

    fun updateAppointment(appointment: Appointment) = perform(RepositoryOperationState.Operation.UPDATE) {
        validateInput(appointment.id > 0, "A saved appointment is required.")
        validate(appointment)
        validateInput(persistChange { repository.update(appointment) } > 0, "Appointment was not found.")
        scheduleReminder(appointment)
        appointment.id
    }

    fun deleteAppointment(id: Long) = perform(RepositoryOperationState.Operation.DELETE) {
        validateInput(id > 0, "A saved appointment is required.")
        validateInput(persistChange { repository.deleteById(id) } > 0, "Appointment was not found.")
        repairAfterWrite { reminderScheduler?.cancelReminder(id) }
        id
    }

    private suspend fun <T> persistChange(change: suspend () -> T): T =
        if (reminderScheduler != null) reminderScheduler.persistChange(change) else change()

    private suspend fun scheduleReminder(appointment: Appointment) =
        repairAfterWrite { reminderScheduler?.scheduleReminder(appointment) }

    private suspend fun repairAfterWrite(repair: suspend () -> Unit) {
        try {
            repair()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // The Room write succeeded. Its precommitted journal and periodic worker repair this.
            android.util.Log.e("ReminderRecovery", "Appointment saved; reminder repair remains pending", failure)
        }
    }

    private fun validate(appointment: Appointment) {
        validateInput(appointment.title.isNotBlank(), "Appointment title is required.")
        validateInput(appointment.caregiverId > 0, "A caregiver is required.")
        validateInput(appointment.childId == null || appointment.childId > 0, "Select a saved child or leave it empty.")
        try {
            appointment.scheduledAt.toEpochMilli()
        } catch (_: ArithmeticException) {
            throw OperationRejected("Appointment time is outside the supported range.")
        }
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
                _operationState.value = RepositoryOperationState(error = "Unable to change appointment. Check related records and try again.")
            }
        }
    }

    class Factory(
        private val repository: AppointmentRepository,
        private val reminderScheduler: AppointmentReminderScheduler? = null,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == AppointmentViewModel::class.java) { "Unknown ViewModel: $modelClass" }
            @Suppress("UNCHECKED_CAST")
            return AppointmentViewModel(repository, reminderScheduler = reminderScheduler) as T
        }
    }
}
