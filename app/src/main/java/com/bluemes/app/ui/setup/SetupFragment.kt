package com.bluemes.app.ui.setup

import android.os.Bundle
import android.view.*
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
    private var _b: FragmentSetupBinding? = null
    private val b get() = _b!!
    private val vm: SetupViewModel by viewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSetupBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        b.contentCard.translationY = 80f; b.contentCard.alpha = 0f
        b.contentCard.animate().translationY(0f).alpha(1f).setDuration(400).setStartDelay(100).start()

        b.nameInput.doAfterTextChanged { t ->
            b.continueButton.isEnabled = (t?.toString()?.trim()?.length ?: 0) in 2..30
        }
        b.nameInput.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE && b.continueButton.isEnabled) { go(); true } else false
        }
        b.continueButton.setOnClickListener { go() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.setupComplete.collect { if (it) findNavController().navigate(R.id.action_setup_to_nearby) }
            }
        }
    }

    private fun go() {
        val n = b.nameInput.text.toString().trim()
        if (n.length >= 2) vm.save(requireContext(), n)
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
