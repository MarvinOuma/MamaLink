package com.mamalink.app.ui.appointment

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.mamalink.app.MamaLinkApplication
import com.mamalink.app.R
import com.mamalink.app.domain.model.Appointment
import com.mamalink.app.domain.model.Caregiver
import com.mamalink.app.domain.model.Child
import com.mamalink.app.ui.caregiver.CaregiverViewModel
import com.mamalink.app.ui.child.ChildViewModel
import com.mamalink.app.ui.common.CaregiverRecordsObservation
import com.mamalink.app.ui.common.recordForm
import com.mamalink.app.ui.common.scrollable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

class AppointmentEditorDialogFragment : DialogFragment() {

    private val app get() = requireActivity().application as MamaLinkApplication
    private val vm: AppointmentViewModel by viewModels({ requireParentFragment() }) {
        AppointmentViewModel.Factory(app.appointmentRepository, app.reminderScheduler)
    }
    private val caregiversVm: CaregiverViewModel by viewModels({ requireParentFragment() }) {
        CaregiverViewModel.Factory(app.caregiverRepository)
    }
    private val childrenVm: ChildViewModel by viewModels({ requireParentFragment() }) {
        ChildViewModel.Factory(app.childRepository)
    }

    private lateinit var titleInput: EditText
    private lateinit var caregiverPicker: Spinner
    private lateinit var childPicker: Spinner
    private lateinit var dateButton: Button
    private lateinit var timeButton: Button
    private lateinit var locationInput: EditText

    private var existingId: Long = 0L
    private var caregivers: List<Caregiver> = emptyList()
    private var availableChildren: List<Child> = emptyList()
    private var selectedCaregiverId: Long = 0L
    private var selectedChildId: Long? = null
    private var chosenDateTime: LocalDateTime = LocalDateTime.now().plusDays(1).withSecond(0).withNano(0)

    private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a")

    private var childrenLoaded = false
    private var childObservation: CaregiverRecordsObservation<Child>? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = arguments
        existingId = args?.getLong(ARG_EXISTING_ID, 0L) ?: 0L
        var submissionPending = savedInstanceState?.getBoolean(KEY_SUBMISSION_PENDING) ?: false
        val initialTitle = args?.getString(ARG_TITLE)
        val initialCaregiverId = args?.getLong(ARG_CAREGIVER_ID, 0L) ?: 0L
        val initialChildId = if (args?.containsKey(ARG_CHILD_ID) == true) args.getLong(ARG_CHILD_ID) else null
        val initialScheduledAtEpochMilli = args?.getLong(ARG_SCHEDULED_AT, 0L) ?: 0L
        val initialLocation = args?.getString(ARG_LOCATION)

        selectedCaregiverId = savedInstanceState?.getLong(KEY_CAREGIVER_ID) ?: initialCaregiverId
        selectedChildId = if (savedInstanceState?.containsKey(KEY_CHILD_ID) == true) {
            val id = savedInstanceState.getLong(KEY_CHILD_ID)
            if (id > 0L) id else null
        } else {
            initialChildId
        }

        chosenDateTime = if (savedInstanceState?.containsKey(KEY_SCHEDULED_AT) == true) {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(savedInstanceState.getLong(KEY_SCHEDULED_AT)), ZoneId.systemDefault())
        } else if (initialScheduledAtEpochMilli > 0L) {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(initialScheduledAtEpochMilli), ZoneId.systemDefault())
        } else {
            LocalDateTime.now().plusDays(1).withSecond(0).withNano(0)
        }

        val form = requireContext().recordForm()
        titleInput = EditText(requireContext()).apply {
            hint = getString(R.string.appointment_title_hint)
            contentDescription = hint
            setText(savedInstanceState?.getString(KEY_TITLE) ?: initialTitle)
        }

        caregiverPicker = Spinner(requireContext()).apply {
            contentDescription = getString(R.string.select_caregiver)
        }

        childPicker = Spinner(requireContext()).apply {
            contentDescription = getString(R.string.child_optional)
        }

        dateButton = Button(requireContext())
        timeButton = Button(requireContext())

        fun refreshDateTimeButtons() {
            dateButton.text = chosenDateTime.format(dateFormat)
            timeButton.text = chosenDateTime.format(timeFormat)
        }
        refreshDateTimeButtons()

        dateButton.setOnClickListener {
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    chosenDateTime = chosenDateTime.withYear(year).withMonth(month + 1).withDayOfMonth(day)
                    refreshDateTimeButtons()
                },
                chosenDateTime.year,
                chosenDateTime.monthValue - 1,
                chosenDateTime.dayOfMonth,
            ).apply {
                datePicker.minDate = if (existingId == 0L) today.timeInMillis else 0L
            }.show()
        }

        timeButton.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    chosenDateTime = chosenDateTime.withHour(hour).withMinute(minute)
                    refreshDateTimeButtons()
                },
                chosenDateTime.hour,
                chosenDateTime.minute,
                false,
            ).show()
        }

        locationInput = EditText(requireContext()).apply {
            hint = getString(R.string.location_optional)
            contentDescription = hint
            setText(savedInstanceState?.getString(KEY_LOCATION) ?: initialLocation)
        }

        form.addView(titleInput)
        form.addView(TextView(requireContext()).apply { text = getString(R.string.caregiver) })
        form.addView(caregiverPicker)
        form.addView(TextView(requireContext()).apply { text = getString(R.string.child_optional) })
        form.addView(childPicker)
        form.addView(TextView(requireContext()).apply { text = getString(R.string.appointment_date) })
        form.addView(dateButton)
        form.addView(TextView(requireContext()).apply { text = getString(R.string.appointment_time) })
        form.addView(timeButton)
        form.addView(locationInput)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (existingId == 0L) R.string.add_appointment else R.string.edit_appointment)
            .setView(form.scrollable())
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()

        childObservation = CaregiverRecordsObservation(this, childrenVm::observeChildren) { list ->
            val wantedChild = if (childrenLoaded) {
                availableChildren.getOrNull(childPicker.selectedItemPosition - 1)?.id
            } else {
                selectedChildId
            }
            availableChildren = list
            selectedChildId = wantedChild?.takeIf { id -> list.any { it.id == id } }

            childPicker.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                listOf(getString(R.string.no_child)) + list.map { it.name },
            )
            val pos = list.indexOfFirst { it.id == selectedChildId } + 1
            childPicker.setSelection(if (pos > 0) pos else 0)
            childrenLoaded = true
            childPicker.isEnabled = true
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
        }

        fun updateChildrenForCaregiver(caregiverId: Long) {
            if (selectedCaregiverId == caregiverId && childrenLoaded) return
            selectedCaregiverId = caregiverId
            availableChildren = emptyList()
            childrenLoaded = false
            childPicker.isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
            childPicker.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                listOf(getString(R.string.no_child)),
            )
            childObservation?.select(caregiverId)
        }

        caregiversVm.caregivers.observe(this) { list ->
            caregivers = list
            caregiverPicker.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                list.map { it.fullName },
            )
            if (list.isNotEmpty()) {
                val index = list.indexOfFirst { it.id == selectedCaregiverId }.coerceAtLeast(0)
                caregiverPicker.setSelection(index)
                updateChildrenForCaregiver(list[index].id)
            }
        }

        caregiverPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                caregivers.getOrNull(position)?.let {
                    if (selectedCaregiverId != it.id) {
                        selectedChildId = null
                        updateChildrenForCaregiver(it.id)
                    }
                }
            }
        }

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            saveButton.isEnabled = childrenLoaded && !submissionPending
            dialog.setCancelable(!submissionPending)

            vm.operationState.observe(this) { state ->
                if (!submissionPending) return@observe
                if (state.inProgress) {
                    saveButton.isEnabled = false
                } else if (state.error != null) {
                    submissionPending = false
                    saveButton.isEnabled = childrenLoaded
                    dialog.setCancelable(true)
                } else if (state.completedOperation != null) {
                    submissionPending = false
                    dismissAllowingStateLoss()
                } else {
                    submissionPending = false
                    saveButton.isEnabled = childrenLoaded
                    dialog.setCancelable(true)
                }
            }

            cancelButton.setOnClickListener {
                if (!submissionPending) dismiss()
            }
            saveButton.setOnClickListener {
                if (submissionPending) return@setOnClickListener
                if (titleInput.text.isNullOrBlank()) {
                    titleInput.error = getString(R.string.title_required)
                    return@setOnClickListener
                }
                if (existingId == 0L && chosenDateTime.isBefore(LocalDateTime.now())) {
                    Toast.makeText(requireContext(), R.string.past_appointment, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!childrenLoaded) return@setOnClickListener

                val caregiverId = caregivers.getOrNull(caregiverPicker.selectedItemPosition)?.id ?: selectedCaregiverId
                val childId = availableChildren.getOrNull(childPicker.selectedItemPosition - 1)?.id
                val appointment = Appointment(
                    id = existingId,
                    caregiverId = caregiverId,
                    childId = childId,
                    title = titleInput.text.toString().trim(),
                    scheduledAt = chosenDateTime.atZone(ZoneId.systemDefault()).toInstant(),
                    location = locationInput.text.toString().trim().ifBlank { null },
                )

                submissionPending = true
                saveButton.isEnabled = false
                dialog.setCancelable(false)
                val expectedOperation = if (existingId == 0L) {
                    com.mamalink.app.ui.common.RepositoryOperationState.Operation.INSERT
                } else {
                    com.mamalink.app.ui.common.RepositoryOperationState.Operation.UPDATE
                }
                if (existingId == 0L) vm.addAppointment(appointment) else vm.updateAppointment(appointment)
                val state = vm.operationState.value
                if (state?.completedOperation == expectedOperation && !state.inProgress) {
                    submissionPending = false
                    dismissAllowingStateLoss()
                }
            }
        }

        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::titleInput.isInitialized) {
            outState.putString(KEY_TITLE, titleInput.text.toString())
        }
        outState.putLong(KEY_CAREGIVER_ID, selectedCaregiverId)
        val childPos = if (::childPicker.isInitialized) childPicker.selectedItemPosition else 0
        val childId = availableChildren.getOrNull(childPos - 1)?.id
        if (childId != null) {
            outState.putLong(KEY_CHILD_ID, childId)
        } else {
            outState.putLong(KEY_CHILD_ID, 0L)
        }
        outState.putLong(KEY_SCHEDULED_AT, chosenDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        outState.putBoolean(KEY_SUBMISSION_PENDING, vm.operationState.value?.inProgress == true)
        if (::locationInput.isInitialized) {
            outState.putString(KEY_LOCATION, locationInput.text.toString())
        }
    }

    override fun onStop() {
        childObservation?.dispose()
        childObservation = null
        super.onStop()
    }

    override fun onDestroyView() {
        childObservation?.dispose()
        childObservation = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "AppointmentEditorDialogFragment"

        private const val ARG_EXISTING_ID = "existing_id"
        private const val ARG_TITLE = "title"
        private const val ARG_CAREGIVER_ID = "caregiver_id"
        private const val ARG_CHILD_ID = "child_id"
        private const val ARG_SCHEDULED_AT = "scheduled_at"
        private const val ARG_LOCATION = "location"

        private const val KEY_TITLE = "key_title"
        private const val KEY_CAREGIVER_ID = "key_caregiver_id"
        private const val KEY_CHILD_ID = "key_child_id"
        private const val KEY_SCHEDULED_AT = "key_scheduled_at"
        private const val KEY_LOCATION = "key_location"
        private const val KEY_SUBMISSION_PENDING = "key_submission_pending"

        fun newInstance(existing: Appointment?, defaultCaregiverId: Long): AppointmentEditorDialogFragment {
            return AppointmentEditorDialogFragment().apply {
                arguments = Bundle().apply {
                    if (existing != null) {
                        putLong(ARG_EXISTING_ID, existing.id)
                        putString(ARG_TITLE, existing.title)
                        putLong(ARG_CAREGIVER_ID, existing.caregiverId)
                        existing.childId?.let { putLong(ARG_CHILD_ID, it) }
                        putLong(ARG_SCHEDULED_AT, existing.scheduledAt.toEpochMilli())
                        putString(ARG_LOCATION, existing.location)
                    } else {
                        putLong(ARG_CAREGIVER_ID, defaultCaregiverId)
                    }
                }
            }
        }
    }
}
