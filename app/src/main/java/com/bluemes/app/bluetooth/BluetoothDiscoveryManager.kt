package com.bluemes.app.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.bluemes.app.models.NearbyUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages Bluetooth Classic discovery and maintains a live list of
 * nearby BlueMes users (those advertising our service UUID).
 *
 * Caller must hold BLUETOOTH_SCAN + BLUETOOTH_CONNECT permissions before
 * calling startDiscovery().
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
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()

                    device?.let { handleDeviceFound(it, rssi) }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Discovery cycle finished — restarting")
                    startDiscovery()
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let { removeUser(it.address) }
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
        val started = bluetoothAdapter.startDiscovery()
        Log.d(TAG, "Discovery started: $started")
    }

    fun stopDiscovery() {
        if (isReceiverRegistered) {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        bluetoothAdapter.cancelDiscovery()
        Log.d(TAG, "Discovery stopped")
    }

    fun updateUserName(address: String, name: String) {
        _nearbyUsers.update { current ->
            current[address]?.let { user ->
                current.toMutableMap().also { it[address] = user.copy(userName = name) }
            } ?: current
        }
    }

    private fun handleDeviceFound(device: BluetoothDevice, rssi: Int) {
        val address = device.address
        val deviceName = try { device.name } catch (_: SecurityException) { null } ?: "Unknown"

        // We add all visible devices; handshake will confirm they run BlueMes
        _nearbyUsers.update { current ->
            val existing = current[address]
            val updated = existing?.copy(rssi = rssi, lastSeenTimestamp = System.currentTimeMillis())
                ?: NearbyUser(
                    deviceAddress = address,
                    deviceName = deviceName,
                    userName = deviceName,
                    rssi = rssi
                )
            current.toMutableMap().also { it[address] = updated }
        }
        Log.d(TAG, "Device found: $deviceName ($address) RSSI=$rssi")
    }

    private fun removeUser(address: String) {
        _nearbyUsers.update { it.toMutableMap().also { m -> m.remove(address) } }
        Log.d(TAG, "Device gone: $address")
    }

    companion object {
        private const val TAG = "BluetoothDiscovery"
    }
}
