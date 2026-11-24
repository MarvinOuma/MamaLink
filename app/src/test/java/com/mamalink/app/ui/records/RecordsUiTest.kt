package com.mamalink.app.ui.records

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.mamalink.app.R
import com.mamalink.app.domain.model.Appointment
import com.mamalink.app.domain.model.Caregiver
import com.mamalink.app.domain.model.Child
import com.mamalink.app.ui.appointment.AppointmentFragment
import com.mamalink.app.ui.caregiver.CaregiverFragment
import com.mamalink.app.ui.child.ChildFragment
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35])
class RecordsUiTest {
    private lateinit var data: ScreenData
    private val demoChild = Child(11, 1, "Demo child A", LocalDate.of(2024, 2, 29))
    private val demoVisit get() = Appointment(21, 1, 11, "Demo visit", LocalDateTime.now().plusDays(2).withSecond(0).withNano(0).atZone(ZoneId.systemDefault()).toInstant(), "Demo room")

    @Before fun prepare() {
        data = ScreenData()
        RecordsTestHost.data = data
    }
    private fun seed() {
        data.caregivers.value = listOf(Caregiver(1, "Demo caregiver A"), Caregiver(2, "Demo caregiver B"))
        data.children(1).value = listOf(demoChild)
        data.children(2).value = listOf(demoChild.copy(id = 12, caregiverId = 2, name = "Demo child B"))
    }
    private fun launch(screen: Class<out Fragment>): ActivityController<RecordsTestHost> {
        RecordsTestHost.screen = screen
        return Robolectric.buildActivity(RecordsTestHost::class.java).setup().visible().also { idle() }
    }
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun root(activity: RecordsTestHost): View = activity.findViewById(android.R.id.content)
    private fun dialog() = (ShadowDialog.getLatestDialog() as AlertDialog).also { assertTrue(it.isShowing) }
    private fun dialogViews(dialog: AlertDialog) = views(dialog.window!!.decorView)
    private fun field(dialog: AlertDialog, label: Int) = dialogViews(dialog).filterIsInstance<EditText>().single { it.contentDescription == dialog.context.getString(label) }
    private fun picker(dialog: AlertDialog, label: Int) = dialogViews(dialog).filterIsInstance<Spinner>().single { it.contentDescription == dialog.context.getString(label) }
    private fun save(dialog: AlertDialog) { dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle() }
    private fun cancel(dialog: AlertDialog) { dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); idle() }
    private fun add(activity: RecordsTestHost): AlertDialog { activity.findViewById<View>(R.id.add_button).performClick(); idle(); return dialog() }
    private fun action(activity: RecordsTestHost, label: Int) {
        views(activity.findViewById(R.id.records)).filterIsInstance<Button>().first { it.text == activity.getString(label) }.performClick(); idle()
    }
    private fun select(spinner: Spinner, position: Int) {
        spinner.setSelection(position)
        spinner.onItemSelectedListener?.onItemSelected(spinner, null, position, position.toLong())
        idle()
    }
    private fun filter(activity: RecordsTestHost, index: Int) = select(activity.findViewById(R.id.filter_spinner), index)
    private fun hasText(activity: RecordsTestHost, text: String) = views(root(activity)).filterIsInstance<TextView>().any { it.text.toString() == text }

    @Test fun `caregiver form validates trims saves edits confirms and deletes`() {
        launch(CaregiverFragment::class.java).use { controller ->
            val activity = controller.get()
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.empty).visibility)
            val editor = add(activity)
            field(editor, R.string.full_name).setText("   ")
            save(editor)
            assertTrue(editor.isShowing); assertEquals(0, data.writes)
            assertEquals(activity.getString(R.string.name_required), field(editor, R.string.full_name).error.toString())
            field(editor, R.string.full_name).setText("  Demo caregiver  ")
            save(editor)
            assertFalse(editor.isShowing); assertTrue(hasText(activity, "Demo caregiver"))
            assertNull(data.caregivers.value!!.single().phoneNumber)
            action(activity, R.string.edit)
            val edit = dialog()
            assertEquals("Demo caregiver", field(edit, R.string.full_name).text.toString())
            field(edit, R.string.full_name).setText("Updated demo")
            field(edit, R.string.phone_optional).setText(" 0700000000 ")
            save(edit)
            assertTrue(hasText(activity, "Updated demo")); assertTrue(hasText(activity, "0700000000"))
            action(activity, R.string.delete); cancel(dialog())
            assertEquals(1, data.caregivers.value!!.size)
            action(activity, R.string.delete); save(dialog())
            assertTrue(data.caregivers.value!!.isEmpty())
            assertEquals(activity.getString(R.string.deleted), activity.findViewById<TextView>(R.id.status).text.toString())
        }
    }

    @Test fun `repository loading and private failure are shown without crashing or exposing details`() {
        seed()
        launch(CaregiverFragment::class.java).use { controller ->
            val activity = controller.get()
            data.gate = CompletableDeferred()
            data.failure = IllegalStateException("private database and record details")
            action(activity, R.string.delete); save(dialog())
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.loading).visibility)
            data.gate!!.complete(Unit); idle()
            assertEquals(View.GONE, activity.findViewById<View>(R.id.loading).visibility)
            val status = activity.findViewById<TextView>(R.id.status).text.toString()
            assertTrue(status.contains("Unable to change caregiver")); assertFalse(status.contains("private"))
            assertEquals(2, data.caregivers.value!!.size)
            data.failure = null; data.gate = null
            action(activity, R.string.delete); save(dialog())
            assertEquals(1, data.caregivers.value!!.size)
        }
    }

    @Test fun `child and appointment creation require an existing caregiver`() {
        listOf(ChildFragment::class.java, AppointmentFragment::class.java).forEach { screen ->
            launch(screen).use { controller ->
                assertFalse(controller.get().findViewById<View>(R.id.add_button).isEnabled)
                data.caregivers.value = listOf(Caregiver(1, "Demo caregiver")); idle()
                assertTrue(controller.get().findViewById<View>(R.id.add_button).isEnabled)
                data.caregivers.value = emptyList(); idle()
                assertFalse(controller.get().findViewById<View>(R.id.add_button).isEnabled)
                assertEquals(0, data.children(1).observerCount)
                assertEquals(0, data.appointments(1).observerCount)
            }
        }
    }

    @Test fun `child form validates birth date caregiver selection and CRUD`() {
        seed(); data.children(1).value = emptyList()
        launch(ChildFragment::class.java).use { controller ->
            val activity = controller.get(); val editor = add(activity)
            save(editor); assertTrue(editor.isShowing); assertEquals(0, data.writes)
            field(editor, R.string.child_name).setText(" Demo baby ")
            val dobButton = dialogViews(editor).filterIsInstance<Button>().first { it.parent is LinearLayout && it.text.toString().contains(LocalDate.now().year.toString()) }
            dobButton.performClick(); idle()
            val dateDialog = ShadowDialog.getLatestDialog() as DatePickerDialog
            assertTrue(dateDialog.datePicker.maxDate <= System.currentTimeMillis())
            dateDialog.datePicker.updateDate(2024, 1, 29)
            dateDialog.getButton(DatePickerDialog.BUTTON_POSITIVE).performClick(); idle()
            select(picker(editor, R.string.select_caregiver), 1)
            save(editor)
            val child = data.children(2).value!!.single { it.name == "Demo baby" }
            assertEquals(LocalDate.of(2024, 2, 29), child.dateOfBirth); assertEquals(2L, child.caregiverId)
            filter(activity, 1); assertTrue(hasText(activity, "Demo baby"))
            // Use the first existing record for edit/delete; both operations must flow through the ViewModel.
            action(activity, R.string.edit); val edit = dialog()
            field(edit, R.string.child_name).setText("Updated demo child"); save(edit)
            assertTrue(hasText(activity, "Updated demo child"))
            action(activity, R.string.delete); cancel(dialog()); assertEquals(2, data.children(2).value!!.size)
            action(activity, R.string.delete); save(dialog()); assertEquals(1, data.children(2).value!!.size)
        }
    }

    @Test fun `switching child filters replaces observers clears pending rows and ignores stale data`() {
        seed()
        launch(ChildFragment::class.java).use { controller ->
            val activity = controller.get()
            assertEquals(1, data.children(1).observerCount)
            repeat(5) { filter(activity, 1); filter(activity, 0) }
            filter(activity, 1)
            assertEquals(0, data.children(1).observerCount); assertEquals(1, data.children(2).observerCount)
            data.children(1).value = listOf(demoChild.copy(name = "Stale private row")); idle()
            assertFalse(hasText(activity, "Stale private row")); assertTrue(hasText(activity, "Demo child B"))
            // Simulate a third caregiver whose query has not returned yet.
            data.children[3] = TrackedRecords(null)
            data.caregivers.value = data.caregivers.value!! + Caregiver(3, "Demo caregiver C"); idle()
            filter(activity, 2)
            assertEquals(0, activity.findViewById<LinearLayout>(R.id.records).childCount)
            data.caregivers.value = emptyList(); idle()
            assertTrue(data.children.values.all { it.observerCount == 0 })
        }
        assertTrue(data.children.values.all { it.observerCount == 0 })
    }

    @Test fun `selected child filter and real records survive recreation`() {
        seed()
        launch(ChildFragment::class.java).use { controller ->
            filter(controller.get(), 1)
            val editor = add(controller.get())
            controller.recreate(); idle()
            assertFalse(editor.isShowing)
            assertEquals(1, controller.get().findViewById<Spinner>(R.id.filter_spinner).selectedItemPosition)
            assertTrue(hasText(controller.get(), "Demo child B"))
            assertEquals(0, data.children(1).observerCount); assertEquals(1, data.children(2).observerCount)
        }
        assertTrue(data.children.values.all { it.observerCount == 0 })
    }

    @Test fun `appointment form validates title converts local time saves edits and cancels`() {
        seed()
        launch(AppointmentFragment::class.java).use { controller ->
            val activity = controller.get(); val editor = add(activity)
            save(editor); assertTrue(editor.isShowing); assertEquals(0, data.writes)
            field(editor, R.string.appointment_title_hint).setText(" Demo visit ")
            field(editor, R.string.location_optional).setText(" Demo room ")
            select(picker(editor, R.string.child_optional), 1)
            val expectedDay = LocalDate.now().plusDays(1)
            save(editor)
            val saved = data.appointments(1).value!!.single()
            assertEquals("Demo visit", saved.title); assertEquals(11L, saved.childId); assertEquals("Demo room", saved.location)
            assertEquals(expectedDay, saved.scheduledAt.atZone(ZoneId.systemDefault()).toLocalDate())
            assertTrue(hasText(activity, "Child: Demo child A"))
            action(activity, R.string.edit); val edit = dialog()
            assertEquals(1, picker(edit, R.string.child_optional).selectedItemPosition)
            field(edit, R.string.appointment_title_hint).setText("Updated demo visit"); save(edit)
            assertEquals(saved.scheduledAt, data.appointments(1).value!!.single().scheduledAt)
            assertEquals(saved.childId, data.appointments(1).value!!.single().childId)
            action(activity, R.string.cancel_appointment); cancel(dialog()); assertEquals(1, data.appointments(1).value!!.size)
            action(activity, R.string.cancel_appointment); save(dialog()); assertTrue(data.appointments(1).value!!.isEmpty())
        }
    }

    @Test fun `appointment rejects earlier time today without dismissing the form`() {
        seed()
        launch(AppointmentFragment::class.java).use { controller ->
            val editor = add(controller.get())
            field(editor, R.string.appointment_title_hint).setText("Demo visit")
            val buttons = dialogViews(editor).filterIsInstance<Button>().filter { it.parent is LinearLayout && it.id == View.NO_ID }
            buttons[0].performClick(); idle()
            val date = ShadowDialog.getLatestDialog() as DatePickerDialog
            val today = LocalDate.now(); date.datePicker.updateDate(today.year, today.monthValue - 1, today.dayOfMonth)
            date.getButton(DatePickerDialog.BUTTON_POSITIVE).performClick(); idle()
            buttons[1].performClick(); idle()
            val time = ShadowDialog.getLatestDialog() as TimePickerDialog
            val timePicker = views(time.window!!.decorView).filterIsInstance<TimePicker>().single()
            timePicker.hour = 0; timePicker.minute = 0
            time.getButton(TimePickerDialog.BUTTON_POSITIVE).performClick(); idle()
            save(editor)
            assertTrue(editor.isShowing); assertEquals(0, data.writes)
            assertEquals(controller.get().getString(R.string.past_appointment), ShadowToast.getTextOfLatestToast())
        }
    }

    @Test fun `appointment dialog replaces child observers and dismissal releases them`() {
        seed(); data.appointments(1).value = listOf(demoVisit)
        launch(AppointmentFragment::class.java).use { controller ->
            val activity = controller.get()
            repeat(5) { filter(activity, 1); filter(activity, 0) }
            assertEquals(1, data.children(1).observerCount); assertEquals(1, data.appointments(1).observerCount)
            assertEquals(0, data.children(2).observerCount); assertEquals(0, data.appointments(2).observerCount)
            repeat(3) {
                action(activity, R.string.edit); val editor = dialog()
                assertEquals(1, picker(editor, R.string.child_optional).selectedItemPosition)
                assertEquals(2, data.children(1).observerCount)
                val caregiver = picker(editor, R.string.select_caregiver)
                select(caregiver, 1)
                assertEquals(1, data.children(1).observerCount); assertEquals(1, data.children(2).observerCount)
                assertEquals(0, picker(editor, R.string.child_optional).selectedItemPosition)
                data.children(1).value = listOf(demoChild.copy(name = "Old caregiver update")); idle()
                assertEquals("Demo child B", picker(editor, R.string.child_optional).adapter.getItem(1))
                val oldAdapter = picker(editor, R.string.child_optional).adapter
                cancel(editor)
                assertEquals(0, data.children(2).observerCount)
                data.children(2).value = listOf(demoChild.copy(id = 12, caregiverId = 2, name = "Demo child B")); idle()
                assertSame(oldAdapter, picker(editor, R.string.child_optional).adapter)
            }
            filter(activity, 1)
            val editor = add(activity)
            controller.recreate(); idle()
            assertFalse(editor.isShowing)
            assertEquals(1, controller.get().findViewById<Spinner>(R.id.filter_spinner).selectedItemPosition)
            assertEquals(0, data.children(1).observerCount); assertEquals(0, data.appointments(1).observerCount)
            assertEquals(1, data.children(2).observerCount); assertEquals(1, data.appointments(2).observerCount)
        }
        assertTrue(data.children.values.all { it.observerCount == 0 })
        assertTrue(data.appointments.values.all { it.observerCount == 0 })
    }

    @Test fun `appointment retains optional no child choice on refresh and rerenders late child labels`() {
        seed(); data.appointments(1).value = listOf(demoVisit)
        launch(AppointmentFragment::class.java).use { controller ->
            val activity = controller.get()
            data.children(1).value = listOf(demoChild.copy(name = "Refreshed demo child")); idle()
            assertTrue(hasText(activity, "Child: Refreshed demo child"))
            action(activity, R.string.edit); val editor = dialog()
            select(picker(editor, R.string.child_optional), 0)
            data.children(1).value = listOf(demoChild.copy(name = "Another refresh")); idle()
            assertEquals(0, picker(editor, R.string.child_optional).selectedItemPosition)
            save(editor)
            assertNull(data.appointments(1).value!!.single().childId)
        }
    }

    @Test fun `appointment reassignment uses only the new caregivers children`() {
        seed(); data.appointments(1).value = listOf(demoVisit)
        launch(AppointmentFragment::class.java).use { controller ->
            action(controller.get(), R.string.edit); val editor = dialog()
            select(picker(editor, R.string.select_caregiver), 1)
            assertEquals(0, picker(editor, R.string.child_optional).selectedItemPosition)
            select(picker(editor, R.string.child_optional), 1)
            save(editor)
            assertTrue(data.appointments(1).value!!.isEmpty())
            val moved = data.appointments(2).value!!.single()
            assertEquals(2L, moved.caregiverId); assertEquals(12L, moved.childId)
        }
    }

    @Test fun `appointment cannot save a stale child while the selected caregivers query is pending`() {
        seed(); data.children[2] = TrackedRecords(null)
        launch(AppointmentFragment::class.java).use { controller ->
            val editor = add(controller.get())
            field(editor, R.string.appointment_title_hint).setText("Demo visit")
            select(picker(editor, R.string.child_optional), 1)
            select(picker(editor, R.string.select_caregiver), 1)
            assertFalse(editor.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            assertFalse(picker(editor, R.string.child_optional).isEnabled)
            assertEquals(0, picker(editor, R.string.child_optional).selectedItemPosition)
            data.children(1).value = listOf(demoChild.copy(name = "Old caregiver result")); idle()
            assertFalse(editor.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            data.children(2).value = emptyList(); idle()
            assertTrue(editor.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            save(editor)
            assertNull(data.appointments(2).value!!.single().childId)
        }
    }

    @Test fun `child and appointment delete failures display safe errors and retain rows`() {
        seed(); data.appointments(1).value = listOf(demoVisit)
        data.failure = IllegalStateException("private foreign key details")
        listOf(ChildFragment::class.java, AppointmentFragment::class.java).forEach { screen ->
            launch(screen).use { controller ->
                val activity = controller.get()
                action(activity, if (screen == ChildFragment::class.java) R.string.delete else R.string.cancel_appointment)
                save(dialog())
                val status = activity.findViewById<TextView>(R.id.status).text.toString()
                assertTrue(status.contains("Unable to change")); assertFalse(status.contains("private"))
                assertEquals(1, activity.findViewById<LinearLayout>(R.id.records).childCount)
            }
        }
    }

    @Test fun `dialog caregiver adapter keeps its IDs when the background list changes`() {
        seed()
        listOf(ChildFragment::class.java, AppointmentFragment::class.java).forEach { screen ->
            launch(screen).use { controller ->
                val editor = add(controller.get())
                val label = if (screen == ChildFragment::class.java) R.string.child_name else R.string.appointment_title_hint
                field(editor, label).setText("Demo record")
                select(picker(editor, R.string.select_caregiver), 1)
                data.caregivers.value = listOf(Caregiver(2, "Renamed demo caregiver")); idle()
                save(editor)
                assertFalse(editor.isShowing)
                if (screen == ChildFragment::class.java) assertTrue(data.children(2).value!!.any { it.name == "Demo record" })
                else assertTrue(data.appointments(2).value!!.any { it.title == "Demo record" })
            }
            seed()
        }
    }

    @Test @Config(qualifiers = "w320dp-h480dp-land-night")
    fun `all record forms scroll in compact dark landscape at large text size`() {
        RuntimeEnvironment.setFontScale(1.5f)
        seed()
        listOf(CaregiverFragment::class.java, ChildFragment::class.java, AppointmentFragment::class.java).forEach { screen ->
            launch(screen).use { controller ->
                val editor = add(controller.get())
                assertTrue(dialogViews(editor).any { it is ScrollView })
                assertTrue(editor.getButton(AlertDialog.BUTTON_POSITIVE).isShown)
                assertTrue(editor.getButton(AlertDialog.BUTTON_NEGATIVE).isShown)
                assertTrue(dialogViews(editor).filterIsInstance<EditText>().all { !it.contentDescription.isNullOrBlank() })
            }
        }
    }
}
