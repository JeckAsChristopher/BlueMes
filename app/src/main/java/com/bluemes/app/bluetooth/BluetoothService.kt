package com.bluemes.app.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.bluemes.app.models.MessagePacket
import com.bluemes.app.models.PacketType
import com.bluemes.app.utils.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Low-level RFCOMM socket layer.
 *
 * Exactly ONE instance of this class lives in the app (owned by BlueMesManager).
 * All handshake verification, encryption, and connection-approval logic lives
 * in BlueMesManager — this class only moves bytes.
 *
 * FIX — connection timeout shown despite messages flowing:
 *   connect() now checks isConnected() first and emits Connected immediately
 *   if the socket was already established via the server path, preventing the
 *   "trying to connect as client → timeout" race.
 */
class BluetoothService(
    private val bluetoothAdapter: BluetoothAdapter,
    val localUserName: String,
    val localAddress: String
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var acceptJob: Job? = null

    private val activeSockets = ConcurrentHashMap<String, ConnectedPeer>()

    private val _incomingRaw = MutableSharedFlow<RawPacket>(extraBufferCapacity = 256)
    val incomingRaw: SharedFlow<RawPacket> = _incomingRaw

    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 64)
    val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents

    private val _connectedPeers = MutableStateFlow<Set<String>>(emptySet())
    val connectedPeers: StateFlow<Set<String>> = _connectedPeers

    // -------------------------------------------------------------------------
    // Server — accept loop
    // -------------------------------------------------------------------------

    fun startServer() {
        acceptJob?.cancel()
        acceptJob = scope.launch {
            var serverSocket: BluetoothServerSocket? = null
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                    Constants.BLUEMES_SERVICE_NAME, Constants.BLUEMES_UUID
                )
                Log.d(TAG, "Server listening")
                while (isActive) {
                    val socket = try {
                        serverSocket.accept()
                    } catch (e: IOException) {
                        if (isActive) Log.e(TAG, "accept() failed", e)
                        break
                    }
                    val addr = socket.remoteDevice.address
                    if (activeSockets.containsKey(addr)) {
                        try { socket.close() } catch (_: IOException) {}
                    } else {
                        Log.d(TAG, "Accepted socket from $addr")
                        launchPeer(socket, isInitiator = false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
            } finally {
                try { serverSocket?.close() } catch (_: IOException) {}
            }
        }
    }

    // -------------------------------------------------------------------------
    // Client — outgoing connection
    // -------------------------------------------------------------------------

    fun connect(device: BluetoothDevice) {
        val address = device.address
        // FIX: if already connected via server path, just announce it — no new socket needed
        if (activeSockets.containsKey(address)) {
            scope.launch { _connectionEvents.emit(ConnectionEvent.Connected(address)) }
            return
        }
        scope.launch {
            _connectionEvents.emit(ConnectionEvent.Connecting(address))
            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(Constants.BLUEMES_UUID)
                try { bluetoothAdapter.cancelDiscovery() } catch (_: SecurityException) {}
                withTimeout(Constants.CONNECTION_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) { socket.connect() }
                }
                Log.d(TAG, "Connected to $address")
                launchPeer(socket, isInitiator = true)
            } catch (e: Exception) {
                Log.e(TAG, "connect() failed for $address: ${e.message}")
                try { socket?.close() } catch (_: IOException) {}
                // Only emit Failed if we're still not connected (server path may have won)
                if (!activeSockets.containsKey(address)) {
                    _connectionEvents.emit(ConnectionEvent.Failed(address, e.message ?: "Timeout"))
                } else {
                    _connectionEvents.emit(ConnectionEvent.Connected(address))
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Per-peer read loop
    // -------------------------------------------------------------------------

    private fun launchPeer(socket: BluetoothSocket, isInitiator: Boolean) {
        val address = socket.remoteDevice.address
        val peer = ConnectedPeer(socket, isInitiator)
        activeSockets[address] = peer
        updatePeerSet()

        scope.launch {
            _connectionEvents.emit(ConnectionEvent.Connected(address))

            val inputStream = try { socket.inputStream } catch (e: IOException) {
                Log.e(TAG, "No inputStream for $address", e)
                disconnect(address); return@launch
            }

            val buf = StringBuilder()
            val bytes = ByteArray(Constants.SOCKET_BUFFER_SIZE)
            try {
                while (isActive && socket.isConnected) {
                    val n = withContext(Dispatchers.IO) { inputStream.read(bytes) }
                    if (n == -1) break
                    buf.append(String(bytes, 0, n, Charsets.UTF_8))
                    var idx: Int
                    while (buf.indexOf(Constants.PACKET_DELIMITER).also { idx = it } != -1) {
                        val raw = buf.substring(0, idx).trim()
                        buf.delete(0, idx + 1)
                        if (raw.isNotEmpty()) _incomingRaw.emit(RawPacket(address, isInitiator, raw))
                    }
                }
            } catch (e: IOException) {
                if (isActive) Log.w(TAG, "Read ended for $address: ${e.message}")
            } finally {
                disconnect(address)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Send
    // -------------------------------------------------------------------------

    fun sendRaw(address: String, json: String) {
        scope.launch {
            val peer = activeSockets[address] ?: run {
                Log.w(TAG, "sendRaw: no socket for $address"); return@launch
            }
            try {
                val data = (json + Constants.PACKET_DELIMITER).toByteArray(Charsets.UTF_8)
                withContext(Dispatchers.IO) { peer.outputStream.write(data); peer.outputStream.flush() }
            } catch (e: IOException) {
                Log.e(TAG, "Send failed to $address", e)
                disconnect(address)
            }
        }
    }

    fun sendPacket(address: String, packet: MessagePacket) = sendRaw(address, packet.serialize())

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    fun disconnect(address: String) {
        val peer = activeSockets.remove(address) ?: return
        try { peer.socket.close() } catch (_: IOException) {}
        updatePeerSet()
        scope.launch { _connectionEvents.emit(ConnectionEvent.Disconnected(address)) }
    }

    fun stopAll() {
        acceptJob?.cancel()
        activeSockets.keys.toList().forEach { disconnect(it) }
        scope.cancel()
    }

    fun isConnected(address: String) = activeSockets.containsKey(address)
    fun isInitiator(address: String) = activeSockets[address]?.isInitiator ?: false

    private fun updatePeerSet() { _connectedPeers.value = activeSockets.keys.toSet() }

    private data class ConnectedPeer(
        val socket: BluetoothSocket,
        val isInitiator: Boolean,
        val outputStream: OutputStream = socket.outputStream
    )

    companion object { private const val TAG = "BtService" }
}

/** Raw bytes from a peer before validation / decryption in BlueMesManager. */
data class RawPacket(val senderAddress: String, val weAreInitiator: Boolean, val json: String)

sealed class ConnectionEvent {
    abstract val address: String
    data class Connecting   (override val address: String)                   : ConnectionEvent()
    data class Connected    (override val address: String)                   : ConnectionEvent()
    data class Disconnected (override val address: String)                   : ConnectionEvent()
    data class Failed       (override val address: String, val reason: String) : ConnectionEvent()
}
