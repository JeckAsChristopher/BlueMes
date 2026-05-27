package com.bluemes.app.ui.chat

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bluemes.app.BlueMesApplication
import com.bluemes.app.bluetooth.BluetoothService
import com.bluemes.app.bluetooth.ConnectionEvent
import com.bluemes.app.data.local.entities.MessageEntity
import com.bluemes.app.data.repository.ChatRepository
import com.bluemes.app.models.MessagePacket
import com.bluemes.app.models.PacketType
import com.bluemes.app.utils.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val context: Context,
    val deviceAddress: String,
    val remoteUserName: String
) : ViewModel() {

    private val db = BlueMesApplication.instance.database
    private val repo = ChatRepository(db.conversationDao(), db.messageDao())
    private val prefs = UserPreferences(context)

    val messages = repo.getMessages(deviceAddress)

    private val _connectionState = MutableStateFlow("Connecting…")
    val connectionState: StateFlow<String> = _connectionState

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    private var bluetoothService: BluetoothService? = null
    private var localAddress: String = "local"
    private var localName: String = "Me"

    init {
        viewModelScope.launch {
            localName = prefs.userName.first() ?: "Me"
            setupBluetoothService()
            repo.markConversationRead(deviceAddress)
        }
    }

    private fun setupBluetoothService() {
        val btManager = context.getSystemService(BluetoothManager::class.java)
        val btAdapter = btManager?.adapter ?: return
        localAddress = try { btAdapter.address } catch (_: SecurityException) { "local" }

        val service = BluetoothService(btAdapter, localName, localAddress)
        bluetoothService = service
        service.startServer()

        // Connect to the remote device
        val device = try { btAdapter.getRemoteDevice(deviceAddress) } catch (_: Exception) { null }
        if (device != null && !service.isConnected(deviceAddress)) {
            service.connect(device)
        }

        viewModelScope.launch {
            service.connectionEvents.collect { event ->
                when (event) {
                    is ConnectionEvent.Connected -> if (event.address == deviceAddress) {
                        _connectionState.value = "Connected"
                    }
                    is ConnectionEvent.Disconnected -> if (event.address == deviceAddress) {
                        _connectionState.value = "Disconnected"
                    }
                    is ConnectionEvent.Connecting -> if (event.address == deviceAddress) {
                        _connectionState.value = "Connecting…"
                    }
                    is ConnectionEvent.Failed -> if (event.address == deviceAddress) {
                        _connectionState.value = "Failed: ${event.reason}"
                    }
                }
            }
        }

        viewModelScope.launch {
            service.incomingMessages.collect { packet ->
                if (packet.senderAddress != deviceAddress) return@collect
                when (packet.type) {
                    PacketType.TEXT_MESSAGE -> repo.saveIncomingMessage(packet)
                    PacketType.TYPING_START -> _isTyping.value = true
                    PacketType.TYPING_STOP -> _isTyping.value = false
                    else -> {}
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || text.length > 2000) return
        val packet = MessagePacket(
            id = UUID.randomUUID().toString(),
            type = PacketType.TEXT_MESSAGE,
            senderAddress = localAddress,
            senderName = localName,
            content = text.trim()
        )
        viewModelScope.launch {
            bluetoothService?.sendPacket(deviceAddress, packet)
            repo.saveOutgoingMessage(
                recipientAddress = deviceAddress,
                recipientName = remoteUserName,
                messageId = packet.id,
                content = packet.content,
                senderAddress = localAddress,
                senderName = localName,
                timestamp = packet.timestamp
            )
        }
    }

    fun sendTypingStart() {
        val packet = MessagePacket(
            id = UUID.randomUUID().toString(),
            type = PacketType.TYPING_START,
            senderAddress = localAddress,
            senderName = localName,
            content = ""
        )
        bluetoothService?.sendPacket(deviceAddress, packet)
    }

    fun sendTypingStop() {
        val packet = MessagePacket(
            id = UUID.randomUUID().toString(),
            type = PacketType.TYPING_STOP,
            senderAddress = localAddress,
            senderName = localName,
            content = ""
        )
        bluetoothService?.sendPacket(deviceAddress, packet)
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothService?.stopAll()
    }
}

class ChatViewModelFactory(
    private val context: Context,
    private val address: String,
    private val userName: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ChatViewModel(context.applicationContext, address, userName) as T
    }
}
