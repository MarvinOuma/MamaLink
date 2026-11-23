package com.mamalink.app.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import com.mamalink.app.data.repository.WorkspaceRepository

/** A null label means the view should use its localized default app name. */
data class DashboardUiState(val workspaceLabel: String? = null)

class DashboardViewModel(repository: WorkspaceRepository) : ViewModel() {
    val uiState: LiveData<DashboardUiState> = repository.observeLabel().map { label ->
        DashboardUiState(label?.trim()?.takeIf { it.isNotEmpty() })
    }

    class Factory(private val repository: WorkspaceRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == DashboardViewModel::class.java) { "Unknown ViewModel: $modelClass" }
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(repository) as T
        }
    }
}
