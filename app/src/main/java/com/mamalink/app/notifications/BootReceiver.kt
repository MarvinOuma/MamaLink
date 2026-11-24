package com.mamalink.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mamalink.app.MamaLinkApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val app = context.applicationContext as? MamaLinkApplication ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching {
                    app.reminderScheduler.reconcileReminders(app.appointmentRepository.getAll())
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
