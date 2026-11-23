package com.mamalink.app.ui

import android.os.Looper
import android.widget.TextView
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
    @Test fun `dashboard opens every placeholder and toolbar up returns home`() {
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
        }
    }
}
