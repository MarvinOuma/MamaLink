package com.mamalink.app.notifications

import android.content.Context

/** Private, synchronous journal independent of the unchanged clinical Room schema v2.
 * Access is serialized by the scheduler. commit(), not apply(), closes process-death gaps.
 * Contains only IDs and epoch milliseconds, never names, titles, or clinical details.
 */
internal class ReminderState(context: Context) {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private fun write(edit: android.content.SharedPreferences.Editor.() -> Unit) {
        check(preferences.edit().apply(edit).commit()) { "Unable to persist reminder recovery state" }
    }
    fun markDirty() = write { putBoolean("dirty", true) }
    fun markClean() = write { putBoolean("dirty", false) }
    fun isDirty() = preferences.getBoolean("dirty", false)
    fun knownIds(): Set<Long> = preferences.getStringSet("ids", emptySet()).orEmpty().map { it.toLong() }.toSet()
    fun remember(id: Long) = write { putStringSet("ids", (knownIds() + id).map { it.toString() }.toSet()) }
    fun forget(id: Long) = write {
        putStringSet("ids", (knownIds() - id).map { it.toString() }.toSet())
        remove("scheduled_$id")
        remove("observed_$id")
    }
    fun scheduledRevision(id: Long): Long? = if (preferences.contains("scheduled_$id")) preferences.getLong("scheduled_$id", 0) else null
    fun scheduled(id: Long, revision: Long) = write { putLong("scheduled_$id", revision) }
    fun observedRevision(id: Long): Long? = if (preferences.contains("observed_$id")) preferences.getLong("observed_$id", 0) else null
    fun observed(id: Long, revision: Long) = write { putLong("observed_$id", revision) }
    // Retain all delivered revisions (including A -> B -> A), not only the latest one.
    fun wasDelivered(id: Long, revision: Long) = preferences.getBoolean("delivered_${id}_$revision", false)
    fun delivered(id: Long, revision: Long) = write {
        putBoolean("delivered_${id}_$revision", true)
        putStringSet("ids", (knownIds() + id).map { it.toString() }.toSet())
    }
    fun undoDelivery(id: Long, revision: Long) = write { remove("delivered_${id}_$revision") }

    companion object { const val FILE = "appointment_reminder_state" }
}
