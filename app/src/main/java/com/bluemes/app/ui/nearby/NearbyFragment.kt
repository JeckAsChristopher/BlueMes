package com.bluemes.app.ui.nearby

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.*
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class NearbyFragment : Fragment() {
    private var _b: FragmentNearbyBinding? = null
    private val b get() = _b!!
    private val vm: NearbyViewModel by viewModels { NearbyViewModelFactory(requireContext()) }
    private lateinit var adapter: NearbyUserAdapter

    private val enableBt = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) checkAndStart()
        else snack(getString(R.string.bluetooth_required))
    }

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { g ->
        if (g.values.all { it }) startUp() else snack(getString(R.string.permissions_required))
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentNearbyBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)

        adapter = NearbyUserAdapter { user ->
            val bundle = Bundle().apply {
                putString("deviceAddress", user.deviceAddress)
                putString("userName", user.userName)
            }
            findNavController().navigate(R.id.action_nearby_to_chat, bundle)
        }
        b.recyclerNearby.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerNearby.adapter = adapter

        b.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_nearby  -> true
                R.id.nav_history -> { findNavController().navigate(R.id.action_nearby_to_history); true }
                R.id.nav_settings -> { findNavController().navigate(R.id.action_nearby_to_settings); true }
                else -> false
            }
        }
        b.bottomNav.selectedItemId = R.id.nav_nearby

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.nearbyUsers.collect { users ->
                    // Only verified BlueMes users are in this map — filter is already done
                    val list = users.values.toList()
                        .sortedByDescending { it.lastSeenTimestamp }
                    adapter.submitList(list)
                    b.emptyState.visibility  = if (list.isEmpty()) View.VISIBLE else View.GONE
                    b.recyclerNearby.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }

        // ── Connection approval dialog ──────────────────────────────────────
        // When someone connects to us (we are the acceptor), show a dialog
        // asking the user to accept or deny before opening chat.
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.pendingRequests.collect { req ->
                    showApprovalDialog(req.address, req.userName)
                }
            }
        }

        checkAndStart()
    }

    private fun showApprovalDialog(address: String, senderName: String) {
        if (!isAdded) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Connection Request")
            .setMessage("$senderName wants to connect to you.")
            .setCancelable(false)
            .setPositiveButton("Accept") { _, _ ->
                vm.approveConnection(address)
                // Navigate directly to the chat with the requester
                val bundle = Bundle().apply {
                    putString("deviceAddress", address)
                    putString("userName", senderName)
                }
                findNavController().navigate(R.id.action_nearby_to_chat, bundle)
            }
            .setNegativeButton("Decline") { _, _ ->
                vm.denyConnection(address)
                snack("$senderName's request was declined.")
            }
            .show()
    }

    private fun checkAndStart() {
        val btAdapter = requireContext().getSystemService(BluetoothManager::class.java)?.adapter
        if (btAdapter == null) { snack(getString(R.string.bluetooth_not_supported)); return }
        if (!btAdapter.isEnabled) {
            enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)); return
        }
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!hasPerm(Manifest.permission.BLUETOOTH_SCAN))    add(Manifest.permission.BLUETOOTH_SCAN)
                if (!hasPerm(Manifest.permission.BLUETOOTH_CONNECT)) add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                if (!hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)) add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        if (needed.isEmpty()) startUp() else perms.launch(needed.toTypedArray())
    }

    private fun startUp() = vm.startDiscoveryAndServer()

    private fun hasPerm(p: String) =
        ContextCompat.checkSelfPermission(requireContext(), p) == PackageManager.PERMISSION_GRANTED

    private fun snack(msg: String) = Snackbar.make(b.root, msg, Snackbar.LENGTH_LONG).show()

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
