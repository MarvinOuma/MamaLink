package com.mamalink.app.ui.records

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import com.mamalink.app.R
import com.mamalink.app.domain.model.Caregiver
import com.mamalink.app.ui.appointment.AppointmentFragment
import com.mamalink.app.ui.child.ChildFragment
import com.mamalink.app.ui.child.ChildEditorDialogFragment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Stage5ReviewProbeTest {
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()

    @Test fun `appointment caregiver switch remains usable after background return`() {
        val data = ScreenData()
        data.caregivers.value = listOf(Caregiver(1, "A"), Caregiver(2, "B"))
        RecordsTestHost.data = data
        RecordsTestHost.screen = AppointmentFragment::class.java
        Robolectric.buildActivity(RecordsTestHost::class.java).setup().visible().use { controller ->
            idle()
            controller.get().findViewById<View>(R.id.add_button).performClick(); idle()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            controller.pause().stop(); idle()
            controller.start().resume().visible(); idle()
            val picker = views(dialog.window!!.decorView).filterIsInstance<Spinner>().single { it.contentDescription == dialog.context.getString(R.string.select_caregiver) }
            picker.setSelection(1)
            picker.onItemSelectedListener!!.onItemSelected(picker, null, 1, 1)
            idle()
            assertTrue("Save must become enabled after switching caregiver", dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        }
    }

    @Test fun `restored child form enables save when caregiver query arrives late`() {
        val data = ScreenData()
        RecordsTestHost.data = data
        RecordsTestHost.screen = ChildFragment::class.java
        Robolectric.buildActivity(RecordsTestHost::class.java).setup().visible().use { controller ->
            idle()
            val parent = controller.get().supportFragmentManager.findFragmentById(R.id.nav_host_fragment)!!
            ChildEditorDialogFragment.newInstance(null, 1).show(parent.childFragmentManager, "restored-editor")
            idle()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            data.caregivers.value = listOf(Caregiver(1, "A")); idle()
            assertTrue("Save must become enabled after caregiver data loads", dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        }
    }
}
