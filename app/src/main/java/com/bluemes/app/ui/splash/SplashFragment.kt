package com.bluemes.app.ui.splash

import android.os.Bundle
import android.view.*
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
    private var _b: FragmentSplashBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSplashBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        b.logoView.alpha = 0f; b.appNameText.alpha = 0f
        b.logoView.animate().alpha(1f).setDuration(600).start()
        b.appNameText.animate().alpha(1f).setDuration(600).setStartDelay(200).start()

        lifecycleScope.launch {
            delay(1500)
            val done = UserPreferences(requireContext()).isSetupDone.first()
            findNavController().navigate(
                if (done) R.id.action_splash_to_nearby else R.id.action_splash_to_setup
            )
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
