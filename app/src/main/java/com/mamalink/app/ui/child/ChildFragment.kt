package com.mamalink.app.ui.child

import android.app.DatePickerDialog
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
import com.mamalink.app.ui.common.RepositoryOperationState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class ChildFragment : Fragment(R.layout.fragment_records) {
    private val app get() = requireActivity().application as MamaLinkApplication
    private val vm: ChildViewModel by viewModels { ChildViewModel.Factory(app.childRepository) }
    private val caregiversVm: CaregiverViewModel by viewModels { CaregiverViewModel.Factory(app.caregiverRepository) }
    private var caregivers: List<Caregiver> = emptyList()
    private var selectedCaregiverId = 0L
    private var binding: FragmentRecordsBinding? = null
    private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentRecordsBinding.bind(view); binding = b
        b.screenIntro.text = getString(R.string.child_intro); b.addButton.text = getString(R.string.add_child)
        b.filterSpinner.visibility = View.VISIBLE
        caregiversVm.caregivers.observe(viewLifecycleOwner) { list ->
            caregivers = list
            val names = list.map { it.fullName }
            b.filterSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, names)
            if (list.isEmpty()) { selectedCaregiverId = 0; b.addButton.isEnabled = false; render(emptyList()); return@observe }
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
        selectedCaregiverId = id
        vm.observeChildren(id).observe(viewLifecycleOwner) { children -> if (selectedCaregiverId == id) render(children) }
    }
    private fun render(children: List<Child>) {
        val b = binding ?: return
        b.records.removeAllViews(); b.empty.visibility = if (children.isEmpty()) View.VISIBLE else View.GONE
        children.forEach { child ->
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 12, 16, 12) }
            row.addView(TextView(requireContext()).apply { text = child.name; textSize = 18f })
            row.addView(TextView(requireContext()).apply { text = getString(R.string.born_on, child.dateOfBirth.format(dateFormat)) })
            val actions = LinearLayout(requireContext())
            actions.addView(MaterialButton(requireContext()).apply { text = getString(R.string.edit); setOnClickListener { showEditor(child) } })
            actions.addView(MaterialButton(requireContext()).apply { text = getString(R.string.delete); setOnClickListener {
                AlertDialog.Builder(requireContext()).setTitle(R.string.delete_child).setMessage(getString(R.string.confirm_delete_named, child.name))
                    .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete) { _, _ -> vm.deleteChild(child.id) }.show()
            } })
            row.addView(actions); b.records.addView(row)
        }
    }
    private fun showEditor(existing: Child?) {
        if (caregivers.isEmpty()) return
        val form = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0) }
        val name = EditText(requireContext()).apply { hint = getString(R.string.child_name); setText(existing?.name); contentDescription = hint }
        val caregiverPicker = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, caregivers.map { it.fullName })
            setSelection(caregivers.indexOfFirst { it.id == (existing?.caregiverId ?: selectedCaregiverId) }.coerceAtLeast(0))
            contentDescription = getString(R.string.select_caregiver)
        }
        val dob = Button(requireContext()); var chosenDate = existing?.dateOfBirth ?: LocalDate.now()
        fun updateDate() { dob.text = chosenDate.format(dateFormat) }; updateDate()
        dob.setOnClickListener {
            val today = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, year, month, day -> chosenDate = LocalDate.of(year, month + 1, day); updateDate() }, chosenDate.year, chosenDate.monthValue - 1, chosenDate.dayOfMonth)
                .apply { datePicker.maxDate = today.timeInMillis }.show()
        }
        form.addView(name); form.addView(TextView(requireContext()).apply { text = getString(R.string.caregiver) }); form.addView(caregiverPicker)
        form.addView(TextView(requireContext()).apply { text = getString(R.string.date_of_birth) }); form.addView(dob)
        val dialog = AlertDialog.Builder(requireContext()).setTitle(if (existing == null) R.string.add_child else R.string.edit_child)
            .setView(form).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (name.text.isNullOrBlank()) { name.error = getString(R.string.name_required); return@setOnClickListener }
            val record = Child(existing?.id ?: 0, caregivers[caregiverPicker.selectedItemPosition].id, name.text.toString().trim(), chosenDate)
            if (existing == null) vm.addChild(record) else vm.updateChild(record)
            dialog.dismiss()
        } }; dialog.show()
    }
    override fun onDestroyView() { binding = null; super.onDestroyView() }
}

