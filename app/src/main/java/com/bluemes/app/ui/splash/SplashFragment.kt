package com.bluemes.app.ui.splash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bluemes.app.R
import com.bluemes.app.databinding.FragmentSplashBinding
import com.bluemes.app.utils.UserPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Animate logo in
        binding.logoView.alpha = 0f
        binding.appNameText.alpha = 0f
        binding.logoView.animate().alpha(1f).setDuration(600).start()
        binding.appNameText.animate().alpha(1f).setDuration(600).setStartDelay(200).start()

        val prefs = UserPreferences(requireContext())

        lifecycleScope.launch {
            delay(1600)
            val setupDone = prefs.isSetupDone.first()
            if (setupDone) {
                findNavController().navigate(R.id.action_splash_to_nearby)
            } else {
                findNavController().navigate(R.id.action_splash_to_setup)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
