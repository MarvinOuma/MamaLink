package com.mamalink.app.ui.child

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.button.MaterialButton
import com.mamalink.app.MamaLinkApplication
import com.mamalink.app.R
import com.mamalink.app.databinding.FragmentRecordsBinding
import com.mamalink.app.domain.model.Caregiver
import com.mamalink.app.domain.model.Child
import com.mamalink.app.ui.caregiver.CaregiverViewModel
import com.mamalink.app.ui.common.CaregiverRecordsObservation
import com.mamalink.app.ui.common.ViewDialogs
import com.mamalink.app.ui.common.RepositoryOperationState
import java.time.format.DateTimeFormatter

class ChildFragment : Fragment(R.layout.fragment_records) {
    private val app get() = requireActivity().application as MamaLinkApplication
    private val vm: ChildViewModel by viewModels { ChildViewModel.Factory(app.childRepository) }
    private val caregiversVm: CaregiverViewModel by viewModels { CaregiverViewModel.Factory(app.caregiverRepository) }
    private var caregivers: List<Caregiver> = emptyList()
    private var selectedCaregiverId = 0L
    private lateinit var recordsObservation: CaregiverRecordsObservation<Child>
    private var binding: FragmentRecordsBinding? = null
    private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")

    private lateinit var dialogs: ViewDialogs

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dialogs = ViewDialogs(viewLifecycleOwner)
        val b = FragmentRecordsBinding.bind(view); binding = b
        b.placeholderTitle.text = getString(R.string.child_title)
        b.screenIntro.text = getString(R.string.child_intro); b.addButton.text = getString(R.string.add_child)
        selectedCaregiverId = savedInstanceState?.getLong("caregiverId") ?: selectedCaregiverId
        recordsObservation = CaregiverRecordsObservation(viewLifecycleOwner, vm::observeChildren, ::render)
        b.filterSpinner.visibility = View.VISIBLE
        caregiversVm.caregivers.observe(viewLifecycleOwner) { list ->
            caregivers = list
            val names = list.map { it.fullName }
            b.filterSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, names)
            if (list.isEmpty()) { recordsObservation.clear(); selectedCaregiverId = 0; b.addButton.isEnabled = false; render(emptyList()); return@observe }
            b.addButton.isEnabled = true
            val wanted = list.indexOfFirst { it.id == selectedCaregiverId }.let { if (it < 0) 0 else it }
            b.filterSpinner.setSelection(wanted)
            observeSelected(list[wanted].id)
        }
        b.filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) { caregivers.getOrNull(position)?.let { observeSelected(it.id) } }
        }
        b.addButton.setOnClickListener { showEditor(null) }
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
    private fun observeSelected(id: Long) {
        if (selectedCaregiverId != id) render(emptyList())
        selectedCaregiverId = id
        recordsObservation.select(id)
    }
    private fun render(children: List<Child>) {
        val b = binding ?: return
        b.records.removeAllViews(); b.empty.visibility = if (children.isEmpty()) View.VISIBLE else View.GONE
        children.forEach { child ->
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 12, 16, 12) }
            row.addView(TextView(requireContext()).apply { text = child.name; textSize = 18f })
            row.addView(TextView(requireContext()).apply { text = getString(R.string.born_on, child.dateOfBirth.format(dateFormat)) })
            val actions = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            actions.addView(MaterialButton(requireContext()).apply { text = getString(R.string.edit); setOnClickListener { showEditor(child) } })
            actions.addView(MaterialButton(requireContext()).apply { text = getString(R.string.delete); setOnClickListener {
                dialogs.show(AlertDialog.Builder(requireContext()).setTitle(R.string.delete_child).setMessage(getString(R.string.confirm_delete_named, child.name))
                    .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete) { _, _ -> vm.deleteChild(child.id) }.create())
            } })
            row.addView(actions); b.records.addView(row)
        }
    }
    private fun showEditor(existing: Child?) {
        if (caregivers.isEmpty()) return
        ChildEditorDialogFragment.newInstance(existing, selectedCaregiverId)
            .show(childFragmentManager, ChildEditorDialogFragment.TAG)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("caregiverId", selectedCaregiverId)
    }
    override fun onDestroyView() {
        if (::recordsObservation.isInitialized) recordsObservation.clear()
        binding = null
        super.onDestroyView()
    }
}
