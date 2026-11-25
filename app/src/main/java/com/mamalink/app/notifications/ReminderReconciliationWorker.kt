package com.mamalink.app.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mamalink.app.MamaLinkApplication
import kotlinx.coroutines.CancellationException

class ReminderReconciliationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? MamaLinkApplication ?: return Result.failure()
        return reconcile(app.reminderScheduler)
    }

    internal suspend fun reconcile(scheduler: AppointmentReminderScheduler): Result = try {
        scheduler.reconcileReminders(emptyList())
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Log.e("ReminderRecovery", "Reconciliation failed; WorkManager will retry", failure)
        Result.retry()
    }
}
