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
 * Core Bluetooth service managing server socket acceptance, outbound connections,
 * and per-peer read/write threads.
 */
class BluetoothService(
    private val bluetoothAdapter: BluetoothAdapter,
    private val localUserName: String,
    private val localAddress: String
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var acceptJob: Job? = null

    // Active sockets keyed by remote device address
    private val activeSockets = ConcurrentHashMap<String, ConnectedPeer>()

    // Flows consumed by the ViewModel
    private val _incomingMessages = MutableSharedFlow<MessagePacket>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<MessagePacket> = _incomingMessages

    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 32)
    val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents

    private val _connectedPeers = MutableStateFlow<Set<String>>(emptySet())
    val connectedPeers: StateFlow<Set<String>> = _connectedPeers

    // -------------------------------------------------------------------------
    // Server-side: listen for incoming connections
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
                Log.d(TAG, "Server socket listening")
                while (isActive) {
                    val socket = try {
                        serverSocket.accept()
                    } catch (e: IOException) {
                        if (isActive) Log.e(TAG, "Accept failed", e)
                        break
                    }
                    val address = socket.remoteDevice.address
                    if (!activeSockets.containsKey(address)) {
                        Log.d(TAG, "Accepted connection from $address")
                        launchConnectedPeer(socket, isInitiator = false)
                    } else {
                        socket.close()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server failed", e)
            } finally {
                serverSocket?.close()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Client-side: connect to a remote device
    // -------------------------------------------------------------------------

    fun connect(device: BluetoothDevice) {
        val address = device.address
        if (activeSockets.containsKey(address)) return

        scope.launch {
            _connectionEvents.emit(ConnectionEvent.Connecting(address))
            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(Constants.BLUEMES_UUID)
                bluetoothAdapter.cancelDiscovery()
                withTimeout(Constants.CONNECTION_TIMEOUT_MS) { socket.connect() }
                Log.d(TAG, "Connected to $address")
                launchConnectedPeer(socket, isInitiator = true)
            } catch (e: Exception) {
                Log.e(TAG, "Connect failed to $address", e)
                socket?.close()
                _connectionEvents.emit(ConnectionEvent.Failed(address, e.message ?: "Unknown error"))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Per-peer coroutine
    // -------------------------------------------------------------------------

    private fun launchConnectedPeer(socket: BluetoothSocket, isInitiator: Boolean) {
        val address = socket.remoteDevice.address
        val peer = ConnectedPeer(socket)
        activeSockets[address] = peer
        updatePeerSet()

        scope.launch {
            _connectionEvents.emit(ConnectionEvent.Connected(address))

            // If we initiated, send a handshake immediately
            if (isInitiator) {
                sendPacket(address, buildHandshake())
            }

            // Read loop
            val inputStream: InputStream = try { socket.inputStream } catch (e: IOException) {
                Log.e(TAG, "Cannot get input stream", e)
                disconnect(address)
                return@launch
            }

            val buffer = StringBuilder()
            val byteBuffer = ByteArray(Constants.SOCKET_BUFFER_SIZE)

            try {
                while (isActive && socket.isConnected) {
                    val bytesRead = inputStream.read(byteBuffer)
                    if (bytesRead == -1) break
                    buffer.append(String(byteBuffer, 0, bytesRead, Charsets.UTF_8))

                    // Split on newline delimiter to extract complete packets
                    var delimIdx: Int
                    while (buffer.indexOf(Constants.PACKET_DELIMITER).also { delimIdx = it } != -1) {
                        val raw = buffer.substring(0, delimIdx).trim()
                        buffer.delete(0, delimIdx + 1)
                        if (raw.isNotEmpty()) handleIncoming(address, raw)
                    }
                }
            } catch (e: IOException) {
                if (isActive) Log.w(TAG, "Peer $address disconnected", e)
            } finally {
                disconnect(address)
            }
        }
    }

    private suspend fun handleIncoming(senderAddress: String, raw: String) {
        val packet = MessagePacket.deserialize(raw) ?: run {
            Log.w(TAG, "Malformed packet from $senderAddress, ignoring")
            return
        }

        when (packet.type) {
            PacketType.HANDSHAKE -> {
                // Reply with ack
                sendPacket(senderAddress, buildHandshake(PacketType.HANDSHAKE_ACK))
                _incomingMessages.emit(packet)
            }
            PacketType.DISCONNECT -> {
                disconnect(senderAddress)
            }
            else -> {
                _incomingMessages.emit(packet)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Send
    // -------------------------------------------------------------------------

    fun sendPacket(address: String, packet: MessagePacket) {
        scope.launch { sendPacketSuspend(address, packet) }
    }

    private suspend fun sendPacketSuspend(address: String, packet: MessagePacket) {
        val peer = activeSockets[address] ?: run {
            Log.w(TAG, "No socket for $address")
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
    }

    fun stopAll() {
        acceptJob?.cancel()
        activeSockets.keys.toList().forEach { disconnect(it) }
        scope.cancel()
    }

    fun isConnected(address: String) = activeSockets.containsKey(address)

    private fun updatePeerSet() {
        _connectedPeers.value = activeSockets.keys.toSet()
    }

    private fun buildHandshake(type: PacketType = PacketType.HANDSHAKE) = MessagePacket(
        id = UUID.randomUUID().toString(),
        type = type,
        senderAddress = localAddress,
        senderName = localUserName,
        content = localUserName
    )

    private data class ConnectedPeer(
        val socket: BluetoothSocket,
        val outputStream: OutputStream = socket.outputStream
    )

    companion object {
        private const val TAG = "BluetoothService"
    }
}

sealed class ConnectionEvent {
    data class Connecting(val address: String) : ConnectionEvent()
    data class Connected(val address: String) : ConnectionEvent()
    data class Disconnected(val address: String) : ConnectionEvent()
    data class Failed(val address: String, val reason: String) : ConnectionEvent()
}
