package com.mamalink.app.ui.child

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.mamalink.app.MamaLinkApplication
import com.mamalink.app.R
import com.mamalink.app.domain.model.Caregiver
import com.mamalink.app.domain.model.Child
import com.mamalink.app.ui.caregiver.CaregiverViewModel
import com.mamalink.app.ui.common.recordForm
import com.mamalink.app.ui.common.scrollable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class ChildEditorDialogFragment : DialogFragment() {

    private val app get() = requireActivity().application as MamaLinkApplication
    private val vm: ChildViewModel by viewModels({ requireParentFragment() }) {
        ChildViewModel.Factory(app.childRepository)
    }
    private val caregiversVm: CaregiverViewModel by viewModels({ requireParentFragment() }) {
        CaregiverViewModel.Factory(app.caregiverRepository)
    }

    private lateinit var nameInput: EditText
    private lateinit var caregiverPicker: Spinner
    private lateinit var dobButton: Button

    private var existingId: Long = 0L
    private var caregivers: List<Caregiver> = emptyList()
    private var selectedCaregiverId: Long = 0L
    private var chosenDate: LocalDate = LocalDate.now()
    private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = arguments
        existingId = args?.getLong(ARG_EXISTING_ID, 0L) ?: 0L
        var submissionPending = savedInstanceState?.getBoolean(KEY_SUBMISSION_PENDING) ?: false
        val initialName = args?.getString(ARG_CHILD_NAME)
        val initialCaregiverId = args?.getLong(ARG_CAREGIVER_ID, 0L) ?: 0L
        val initialDobEpochDay = args?.getLong(ARG_DOB_EPOCH_DAY, LocalDate.now().toEpochDay()) ?: LocalDate.now().toEpochDay()

        selectedCaregiverId = savedInstanceState?.getLong(KEY_CAREGIVER_ID) ?: initialCaregiverId
        chosenDate = savedInstanceState?.getLong(KEY_DOB_EPOCH_DAY)?.let { LocalDate.ofEpochDay(it) }
            ?: LocalDate.ofEpochDay(initialDobEpochDay)

        val form = requireContext().recordForm()
        nameInput = EditText(requireContext()).apply {
            hint = getString(R.string.child_name)
            contentDescription = hint
            setText(savedInstanceState?.getString(KEY_NAME) ?: initialName)
        }

        caregiverPicker = Spinner(requireContext()).apply {
            contentDescription = getString(R.string.select_caregiver)
        }

        dobButton = Button(requireContext())
        fun updateDateText() {
            dobButton.text = chosenDate.format(dateFormat)
        }
        updateDateText()

        dobButton.setOnClickListener {
            val today = Calendar.getInstance()
            val dateDialog = DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    chosenDate = LocalDate.of(year, month + 1, day)
                    updateDateText()
                },
                chosenDate.year,
                chosenDate.monthValue - 1,
                chosenDate.dayOfMonth,
            ).apply {
                datePicker.maxDate = today.timeInMillis
            }
            dateDialog.show()
        }

        form.addView(nameInput)
        form.addView(TextView(requireContext()).apply { text = getString(R.string.caregiver) })
        form.addView(caregiverPicker)
        form.addView(TextView(requireContext()).apply { text = getString(R.string.date_of_birth) })
        form.addView(dobButton)

        caregiversVm.caregivers.observe(this) { list ->
            caregivers = list
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                list.map { it.fullName },
            )
            caregiverPicker.adapter = adapter
            val index = list.indexOfFirst { it.id == selectedCaregiverId }.coerceAtLeast(0)
            if (list.isNotEmpty()) {
                caregiverPicker.setSelection(index)
                selectedCaregiverId = list[index].id
            }
            (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled =
                list.isNotEmpty() && !submissionPending
        }

        caregiverPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, v: android.view.View?, position: Int, id: Long) {
                caregivers.getOrNull(position)?.let { selectedCaregiverId = it.id }
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (existingId == 0L) R.string.add_child else R.string.edit_child)
            .setView(form.scrollable())
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            saveButton.isEnabled = caregivers.isNotEmpty() && !submissionPending
            dialog.setCancelable(!submissionPending)

            vm.operationState.observe(this) { state ->
                if (!submissionPending) return@observe
                if (state.inProgress) {
                    saveButton.isEnabled = false
                } else if (state.error != null) {
                    submissionPending = false
                    saveButton.isEnabled = caregivers.isNotEmpty()
                    dialog.setCancelable(true)
                } else if (state.completedOperation != null) {
                    submissionPending = false
                    dismissAllowingStateLoss()
                } else {
                    submissionPending = false
                    saveButton.isEnabled = caregivers.isNotEmpty()
                    dialog.setCancelable(true)
                }
            }

            cancelButton.setOnClickListener {
                if (!submissionPending) dismiss()
            }
            saveButton.setOnClickListener {
                if (submissionPending) return@setOnClickListener
                if (nameInput.text.isNullOrBlank()) {
                    nameInput.error = getString(R.string.name_required)
                    return@setOnClickListener
                }
                if (caregivers.isEmpty()) return@setOnClickListener

                val caregiverId = caregivers.getOrNull(caregiverPicker.selectedItemPosition)?.id
                    ?: selectedCaregiverId
                val record = Child(
                    id = existingId,
                    caregiverId = caregiverId,
                    name = nameInput.text.toString().trim(),
                    dateOfBirth = chosenDate,
                )
                submissionPending = true
                saveButton.isEnabled = false
                dialog.setCancelable(false)
                val expectedOperation = if (existingId == 0L) {
                    com.mamalink.app.ui.common.RepositoryOperationState.Operation.INSERT
                } else {
                    com.mamalink.app.ui.common.RepositoryOperationState.Operation.UPDATE
                }
                if (existingId == 0L) vm.addChild(record) else vm.updateChild(record)
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
        if (::nameInput.isInitialized) {
            outState.putString(KEY_NAME, nameInput.text.toString())
        }
        outState.putLong(KEY_CAREGIVER_ID, selectedCaregiverId)
        outState.putLong(KEY_DOB_EPOCH_DAY, chosenDate.toEpochDay())
        outState.putBoolean(KEY_SUBMISSION_PENDING, vm.operationState.value?.inProgress == true)
    }

    companion object {
        const val TAG = "ChildEditorDialogFragment"
        private const val ARG_EXISTING_ID = "existing_id"
        private const val ARG_CHILD_NAME = "child_name"
        private const val ARG_CAREGIVER_ID = "caregiver_id"
        private const val ARG_DOB_EPOCH_DAY = "dob_epoch_day"

        private const val KEY_NAME = "key_name"
        private const val KEY_CAREGIVER_ID = "key_caregiver_id"
        private const val KEY_DOB_EPOCH_DAY = "key_dob_epoch_day"
        private const val KEY_SUBMISSION_PENDING = "key_submission_pending"

        fun newInstance(existing: Child?, defaultCaregiverId: Long): ChildEditorDialogFragment {
            return ChildEditorDialogFragment().apply {
                arguments = Bundle().apply {
                    if (existing != null) {
                        putLong(ARG_EXISTING_ID, existing.id)
                        putString(ARG_CHILD_NAME, existing.name)
                        putLong(ARG_CAREGIVER_ID, existing.caregiverId)
                        putLong(ARG_DOB_EPOCH_DAY, existing.dateOfBirth.toEpochDay())
                    } else {
                        putLong(ARG_CAREGIVER_ID, defaultCaregiverId)
                        putLong(ARG_DOB_EPOCH_DAY, LocalDate.now().toEpochDay())
                    }
                }
            }
        }
    }
}
