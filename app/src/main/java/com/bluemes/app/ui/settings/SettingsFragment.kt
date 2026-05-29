package com.bluemes.app.ui.settings

import android.bluetooth.BluetoothManager
import android.os.Bundle
import android.view.*
import android.widget.EditText
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
    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!
    private val vm: SettingsViewModel by viewModels { SettingsViewModelFactory(requireContext()) }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        b.appVersion.text = "Version ${BuildConfig.VERSION_NAME}"
        val btOn = requireContext().getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
        b.bluetoothStatus.text = if (btOn) "Bluetooth: On" else "Bluetooth: Off"

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { vm.userName.collect { b.currentName.text = it ?: "Not set" } }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { vm.isDark.collect { b.darkModeSwitch.isChecked = it } }
        }

        b.changeNameButton.setOnClickListener {
            val input = EditText(requireContext()).apply { hint = "Enter new name"; setText(b.currentName.text) }
            AlertDialog.Builder(requireContext()).setTitle("Change Name").setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val n = input.text.toString().trim()
                    if (n.length in 2..30) vm.setName(n)
                }.setNegativeButton("Cancel", null).show()
        }

        b.clearHistoryButton.setOnClickListener {
            AlertDialog.Builder(requireContext()).setTitle("Clear Chat History")
                .setMessage("This will permanently delete all conversations and messages.")
                .setPositiveButton("Clear") { _, _ -> vm.clearHistory() }
                .setNegativeButton("Cancel", null).show()
        }

        b.darkModeSwitch.setOnCheckedChangeListener { _, on ->
            vm.setDark(on)
            AppCompatDelegate.setDefaultNightMode(
                if (on) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )
        }

        b.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_nearby   -> { findNavController().navigate(R.id.action_settings_to_nearby); true }
                R.id.nav_history  -> { findNavController().navigate(R.id.action_settings_to_history); true }
                R.id.nav_settings -> true
                else -> false
            }
        }
        b.bottomNav.selectedItemId = R.id.nav_settings
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
