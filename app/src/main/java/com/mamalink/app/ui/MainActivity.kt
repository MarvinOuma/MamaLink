package com.mamalink.app.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.mamalink.app.R
import com.mamalink.app.databinding.ActivityMainBinding
import com.mamalink.app.notifications.AppointmentNotificationHelper

class MainActivity : AppCompatActivity() {
    private val navController by lazy {
        (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment)
            .navController
    }
    private val appBarConfiguration by lazy { AppBarConfiguration(setOf(R.id.dashboardFragment)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
        setSupportActionBar(binding.toolbar)
        (application as com.mamalink.app.MamaLinkApplication).reconcileAppointmentReminders()
        setupActionBarWithNavController(navController, appBarConfiguration)
        handleAppointmentNotificationIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAppointmentNotificationIntent(intent)
    }

    private fun handleAppointmentNotificationIntent(intent: android.content.Intent?) {
        if (intent?.getIntExtra(
                AppointmentNotificationHelper.EXTRA_NAVIGATE_DESTINATION,
                0,
            ) != R.id.appointmentsFragment
        ) return

        val appointmentId = intent.getLongExtra(
            AppointmentNotificationHelper.EXTRA_APPOINTMENT_ID,
            0L,
        )
        val arguments = Bundle().apply { putLong("appointmentId", appointmentId) }
        navController.navigate(
            R.id.appointmentsFragment,
            arguments,
            navOptions { launchSingleTop = true },
        )
        intent.removeExtra(AppointmentNotificationHelper.EXTRA_NAVIGATE_DESTINATION)
        intent.removeExtra(AppointmentNotificationHelper.EXTRA_APPOINTMENT_ID)
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
}
