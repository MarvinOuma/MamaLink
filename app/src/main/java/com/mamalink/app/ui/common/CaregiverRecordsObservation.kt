package com.mamalink.app.ui.common

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

/** One replaceable subscription, owned by a screen view (or explicitly cleared by a dialog). */
internal class CaregiverRecordsObservation<T>(
    private val owner: LifecycleOwner,
    private val sourceFor: (Long) -> LiveData<List<T>>,
    private val onChanged: (List<T>) -> Unit,
) : DefaultLifecycleObserver {
    private var caregiverId: Long? = null
    private var source: LiveData<List<T>>? = null
    private var observer: Observer<List<T>>? = null

    init { owner.lifecycle.addObserver(this) }

    fun select(id: Long) {
        if (caregiverId == id || owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return
        clear()
        caregiverId = id
        val next = sourceFor(id)
        val listener = Observer<List<T>> { records ->
            if (caregiverId == id) onChanged(records)
        }
        source = next
        observer = listener
        next.observe(owner, listener)
    }

    fun clear() {
        caregiverId = null
        observer?.let { source?.removeObserver(it) }
        source = null
        observer = null
    }

    fun dispose() {
        clear()
        owner.lifecycle.removeObserver(this)
    }

    override fun onDestroy(owner: LifecycleOwner) = dispose()
}
