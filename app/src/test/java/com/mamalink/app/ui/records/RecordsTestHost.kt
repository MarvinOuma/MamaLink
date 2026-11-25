package com.mamalink.app.ui.records

import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mamalink.app.R
import com.mamalink.app.domain.model.Appointment
import com.mamalink.app.domain.model.Caregiver
import com.mamalink.app.domain.model.Child
import com.mamalink.app.domain.repository.AppointmentRepository
import com.mamalink.app.domain.repository.CaregiverRepository
import com.mamalink.app.domain.repository.ChildRepository
import com.mamalink.app.ui.appointment.AppointmentFragment
import com.mamalink.app.ui.appointment.AppointmentViewModel
import com.mamalink.app.ui.caregiver.CaregiverFragment
import com.mamalink.app.ui.caregiver.CaregiverViewModel
import com.mamalink.app.ui.child.ChildFragment
import com.mamalink.app.ui.child.ChildViewModel
import kotlinx.coroutines.CompletableDeferred

/** Real Fragments and ViewModels, with controllable repository boundaries only. */
class RecordsTestHost : AppCompatActivity() {
    companion object {
        lateinit var data: ScreenData
        var screen: Class<out Fragment> = CaregiverFragment::class.java
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_MamaLink)
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentPreCreated(fm: FragmentManager, f: Fragment, state: Bundle?) {
                val factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        val vm = when (modelClass) {
                            CaregiverViewModel::class.java -> CaregiverViewModel(data.caregiverRepository)
                            ChildViewModel::class.java -> ChildViewModel(data.childRepository)
                            AppointmentViewModel::class.java -> AppointmentViewModel(data.appointmentRepository)
                            else -> error("Unexpected ViewModel")
                        }
                        @Suppress("UNCHECKED_CAST")
                        return vm as T
                    }
                }
                val provider = ViewModelProvider(f, factory)
                if (f is CaregiverFragment || f is ChildFragment || f is AppointmentFragment) provider[CaregiverViewModel::class.java]
                if (f is ChildFragment || f is AppointmentFragment) provider[ChildViewModel::class.java]
                if (f is AppointmentFragment) provider[AppointmentViewModel::class.java]
            }
        }, false)
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply { id = R.id.nav_host_fragment })
        if (savedInstanceState == null) supportFragmentManager.beginTransaction()
            .add(R.id.nav_host_fragment, screen.getDeclaredConstructor().newInstance()).commitNow()
    }
}

class TrackedRecords<T>(initial: List<T>? = emptyList()) : MutableLiveData<List<T>>() {
    init { if (initial != null) value = initial }
    private val listeners = mutableSetOf<Observer<in List<T>>>()
    val observerCount get() = listeners.size
    override fun observe(owner: LifecycleOwner, observer: Observer<in List<T>>) {
        super.observe(owner, observer)
        listeners.add(observer)
    }
    override fun removeObserver(observer: Observer<in List<T>>) {
        super.removeObserver(observer)
        listeners.remove(observer)
    }
}

class ScreenData {
    val caregivers = TrackedRecords<Caregiver>()
    val children = mutableMapOf<Long, TrackedRecords<Child>>()
    val appointments = mutableMapOf<Long, TrackedRecords<Appointment>>()
    fun children(id: Long) = children.getOrPut(id) { TrackedRecords() }
    fun appointments(id: Long) = appointments.getOrPut(id) { TrackedRecords() }
    var failure: Exception? = null
    var gate: CompletableDeferred<Unit>? = null
    var writes = 0
    private var nextId = 100L
    private suspend fun write() { writes++; gate?.await(); failure?.let { throw it } }

    val caregiverRepository = object : CaregiverRepository {
        override fun observeAll() = caregivers
        override fun observeById(id: Long) = MutableLiveData(caregivers.value?.find { it.id == id })
        override suspend fun insert(caregiver: Caregiver): Long {
            write(); val id = nextId++; caregivers.value = caregivers.value.orEmpty() + caregiver.copy(id = id); return id
        }
        override suspend fun update(caregiver: Caregiver): Int {
            write(); caregivers.value = caregivers.value.orEmpty().map { if (it.id == caregiver.id) caregiver else it }; return 1
        }
        override suspend fun deleteById(id: Long): Int {
            write(); caregivers.value = caregivers.value.orEmpty().filterNot { it.id == id }; return 1
        }
    }
    val childRepository = object : ChildRepository {
        override fun observeByCaregiver(caregiverId: Long) = children(caregiverId)
        override fun observeById(id: Long) = MutableLiveData<Child?>(null)
        override suspend fun insert(child: Child): Long {
            write(); val id = nextId++; children(child.caregiverId).value = children(child.caregiverId).value.orEmpty() + child.copy(id = id); return id
        }
        override suspend fun update(child: Child): Int {
            write(); children.values.forEach { it.value = it.value.orEmpty().filterNot { old -> old.id == child.id } }
            children(child.caregiverId).value = children(child.caregiverId).value.orEmpty() + child; return 1
        }
        override suspend fun deleteById(id: Long): Int {
            write(); children.values.forEach { it.value = it.value.orEmpty().filterNot { child -> child.id == id } }; return 1
        }
    }
    val appointmentRepository = object : AppointmentRepository {
        override fun observeByCaregiver(caregiverId: Long) = appointments(caregiverId)
        override fun observeById(id: Long) = MutableLiveData<Appointment?>(null)
        override suspend fun getById(id: Long) = appointments.values.asSequence().flatMap { it.value.orEmpty().asSequence() }.firstOrNull { it.id == id }
        override suspend fun getAll() = appointments.values.flatMap { it.value.orEmpty() }.distinctBy { it.id }.sortedBy { it.scheduledAt }
        override suspend fun insert(appointment: Appointment): Long {
            write(); val id = nextId++; appointments(appointment.caregiverId).value = appointments(appointment.caregiverId).value.orEmpty() + appointment.copy(id = id); return id
        }
        override suspend fun update(appointment: Appointment): Int {
            write(); appointments.values.forEach { it.value = it.value.orEmpty().filterNot { old -> old.id == appointment.id } }
            appointments(appointment.caregiverId).value = appointments(appointment.caregiverId).value.orEmpty() + appointment; return 1
        }
        override suspend fun deleteById(id: Long): Int {
            write(); appointments.values.forEach { it.value = it.value.orEmpty().filterNot { item -> item.id == id } }; return 1
        }
    }
}
