package com.mamalink.app.ui.common

import android.app.Dialog
import android.content.Context
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** Dialogs contain private form data; dismiss them when their owning view goes away. */
internal class ViewDialogs(owner: LifecycleOwner) : DefaultLifecycleObserver {
    private val dialogs = mutableSetOf<Dialog>()
    init { owner.lifecycle.addObserver(this) }

    fun show(dialog: Dialog, onDismiss: () -> Unit = {}) {
        dialogs.add(dialog)
        dialog.setOnDismissListener {
            dialogs.remove(dialog)
            onDismiss()
        }
        dialog.show()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        dialogs.toList().forEach { it.dismiss() }
        dialogs.clear()
        owner.lifecycle.removeObserver(this)
    }
}

internal fun Context.recordForm() = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    val inset = (24 * resources.displayMetrics.density).toInt()
    setPadding(inset, inset / 3, inset, 0)
}

internal fun LinearLayout.scrollable() = ScrollView(context).apply { addView(this@scrollable) }
