package com.mamalink.app.ui.dashboard

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.mamalink.app.MamaLinkApplication
import com.mamalink.app.R
import com.mamalink.app.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {
    private val viewModel: DashboardViewModel by viewModels {
        DashboardViewModel.Factory(
            (requireActivity().application as MamaLinkApplication).workspaceRepository,
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Binding is local to the view lifecycle; observers are removed when the view is destroyed.
        val binding = FragmentDashboardBinding.bind(view)
        ViewCompat.setAccessibilityHeading(binding.workspaceLabel, true)
        ViewCompat.setAccessibilityHeading(binding.sectionHeading, true)
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.workspaceLabel.text = state.workspaceLabel ?: getString(R.string.app_name)
        }
        val destinations = mapOf(
            binding.caregiverButton to R.id.caregiverFragment,
            binding.childButton to R.id.childFragment,
            binding.appointmentsButton to R.id.appointmentsFragment,
            binding.timelineButton to R.id.timelineFragment,
            binding.resourcesButton to R.id.resourcesFragment,
        )
        destinations.forEach { (button, destination) ->
            button.setOnClickListener { findNavController().navigate(destination) }
        }
    }
}
