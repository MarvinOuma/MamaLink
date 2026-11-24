package com.mamalink.app.ui.common

/** Only explicit validation messages are safe to display; repository failures stay generic. */
internal class OperationRejected(message: String) : IllegalArgumentException(message)

internal fun validateInput(valid: Boolean, message: String) {
    if (!valid) throw OperationRejected(message)
}
