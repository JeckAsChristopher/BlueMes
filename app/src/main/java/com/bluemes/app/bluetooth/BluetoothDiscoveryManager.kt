package com.bluemes.app.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.bluemes.app.models.ConnectionState
import com.bluemes.app.models.NearbyUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages Bluetooth Classic discovery broadcasts.
 *
 * Note: discoverable devices appear here first with their Bluetooth *device name*
 * as the userName placeholder. The real BlueMes username is filled in by
 * BlueMesManager once a HANDSHAKE packet is received over the socket.
 */
class BluetoothDiscoveryManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter
) {
    private val _nearbyUsers = MutableStateFlow<Map<String, NearbyUser>>(emptyMap())
    val nearbyUsers: StateFlow<Map<String, NearbyUser>> = _nearbyUsers

    private var isReceiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
                    val rssi = intent.getShortExtra(
                        BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE
                    ).toInt()
                    onDeviceFound(device, rssi)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Discovery cycle finished — restarting")
                    startDiscovery()
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device: BluetoothDevice =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
                    removeUser(device.address)
                }
            }
        }
    }

    fun startDiscovery() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            context.registerReceiver(receiver, filter)
            isReceiverRegistered = true
        }
        if (bluetoothAdapter.isDiscovering) bluetoothAdapter.cancelDiscovery()
        val started = try { bluetoothAdapter.startDiscovery() } catch (_: SecurityException) { false }
        Log.d(TAG, "startDiscovery: $started")
    }

    fun stopDiscovery() {
        if (isReceiverRegistered) {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        try { bluetoothAdapter.cancelDiscovery() } catch (_: SecurityException) {}
        Log.d(TAG, "Discovery stopped")
    }

    /**
     * Called by BlueMesManager after a HANDSHAKE packet is received so that the
     * real BlueMes display name replaces the Bluetooth device name placeholder.
     */
    fun updateUserName(address: String, name: String) {
        _nearbyUsers.update { current ->
            val existing = current[address] ?: return@update current
            current.toMutableMap().also { it[address] = existing.copy(userName = name) }
        }
    }

    /**
     * Called by BlueMesManager to mark a device as connected in the list,
     * even if it was not found by discovery (i.e. when we are the acceptor).
     */
    fun addOrUpdateUser(address: String, userName: String, state: ConnectionState) {
        _nearbyUsers.update { current ->
            val existing = current[address]
            val updated = existing?.copy(userName = userName, connectionState = state)
                ?: NearbyUser(
                    deviceAddress = address,
                    deviceName = address,
                    userName = userName,
                    connectionState = state
                )
            current.toMutableMap().also { it[address] = updated }
        }
    }

    private fun onDeviceFound(device: BluetoothDevice, rssi: Int) {
        val address = device.address
        // Use device name as placeholder — real name comes from HANDSHAKE
        val deviceName = try { device.name } catch (_: SecurityException) { null } ?: address

        _nearbyUsers.update { current ->
            val existing = current[address]
            val updated = existing?.copy(
                rssi = rssi,
                lastSeenTimestamp = System.currentTimeMillis()
            ) ?: NearbyUser(
                deviceAddress = address,
                deviceName = deviceName,
                userName = deviceName, // placeholder until handshake
                rssi = rssi
            )
            current.toMutableMap().also { it[address] = updated }
        }
        Log.d(TAG, "Found: $deviceName ($address) RSSI=$rssi")
    }

    private fun removeUser(address: String) {
        _nearbyUsers.update { it.toMutableMap().also { m -> m.remove(address) } }
        Log.d(TAG, "Removed: $address")
    }

    companion object {
        private const val TAG = "BtDiscovery"
    }
}
