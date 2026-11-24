package com.mamalink.app.ui.appointment

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import com.mamalink.app.domain.model.Appointment
import com.mamalink.app.domain.model.Caregiver
import com.mamalink.app.domain.model.Child
import com.mamalink.app.ui.caregiver.CaregiverViewModel
import com.mamalink.app.ui.child.ChildViewModel
import com.mamalink.app.ui.common.CaregiverRecordsObservation
import com.mamalink.app.ui.common.ViewDialogs
import com.mamalink.app.ui.common.recordForm
import com.mamalink.app.ui.common.scrollable
import com.mamalink.app.ui.common.RepositoryOperationState
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Calendar

class AppointmentFragment : Fragment(R.layout.fragment_records) {
    private val app get() = requireActivity().application as MamaLinkApplication
    private val vm: AppointmentViewModel by viewModels { AppointmentViewModel.Factory(app.appointmentRepository) }
    private val caregiversVm: CaregiverViewModel by viewModels { CaregiverViewModel.Factory(app.caregiverRepository) }
    private val childrenVm: ChildViewModel by viewModels { ChildViewModel.Factory(app.childRepository) }
    private var caregivers: List<Caregiver> = emptyList()
    private var children: List<Child> = emptyList()
    private var selectedCaregiverId = 0L
    private var appointments: List<Appointment> = emptyList()
    private lateinit var recordsObservation: CaregiverRecordsObservation<Appointment>
    private lateinit var childrenObservation: CaregiverRecordsObservation<Child>
    private var binding: FragmentRecordsBinding? = null
    private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val dateTimeFormat = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")

    private lateinit var dialogs: ViewDialogs

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dialogs = ViewDialogs(viewLifecycleOwner)
        val b = FragmentRecordsBinding.bind(view); binding = b
        b.placeholderTitle.text = getString(R.string.appointments_title)
        b.screenIntro.text = getString(R.string.appointment_intro); b.addButton.text = getString(R.string.add_appointment)
        selectedCaregiverId = savedInstanceState?.getLong("caregiverId") ?: selectedCaregiverId
        recordsObservation = CaregiverRecordsObservation(viewLifecycleOwner, vm::observeAppointments) {
            appointments = it
            render(it)
        }
        childrenObservation = CaregiverRecordsObservation(viewLifecycleOwner, childrenVm::observeChildren) {
            children = it
            render(appointments)
        }
        b.filterSpinner.visibility = View.VISIBLE
        caregiversVm.caregivers.observe(viewLifecycleOwner) { list ->
            caregivers = list; b.filterSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, list.map { it.fullName })
            if (list.isEmpty()) { b.addButton.isEnabled = false; recordsObservation.clear(); childrenObservation.clear(); selectedCaregiverId = 0; children = emptyList(); appointments = emptyList(); render(emptyList()) }
            else { b.addButton.isEnabled = true; val index = list.indexOfFirst { it.id == selectedCaregiverId }.coerceAtLeast(0); b.filterSpinner.setSelection(index); observeSelected(list[index].id) }
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
                RepositoryOperationState.Operation.DELETE -> getString(R.string.cancelled)
                else -> ""
            }
        }
    }
    private fun observeSelected(id: Long) {
        if (selectedCaregiverId != id) {
            children = emptyList()
            appointments = emptyList()
            render(emptyList())
        }
        selectedCaregiverId = id
        childrenObservation.select(id)
        recordsObservation.select(id)
    }
    private fun render(items: List<Appointment>) {
        val b = binding ?: return
        b.records.removeAllViews(); b.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        items.sortedBy { it.scheduledAt }.forEach { appointment ->
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 12, 16, 12) }
            row.addView(TextView(requireContext()).apply { text = appointment.title; textSize = 18f })
            val local = LocalDateTime.ofInstant(appointment.scheduledAt, ZoneId.systemDefault())
            row.addView(TextView(requireContext()).apply { text = local.format(dateTimeFormat) })
            appointment.location?.takeIf { it.isNotBlank() }?.let { location -> row.addView(TextView(requireContext()).apply { text = location }) }
            caregivers.firstOrNull { it.id == appointment.caregiverId }?.let { caregiver -> row.addView(TextView(requireContext()).apply { text = getString(R.string.with_caregiver, caregiver.fullName) }) }
            appointment.childId?.let { childId -> children.firstOrNull { it.id == childId }?.let { child -> row.addView(TextView(requireContext()).apply { text = getString(R.string.for_child, child.name) }) } }
            val actions = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            actions.addView(MaterialButton(requireContext()).apply { text = getString(R.string.edit); setOnClickListener { showEditor(appointment) } })
            actions.addView(MaterialButton(requireContext()).apply { text = getString(R.string.cancel_appointment); setOnClickListener {
                dialogs.show(AlertDialog.Builder(requireContext()).setTitle(R.string.cancel_appointment).setMessage(getString(R.string.confirm_delete_named, appointment.title))
                    .setNegativeButton(R.string.keep, null).setPositiveButton(R.string.cancel_appointment) { _, _ -> vm.deleteAppointment(appointment.id) }.create())
            } })
            row.addView(actions); b.records.addView(row)
        }
    }
    private fun showEditor(existing: Appointment?) {
        if (caregivers.isEmpty()) return
        val editorCaregivers = caregivers.toList()
        val form = requireContext().recordForm()
        val title = EditText(requireContext()).apply { hint = getString(R.string.appointment_title_hint); setText(existing?.title); contentDescription = hint }
        val caregiverPicker = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, editorCaregivers.map { it.fullName })
            setSelection(editorCaregivers.indexOfFirst { it.id == (existing?.caregiverId ?: selectedCaregiverId) }.coerceAtLeast(0)); contentDescription = getString(R.string.select_caregiver)
        }
        val childPicker = Spinner(requireContext()).apply { contentDescription = getString(R.string.child_optional) }
        var availableChildren: List<Child> = emptyList()
        var editorCaregiverId: Long? = null
        var preferredChildId = existing?.childId
        var childrenLoaded = false
        var editorDialog: AlertDialog? = null
        val childObservation = CaregiverRecordsObservation(viewLifecycleOwner, childrenVm::observeChildren) { list ->
            val selectedChild = if (childrenLoaded) availableChildren.getOrNull(childPicker.selectedItemPosition - 1)?.id else preferredChildId
            availableChildren = list
            preferredChildId = selectedChild?.takeIf { id -> list.any { it.id == id } }
            childPicker.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                listOf(getString(R.string.no_child)) + list.map { it.name })
            childPicker.setSelection(list.indexOfFirst { it.id == preferredChildId } + 1)
            childrenLoaded = true
            childPicker.isEnabled = true
            editorDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
        }
        fun updateChildren(caregiverId: Long) {
            if (editorCaregiverId == caregiverId) return
            if (editorCaregiverId != null) preferredChildId = null
            editorCaregiverId = caregiverId
            availableChildren = emptyList()
            childrenLoaded = false
            childPicker.isEnabled = false
            editorDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
            childPicker.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf(getString(R.string.no_child)))
            childObservation.select(caregiverId)
        }
        updateChildren(editorCaregivers[caregiverPicker.selectedItemPosition].id)
        caregiverPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                editorCaregivers.getOrNull(position)?.let { updateChildren(it.id) }
            }
        }
        val initial = existing?.scheduledAt?.let { LocalDateTime.ofInstant(it, ZoneId.systemDefault()) } ?: LocalDateTime.now().plusDays(1).withSecond(0).withNano(0)
        var chosen = initial
        val dateButton = Button(requireContext()); val timeButton = Button(requireContext())
        fun refresh() { dateButton.text = chosen.format(dateFormat); timeButton.text = chosen.format(DateTimeFormatter.ofPattern("h:mm a")) }
        refresh()
        dateButton.setOnClickListener {
            val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            dialogs.show(DatePickerDialog(requireContext(), { _, year, month, day -> chosen = chosen.withDate(year, month + 1, day); refresh() }, chosen.year, chosen.monthValue - 1, chosen.dayOfMonth)
                .apply { datePicker.minDate = if (existing == null) today.timeInMillis else 0 })
        }
        timeButton.setOnClickListener { dialogs.show(TimePickerDialog(requireContext(), { _, hour, minute -> chosen = chosen.withHour(hour).withMinute(minute); refresh() }, chosen.hour, chosen.minute, false)) }
        val location = EditText(requireContext()).apply { hint = getString(R.string.location_optional); setText(existing?.location); contentDescription = hint }
        form.addView(title); form.addView(TextView(requireContext()).apply { text = getString(R.string.caregiver) }); form.addView(caregiverPicker)
        form.addView(TextView(requireContext()).apply { text = getString(R.string.child_optional) }); form.addView(childPicker)
        form.addView(TextView(requireContext()).apply { text = getString(R.string.appointment_date) }); form.addView(dateButton)
        form.addView(TextView(requireContext()).apply { text = getString(R.string.appointment_time) }); form.addView(timeButton); form.addView(location)
        val dialog = AlertDialog.Builder(requireContext()).setTitle(if (existing == null) R.string.add_appointment else R.string.edit_appointment)
            .setView(form.scrollable()).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create()
        editorDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = childrenLoaded
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (title.text.isNullOrBlank()) { title.error = getString(R.string.title_required); return@setOnClickListener }
            if (existing == null && chosen.isBefore(LocalDateTime.now())) { Toast.makeText(requireContext(), R.string.past_appointment, Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (!childrenLoaded) return@setOnClickListener
            val caregiverId = editorCaregivers[caregiverPicker.selectedItemPosition].id
            val childId = availableChildren.getOrNull(childPicker.selectedItemPosition - 1)?.id
            val item = Appointment(existing?.id ?: 0, caregiverId, childId, title.text.toString().trim(), chosen.atZone(ZoneId.systemDefault()).toInstant(), location.text.toString().trim().ifBlank { null })
            if (existing == null) vm.addAppointment(item) else vm.updateAppointment(item)
            dialog.dismiss()
        } }; dialogs.show(dialog) { childObservation.dispose(); editorDialog = null }
    }
    private fun LocalDateTime.withDate(year: Int, month: Int, day: Int) = LocalDateTime.of(LocalDate.of(year, month, day), toLocalTime())
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("caregiverId", selectedCaregiverId)
    }
    override fun onDestroyView() { binding = null; super.onDestroyView() }
}

