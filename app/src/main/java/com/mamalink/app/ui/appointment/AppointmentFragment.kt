package com.mamalink.app.ui.appointment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
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
import com.mamalink.app.ui.common.RepositoryOperationState
import java.time.*
import java.time.format.DateTimeFormatter

class AppointmentFragment : Fragment(R.layout.fragment_records) {
    private val app get() = requireActivity().application as MamaLinkApplication
    private val vm: AppointmentViewModel by viewModels {
        AppointmentViewModel.Factory(app.appointmentRepository, app.reminderScheduler)
    }
    private val caregiversVm: CaregiverViewModel by viewModels { CaregiverViewModel.Factory(app.caregiverRepository) }
    private val childrenVm: ChildViewModel by viewModels { ChildViewModel.Factory(app.childRepository) }
    private var caregivers: List<Caregiver> = emptyList()
    private var children: List<Child> = emptyList()
    private var selectedCaregiverId = 0L
    private var appointments: List<Appointment> = emptyList()
    private var notificationAppointment: Appointment? = null
    private lateinit var recordsObservation: CaregiverRecordsObservation<Appointment>
    private lateinit var childrenObservation: CaregiverRecordsObservation<Child>
    private var binding: FragmentRecordsBinding? = null
    private val dateTimeFormat = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")

    private lateinit var dialogs: ViewDialogs

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(requireContext(), R.string.notification_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

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
        val notificationAppointmentId = arguments?.getLong(ARG_APPOINTMENT_ID, 0L) ?: 0L
        if (notificationAppointmentId > 0L) {
            vm.observeAppointment(notificationAppointmentId).observe(viewLifecycleOwner) { appointment ->
                notificationAppointment = appointment
                selectNotificationCaregiver()
            }
        }
        childrenObservation = CaregiverRecordsObservation(viewLifecycleOwner, childrenVm::observeChildren) {
            children = it
            render(appointments)
        }
        b.filterSpinner.visibility = View.VISIBLE
        caregiversVm.caregivers.observe(viewLifecycleOwner) { list ->
            caregivers = list
            b.filterSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, list.map { it.fullName })
            if (list.isEmpty()) { b.addButton.isEnabled = false; recordsObservation.clear(); childrenObservation.clear(); selectedCaregiverId = 0; children = emptyList(); appointments = emptyList(); render(emptyList()) }
            else { b.addButton.isEnabled = true; val index = list.indexOfFirst { it.id == selectedCaregiverId }.coerceAtLeast(0); b.filterSpinner.setSelection(index); observeSelected(list[index].id); selectNotificationCaregiver() }
        }
        b.filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) { caregivers.getOrNull(position)?.let { observeSelected(it.id) } }
        }
        b.addButton.setOnClickListener {
            checkNotificationPermission()
            showEditor(null)
        }
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

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun selectNotificationCaregiver() {
        val appointment = notificationAppointment ?: return
        val index = caregivers.indexOfFirst { it.id == appointment.caregiverId }
        val b = binding ?: return
        if (index < 0) return
        notificationAppointment = null
        arguments?.putLong(ARG_APPOINTMENT_ID, 0L)
        b.filterSpinner.setSelection(index)
        observeSelected(appointment.caregiverId)
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
        AppointmentEditorDialogFragment.newInstance(existing, selectedCaregiverId)
            .show(childFragmentManager, AppointmentEditorDialogFragment.TAG)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("caregiverId", selectedCaregiverId)
    }

    override fun onDestroyView() {
        if (::recordsObservation.isInitialized) recordsObservation.clear()
        if (::childrenObservation.isInitialized) childrenObservation.clear()
        binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_APPOINTMENT_ID = "appointmentId"
    }
}
