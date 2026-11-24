package com.mamalink.app.ui.caregiver

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.mamalink.app.MamaLinkApplication
import com.mamalink.app.R
import com.mamalink.app.domain.model.Caregiver
import com.mamalink.app.ui.common.recordForm
import com.mamalink.app.ui.common.scrollable

class CaregiverEditorDialogFragment : DialogFragment() {

    private val vm: CaregiverViewModel by viewModels({ requireParentFragment() }) {
        CaregiverViewModel.Factory(
            (requireActivity().application as MamaLinkApplication).caregiverRepository,
        )
    }

    private lateinit var nameInput: EditText
    private lateinit var phoneInput: EditText
    private var existingId: Long = 0L

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = arguments
        existingId = args?.getLong(ARG_EXISTING_ID, 0L) ?: 0L
        var submissionPending = savedInstanceState?.getBoolean(KEY_SUBMISSION_PENDING) ?: false
        val initialName = args?.getString(ARG_FULL_NAME)
        val initialPhone = args?.getString(ARG_PHONE)

        val form = requireContext().recordForm()
        nameInput = EditText(requireContext()).apply {
            hint = getString(R.string.full_name)
            contentDescription = hint
            setText(savedInstanceState?.getString(KEY_NAME) ?: initialName)
        }
        phoneInput = EditText(requireContext()).apply {
            hint = getString(R.string.phone_optional)
            contentDescription = hint
            inputType = InputType.TYPE_CLASS_PHONE
            setText(savedInstanceState?.getString(KEY_PHONE) ?: initialPhone)
        }
        form.addView(nameInput)
        form.addView(phoneInput)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (existingId == 0L) R.string.add_caregiver else R.string.edit_caregiver)
            .setView(form.scrollable())
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            saveButton.isEnabled = !submissionPending
            dialog.setCancelable(!submissionPending)

            vm.operationState.observe(this) { state ->
                if (!submissionPending) return@observe
                if (state.inProgress) {
                    saveButton.isEnabled = false
                } else if (state.error != null) {
                    submissionPending = false
                    saveButton.isEnabled = true
                    dialog.setCancelable(true)
                } else if (state.completedOperation != null) {
                    submissionPending = false
                    dismissAllowingStateLoss()
                } else {
                    submissionPending = false
                    saveButton.isEnabled = true
                    dialog.setCancelable(true)
                }
            }

            cancelButton.setOnClickListener {
                if (!submissionPending) dismiss()
            }
            saveButton.setOnClickListener {
                if (submissionPending || nameInput.text.isNullOrBlank()) {
                    if (nameInput.text.isNullOrBlank()) {
                        nameInput.error = getString(R.string.name_required)
                    }
                    return@setOnClickListener
                }
                val record = Caregiver(
                    id = existingId,
                    fullName = nameInput.text.toString().trim(),
                    phoneNumber = phoneInput.text.toString().trim().ifBlank { null },
                )
                submissionPending = true
                saveButton.isEnabled = false
                dialog.setCancelable(false)
                val expectedOperation = if (existingId == 0L) {
                    com.mamalink.app.ui.common.RepositoryOperationState.Operation.INSERT
                } else {
                    com.mamalink.app.ui.common.RepositoryOperationState.Operation.UPDATE
                }
                if (existingId == 0L) vm.addCaregiver(record) else vm.updateCaregiver(record)
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
        if (::phoneInput.isInitialized) {
            outState.putString(KEY_PHONE, phoneInput.text.toString())
        }
        outState.putBoolean(KEY_SUBMISSION_PENDING, vm.operationState.value?.inProgress == true)
    }

    companion object {
        const val TAG = "CaregiverEditorDialogFragment"
        private const val ARG_EXISTING_ID = "existing_id"
        private const val ARG_FULL_NAME = "full_name"
        private const val ARG_PHONE = "phone"
        private const val KEY_NAME = "key_name"
        private const val KEY_PHONE = "key_phone"
        private const val KEY_SUBMISSION_PENDING = "key_submission_pending"

        fun newInstance(existing: Caregiver?): CaregiverEditorDialogFragment {
            return CaregiverEditorDialogFragment().apply {
                arguments = Bundle().apply {
                    if (existing != null) {
                        putLong(ARG_EXISTING_ID, existing.id)
                        putString(ARG_FULL_NAME, existing.fullName)
                        putString(ARG_PHONE, existing.phoneNumber)
                    }
                }
            }
        }
    }
}
