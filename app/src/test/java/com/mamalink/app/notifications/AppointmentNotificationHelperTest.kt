package com.mamalink.app.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppointmentNotificationHelperTest {
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
