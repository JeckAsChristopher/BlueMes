package com.bluemes.app.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Emits raw Bluetooth discovery events.
 *
 * IMPORTANT: this class no longer maintains a "nearbyUsers" map of its own.
 * Raw found-devices are forwarded to BlueMesManager, which only promotes a
 * device into the visible nearby list AFTER the BlueMes handshake succeeds.
 * This prevents non-BlueMes Bluetooth devices from ever appearing in the UI.
 */
class BluetoothDiscoveryManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter
) {
    private val _deviceFound = MutableSharedFlow<DiscoveredDevice>(extraBufferCapacity = 64)
    val deviceFound: SharedFlow<DiscoveredDevice> = _deviceFound

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val dev: BluetoothDevice =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    val name = try { dev.name } catch (_: SecurityException) { null } ?: dev.address
                    _deviceFound.tryEmit(DiscoveredDevice(dev, name, rssi))
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Discovery finished — restarting")
                    startDiscovery()
                }
            }
        }
    }

    fun startDiscovery() {
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            context.registerReceiver(receiver, filter)
            registered = true
        }
        if (bluetoothAdapter.isDiscovering) bluetoothAdapter.cancelDiscovery()
        val ok = try { bluetoothAdapter.startDiscovery() } catch (_: SecurityException) { false }
        Log.d(TAG, "startDiscovery: $ok")
    }

    fun stopDiscovery() {
        if (registered) {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            registered = false
        }
        try { bluetoothAdapter.cancelDiscovery() } catch (_: SecurityException) {}
    }

    companion object { private const val TAG = "BtDiscovery" }
}

data class DiscoveredDevice(
    val device: BluetoothDevice,
    val deviceName: String,
    val rssi: Int
)
