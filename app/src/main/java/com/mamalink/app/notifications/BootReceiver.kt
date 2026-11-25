package com.mamalink.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Broadcast lifetime covers only persistence of work, never database reconciliation.
                kotlinx.coroutines.withTimeout(8_000) {
                    enqueueRecovery(AndroidReminderWorkQueue(context))
                }
            } catch (failure: Exception) {
                android.util.Log.e("ReminderRecovery", "Boot repair enqueue failed; registered periodic recovery remains durable", failure)
            } finally {
                pendingResult.finish()
            }
        }
    }

    internal suspend fun enqueueRecovery(queue: ReminderWorkQueue) = queue.requestReconciliation()
}
