package com.bluemes.app.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bluemes.app.R
import com.bluemes.app.databinding.FragmentSetupBinding
import kotlinx.coroutines.launch

class SetupFragment : Fragment() {

    private var _binding: FragmentSetupBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SetupViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Slide-in entrance animation
        binding.contentCard.translationY = 80f
        binding.contentCard.alpha = 0f
        binding.contentCard.animate().translationY(0f).alpha(1f).setDuration(400).setStartDelay(100).start()

        binding.nameInput.doAfterTextChanged { text ->
            val trimmed = text?.toString()?.trim() ?: ""
            binding.continueButton.isEnabled = trimmed.length in 2..30
        }

        binding.nameInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && binding.continueButton.isEnabled) {
                onContinue()
                true
            } else false
        }

        binding.continueButton.setOnClickListener { onContinue() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.setupComplete.collect { done ->
                    if (done) findNavController().navigate(R.id.action_setup_to_nearby)
                }
            }
        }
    }

    private fun onContinue() {
        val name = binding.nameInput.text.toString().trim()
        if (name.length < 2) return
        viewModel.saveUserName(requireContext(), name)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
