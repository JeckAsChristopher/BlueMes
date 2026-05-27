package com.bluemes.app.ui.nearby

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bluemes.app.R
import com.bluemes.app.databinding.FragmentNearbyBinding
import com.bluemes.app.ui.nearby.adapters.NearbyUserAdapter
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class NearbyFragment : Fragment() {

    private var _binding: FragmentNearbyBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NearbyViewModel by viewModels {
        NearbyViewModelFactory(requireContext())
    }

    private lateinit var adapter: NearbyUserAdapter

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            checkPermissionsAndStart()
        } else {
            showSnackbar(getString(R.string.bluetooth_required))
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            startDiscovery()
        } else {
            showSnackbar(getString(R.string.permissions_required))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNearbyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = NearbyUserAdapter { user ->
            val bundle = Bundle().apply {
                putString("deviceAddress", user.deviceAddress)
                putString("userName", user.userName)
            }
            findNavController().navigate(R.id.action_nearby_to_chat, bundle)
        }

        binding.recyclerNearby.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerNearby.adapter = adapter

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_nearby -> true
                R.id.nav_history -> {
                    findNavController().navigate(R.id.action_nearby_to_history)
                    true
                }
                R.id.nav_settings -> {
                    findNavController().navigate(R.id.action_nearby_to_settings)
                    true
                }
                else -> false
            }
        }
        binding.bottomNav.selectedItemId = R.id.nav_nearby

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.nearbyUsers.collect { users ->
                    val list = users.values.toList()
                    adapter.submitList(list)
                    binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerNearby.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }

        checkPermissionsAndStart()
    }

    private fun checkPermissionsAndStart() {
        val btManager = requireContext().getSystemService(BluetoothManager::class.java)
        val btAdapter = btManager?.adapter
        if (btAdapter == null) {
            showSnackbar(getString(R.string.bluetooth_not_supported))
            return
        }
        if (!btAdapter.isEnabled) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        requestPermissionsIfNeeded()
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN))
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT))
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (needed.isEmpty()) startDiscovery() else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun startDiscovery() {
        viewModel.startDiscoveryAndServer()
    }

    private fun hasPermission(perm: String) =
        ContextCompat.checkSelfPermission(requireContext(), perm) == PackageManager.PERMISSION_GRANTED

    private fun showSnackbar(msg: String) =
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
