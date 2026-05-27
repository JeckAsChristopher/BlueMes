package com.bluemes.app.ui.settings

import android.bluetooth.BluetoothManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bluemes.app.BuildConfig
import com.bluemes.app.R
import com.bluemes.app.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.appVersion.text = "Version ${BuildConfig.VERSION_NAME}"

        val btManager = requireContext().getSystemService(BluetoothManager::class.java)
        val btEnabled = btManager?.adapter?.isEnabled == true
        binding.bluetoothStatus.text = if (btEnabled) "Bluetooth: On" else "Bluetooth: Off"

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.userName.collect { name ->
                    binding.currentName.text = name ?: "Not set"
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isDarkMode.collect { dark ->
                    binding.darkModeSwitch.isChecked = dark
                }
            }
        }

        binding.changeNameButton.setOnClickListener {
            showChangeNameDialog()
        }

        binding.clearHistoryButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear Chat History")
                .setMessage("This will permanently delete all conversations and messages. This cannot be undone.")
                .setPositiveButton("Clear") { _, _ -> viewModel.clearHistory() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.darkModeSwitch.setOnCheckedChangeListener { _, checked ->
            viewModel.setDarkMode(checked)
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_nearby -> {
                    findNavController().navigate(R.id.action_settings_to_nearby)
                    true
                }
                R.id.nav_history -> {
                    findNavController().navigate(R.id.action_settings_to_history)
                    true
                }
                R.id.nav_settings -> true
                else -> false
            }
        }
        binding.bottomNav.selectedItemId = R.id.nav_settings
    }

    private fun showChangeNameDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "Enter new name"
            setText(binding.currentName.text)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Change Name")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.length in 2..30) viewModel.updateUserName(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
