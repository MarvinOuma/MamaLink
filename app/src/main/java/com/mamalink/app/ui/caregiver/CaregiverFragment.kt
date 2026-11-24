package com.mamalink.app.ui.caregiver

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.button.MaterialButton
import com.mamalink.app.MamaLinkApplication
import com.mamalink.app.R
import com.mamalink.app.databinding.FragmentRecordsBinding
import com.mamalink.app.domain.model.Caregiver
import com.mamalink.app.ui.common.ViewDialogs
import com.mamalink.app.ui.common.RepositoryOperationState

class CaregiverFragment : Fragment(R.layout.fragment_records) {
    private val vm: CaregiverViewModel by viewModels {
        CaregiverViewModel.Factory((requireActivity().application as MamaLinkApplication).caregiverRepository)
    }
    private lateinit var dialogs: ViewDialogs

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dialogs = ViewDialogs(viewLifecycleOwner)
        val b = FragmentRecordsBinding.bind(view)
        b.placeholderTitle.text = getString(R.string.caregiver_title)
        b.screenIntro.text = getString(R.string.caregiver_intro)
        b.addButton.text = getString(R.string.add_caregiver)
        b.addButton.setOnClickListener { showEditor(null) }
        vm.caregivers.observe(viewLifecycleOwner) { people ->
            b.records.removeAllViews(); b.empty.visibility = if (people.isEmpty()) View.VISIBLE else View.GONE
            people.forEach { person ->
                val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 12, 16, 12) }
                row.addView(TextView(requireContext()).apply { text = person.fullName; textSize = 18f; contentDescription = person.fullName })
                if (!person.phoneNumber.isNullOrBlank()) row.addView(TextView(requireContext()).apply { text = person.phoneNumber })
                val actions = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
                actions.addView(MaterialButton(requireContext()).apply { text = getString(R.string.edit); setOnClickListener { showEditor(person) } })
                actions.addView(MaterialButton(requireContext()).apply { text = getString(R.string.delete); setOnClickListener { confirmDelete(person) } })
                row.addView(actions); b.records.addView(row)
            }
        }
        vm.operationState.observe(viewLifecycleOwner) { state ->
            b.loading.visibility = if (state.inProgress) View.VISIBLE else View.GONE
            b.status.text = state.error ?: when (state.completedOperation) {
                RepositoryOperationState.Operation.INSERT -> getString(R.string.saved)
                RepositoryOperationState.Operation.UPDATE -> getString(R.string.updated)
                RepositoryOperationState.Operation.DELETE -> getString(R.string.deleted)
                else -> ""
            }
        }
    }

    private fun showEditor(existing: Caregiver?) {
        CaregiverEditorDialogFragment.newInstance(existing)
            .show(childFragmentManager, CaregiverEditorDialogFragment.TAG)
    }

    private fun confirmDelete(person: Caregiver) {
        dialogs.show(AlertDialog.Builder(requireContext()).setTitle(R.string.delete_caregiver)
            .setMessage(getString(R.string.confirm_delete_named, person.fullName)).setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> vm.deleteCaregiver(person.id) }.create())
    }
}
