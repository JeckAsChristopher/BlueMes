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
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Core Bluetooth service.
 *
 * Key fix: only ONE instance of this class should exist in the whole app,
 * owned by BlueMesManager. All ViewModels share this single instance so that
 * connections made while the user is on NearbyFragment are still alive when
 * they navigate to ChatFragment, and vice-versa.
 */
class BluetoothService(
    private val bluetoothAdapter: BluetoothAdapter,
    val localUserName: String,
    val localAddress: String
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var acceptJob: Job? = null

    private val activeSockets = ConcurrentHashMap<String, ConnectedPeer>()

    private val _incomingMessages = MutableSharedFlow<MessagePacket>(extraBufferCapacity = 128)
    val incomingMessages: SharedFlow<MessagePacket> = _incomingMessages

    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 64)
    val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents

    private val _connectedPeers = MutableStateFlow<Set<String>>(emptySet())
    val connectedPeers: StateFlow<Set<String>> = _connectedPeers

    // -------------------------------------------------------------------------
    // Server-side: accept incoming connections in a loop
    // -------------------------------------------------------------------------

    fun startServer() {
        acceptJob?.cancel()
        acceptJob = scope.launch {
            var serverSocket: BluetoothServerSocket? = null
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                    Constants.BLUEMES_SERVICE_NAME,
                    Constants.BLUEMES_UUID
                )
                Log.d(TAG, "Server listening as '$localUserName' ($localAddress)")

                while (isActive) {
                    val socket = try {
                        serverSocket.accept()
                    } catch (e: IOException) {
                        if (isActive) Log.e(TAG, "accept() failed", e)
                        break
                    }
                    val addr = socket.remoteDevice.address
                    if (!activeSockets.containsKey(addr)) {
                        Log.d(TAG, "Accepted connection from $addr")
                        launchPeer(socket, isInitiator = false)
                    } else {
                        // Already connected — reject duplicate
                        try { socket.close() } catch (_: IOException) {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket error", e)
            } finally {
                try { serverSocket?.close() } catch (_: IOException) {}
            }
        }
    }

    // -------------------------------------------------------------------------
    // Client-side: connect to a remote device
    // -------------------------------------------------------------------------

    fun connect(device: BluetoothDevice) {
        val address = device.address
        if (activeSockets.containsKey(address)) {
            Log.d(TAG, "Already connected to $address, skipping")
            return
        }
        scope.launch {
            _connectionEvents.emit(ConnectionEvent.Connecting(address))
            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(Constants.BLUEMES_UUID)
                // Cancel ongoing discovery — it slows connection
                try { bluetoothAdapter.cancelDiscovery() } catch (_: SecurityException) {}
                withTimeout(Constants.CONNECTION_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) { socket.connect() }
                }
                Log.d(TAG, "Connected to $address")
                launchPeer(socket, isInitiator = true)
            } catch (e: Exception) {
                Log.e(TAG, "connect() failed for $address", e)
                try { socket?.close() } catch (_: IOException) {}
                _connectionEvents.emit(ConnectionEvent.Failed(address, e.message ?: "Unknown"))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Per-peer coroutine: send handshake then loop-read
    // -------------------------------------------------------------------------

    private fun launchPeer(socket: BluetoothSocket, isInitiator: Boolean) {
        val address = socket.remoteDevice.address
        val peer = ConnectedPeer(socket)
        activeSockets[address] = peer
        updatePeerSet()

        scope.launch {
            _connectionEvents.emit(ConnectionEvent.Connected(address))

            // Initiator sends handshake first; acceptor sends handshake_ack
            val handshakeType = if (isInitiator) PacketType.HANDSHAKE else PacketType.HANDSHAKE_ACK
            sendPacketInternal(address, buildHandshake(handshakeType))

            val inputStream: InputStream = try {
                socket.inputStream
            } catch (e: IOException) {
                Log.e(TAG, "Cannot get inputStream for $address", e)
                disconnect(address)
                return@launch
            }

            val buf = StringBuilder()
            val bytes = ByteArray(Constants.SOCKET_BUFFER_SIZE)

            try {
                while (isActive && socket.isConnected) {
                    val n = withContext(Dispatchers.IO) { inputStream.read(bytes) }
                    if (n == -1) break
                    buf.append(String(bytes, 0, n, Charsets.UTF_8))

                    // Extract complete newline-delimited packets
                    var idx: Int
                    while (buf.indexOf(Constants.PACKET_DELIMITER).also { idx = it } != -1) {
                        val raw = buf.substring(0, idx).trim()
                        buf.delete(0, idx + 1)
                        if (raw.isNotEmpty()) handleRaw(address, raw)
                    }
                }
            } catch (e: IOException) {
                if (isActive) Log.w(TAG, "Read loop ended for $address: ${e.message}")
            } finally {
                disconnect(address)
            }
        }
    }

    private suspend fun handleRaw(senderAddr: String, raw: String) {
        val packet = MessagePacket.deserialize(raw) ?: run {
            Log.w(TAG, "Malformed packet from $senderAddr — discarded")
            return
        }
        when (packet.type) {
            PacketType.DISCONNECT -> disconnect(senderAddr)
            else -> _incomingMessages.emit(packet)
        }
    }

    // -------------------------------------------------------------------------
    // Send helpers
    // -------------------------------------------------------------------------

    fun sendPacket(address: String, packet: MessagePacket) {
        scope.launch { sendPacketInternal(address, packet) }
    }

    private suspend fun sendPacketInternal(address: String, packet: MessagePacket) {
        val peer = activeSockets[address] ?: run {
            Log.w(TAG, "sendPacket: no socket for $address")
            return
        }
        try {
            val data = (packet.serialize() + Constants.PACKET_DELIMITER).toByteArray(Charsets.UTF_8)
            withContext(Dispatchers.IO) {
                peer.outputStream.write(data)
                peer.outputStream.flush()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Send failed to $address", e)
            disconnect(address)
        }
    }

    // -------------------------------------------------------------------------
    // Disconnect
    // -------------------------------------------------------------------------

    fun disconnect(address: String) {
        val peer = activeSockets.remove(address) ?: return
        try { peer.socket.close() } catch (_: IOException) {}
        updatePeerSet()
        scope.launch { _connectionEvents.emit(ConnectionEvent.Disconnected(address)) }
        Log.d(TAG, "Disconnected from $address")
    }

    fun stopAll() {
        acceptJob?.cancel()
        activeSockets.keys.toList().forEach { disconnect(it) }
        scope.cancel()
        Log.d(TAG, "BluetoothService stopped")
    }

    fun isConnected(address: String) = activeSockets.containsKey(address)

    private fun updatePeerSet() {
        _connectedPeers.value = activeSockets.keys.toSet()
    }

    private fun buildHandshake(type: PacketType = PacketType.HANDSHAKE) = MessagePacket(
        id = UUID.randomUUID().toString(),
        type = type,
        senderAddress = localAddress,
        senderName = localUserName,   // ← always the user's chosen BlueMes name
        content = localUserName
    )

    private data class ConnectedPeer(
        val socket: BluetoothSocket,
        val outputStream: OutputStream = socket.outputStream
    )

    companion object {
        private const val TAG = "BtService"
    }
}

// ---------------------------------------------------------------------------
// Connection events
// ---------------------------------------------------------------------------

sealed class ConnectionEvent {
    abstract val address: String
    data class Connecting(override val address: String) : ConnectionEvent()
    data class Connected(override val address: String) : ConnectionEvent()
    data class Disconnected(override val address: String) : ConnectionEvent()
    data class Failed(override val address: String, val reason: String) : ConnectionEvent()
}
