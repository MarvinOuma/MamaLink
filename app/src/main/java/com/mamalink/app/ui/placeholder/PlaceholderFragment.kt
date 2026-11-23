package com.mamalink.app.ui.placeholder

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.mamalink.app.R
import com.mamalink.app.databinding.FragmentPlaceholderBinding

/** Shared presentation for unfinished destinations; contains no medical content or input forms. */
class PlaceholderFragment : Fragment(R.layout.fragment_placeholder) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentPlaceholderBinding.bind(view)
        ViewCompat.setAccessibilityHeading(binding.placeholderTitle, true)
        binding.placeholderTitle.text = getString(requireArguments().getInt("titleRes"))
        binding.placeholderDescription.text = getString(requireArguments().getInt("descriptionRes"))
    }
}
