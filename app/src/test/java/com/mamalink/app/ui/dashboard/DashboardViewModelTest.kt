package com.mamalink.app.ui.dashboard

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.mamalink.app.data.repository.WorkspaceRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class DashboardViewModelTest {
    @get:Rule val instantExecutor = InstantTaskExecutorRule()

    @Test fun `label updates reach the UI and blank labels use the localized default`() {
        val labels = MutableLiveData<String?>(null)
        val repository = object : WorkspaceRepository {
            override fun observeLabel() = labels
        }
        val viewModel = DashboardViewModel(repository)
        val observer = Observer<DashboardUiState> { }
        viewModel.uiState.observeForever(observer)
        try {
            assertNull(viewModel.uiState.value?.workspaceLabel)
            labels.value = "  Demo workspace  "
            assertEquals("Demo workspace", viewModel.uiState.value?.workspaceLabel)
            labels.value = "  "
            assertNull(viewModel.uiState.value?.workspaceLabel)
            labels.value = null
            assertNull(viewModel.uiState.value?.workspaceLabel)
        } finally {
            viewModel.uiState.removeObserver(observer)
        }
    }
}
