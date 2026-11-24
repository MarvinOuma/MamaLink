package com.mamalink.app.ui.common

/** Observable progress and outcome for a repository write. */
data class RepositoryOperationState(
    val inProgress: Boolean = false,
    val completedOperation: Operation? = null,
    val affectedId: Long? = null,
    val error: String? = null,
) {
    enum class Operation { INSERT, UPDATE, DELETE }
}
