package com.mamalink.app.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppointmentNotificationHelperTest {
    @Test
    fun `granted notification keeps details private and navigation immutable`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(AppointmentNotificationHelper.showAppointmentNotification(context, 7, "Visit", "Tomorrow"))
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = shadowOf(manager).allNotifications.single()
        assertEquals(android.app.Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertTrue(notification.contentIntent.isImmutable)
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(7L, intent.getLongExtra(AppointmentNotificationHelper.EXTRA_APPOINTMENT_ID, 0))
        assertEquals(com.mamalink.app.R.id.appointmentsFragment,
            intent.getIntExtra(AppointmentNotificationHelper.EXTRA_NAVIGATE_DESTINATION, 0))
    }

    @Test
    fun `denied notification permission is handled without posting or crashing`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        AppointmentNotificationHelper.showAppointmentNotification(
            context = context,
            appointmentId = 5L,
            title = "Routine visit",
            formattedTime = "Nov 26, 1:00 PM",
        )

        val manager = context.getSystemService(NotificationManager::class.java)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
