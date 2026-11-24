package com.mamalink.app.ui

import android.os.Looper
import android.widget.TextView
import android.view.View
import com.mamalink.app.ui.caregiver.CaregiverFragment
import com.mamalink.app.ui.child.ChildFragment
import com.mamalink.app.ui.appointment.AppointmentFragment
import androidx.navigation.fragment.NavHostFragment
import com.mamalink.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 35])
class NavigationTest {
    @Test fun `dashboard opens real record screens and coming soon screens and toolbar up returns home`() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val nav = (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                as NavHostFragment).navController
            assertEquals(R.id.dashboardFragment, nav.currentDestination?.id)
            val destinations = listOf(
                Triple(R.id.caregiver_button, R.id.caregiverFragment, R.string.caregiver_title),
                Triple(R.id.child_button, R.id.childFragment, R.string.child_title),
                Triple(R.id.appointments_button, R.id.appointmentsFragment, R.string.appointments_title),
                Triple(R.id.timeline_button, R.id.timelineFragment, R.string.timeline_title),
                Triple(R.id.resources_button, R.id.resourcesFragment, R.string.resources_title),
            )
            destinations.forEach { (buttonId, destinationId, titleId) ->
                activity.findViewById<android.view.View>(buttonId).performClick()
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(destinationId, nav.currentDestination?.id)
                assertEquals(activity.getString(titleId), activity.findViewById<TextView>(R.id.placeholder_title).text.toString())
                val screen = (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                    as NavHostFragment).childFragmentManager.primaryNavigationFragment
                when (destinationId) {
                    R.id.caregiverFragment -> {
                        assertTrue(screen is CaregiverFragment)
                        assertRecords(activity, R.string.add_caregiver, R.string.caregiver_intro, false)
                    }
                    R.id.childFragment -> {
                        assertTrue(screen is ChildFragment)
                        assertRecords(activity, R.string.add_child, R.string.child_intro, true)
                    }
                    R.id.appointmentsFragment -> {
                        assertTrue(screen is AppointmentFragment)
                        assertRecords(activity, R.string.add_appointment, R.string.appointment_intro, true)
                    }
                }
                assertTrue(activity.onSupportNavigateUp())
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(R.id.dashboardFragment, nav.currentDestination?.id)
            }
        }
    }

    @Test fun `system back returns to the dashboard`() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            activity.findViewById<android.view.View>(R.id.appointments_button).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertRecords(activity, R.string.add_appointment, R.string.appointment_intro, true)
            activity.onBackPressedDispatcher.onBackPressed()
            shadowOf(Looper.getMainLooper()).idle()
            val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                as NavHostFragment
            assertEquals(R.id.dashboardFragment, host.navController.currentDestination?.id)
        }
    }

    @Test fun `navigation destination survives activity recreation`() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            controller.get().findViewById<android.view.View>(R.id.child_button).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            controller.recreate()
            val host = controller.get().supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                as NavHostFragment
            assertEquals(R.id.childFragment, host.navController.currentDestination?.id)
            assertEquals(controller.get().getString(R.string.child_title),
                controller.get().findViewById<TextView>(R.id.placeholder_title).text.toString())
            assertRecords(controller.get(), R.string.add_child, R.string.child_intro, true)
            controller.get().onBackPressedDispatcher.onBackPressed()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(R.id.dashboardFragment, host.navController.currentDestination?.id)
        }
    }

    private fun assertRecords(activity: MainActivity, addLabel: Int, intro: Int, filtered: Boolean) {
        assertEquals(activity.getString(addLabel), activity.findViewById<TextView>(R.id.add_button).text.toString())
        assertEquals(activity.getString(intro), activity.findViewById<TextView>(R.id.screen_intro).text.toString())
        assertEquals(if (filtered) View.VISIBLE else View.GONE, activity.findViewById<View>(R.id.filter_spinner).visibility)
        assertTrue(activity.findViewById<View>(R.id.records).isShown)
    }
}
