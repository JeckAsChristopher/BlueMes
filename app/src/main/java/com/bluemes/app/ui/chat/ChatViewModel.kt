package com.bluemes.app.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bluemes.app.BlueMesApplication
import com.bluemes.app.bluetooth.BlueMesManager
import com.bluemes.app.bluetooth.ConnectionEvent
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

    // *** Use the shared singleton — NOT a new service instance ***
    private val manager = BlueMesApplication.instance.bluetoothManager

    val messages = repo.getMessages(deviceAddress)

    private val _connectionState = MutableStateFlow("Connecting…")
    val connectionState: StateFlow<String> = _connectionState

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    init {
        viewModelScope.launch {
            // Ensure manager is running (it may already be from NearbyFragment)
            if (!manager.isReady()) {
                manager.init(prefs)
                manager.start(context)
            }

            // Connect outward if not already connected
            val service = manager.service
            if (service != null && !service.isConnected(deviceAddress)) {
                val btMgr = context.getSystemService(android.bluetooth.BluetoothManager::class.java)
                val device = try {
                    btMgr?.adapter?.getRemoteDevice(deviceAddress)
                } catch (_: Exception) { null }

                if (device != null) {
                    service.connect(device)
                } else {
                    _connectionState.value = "Unable to find device"
                }
            } else if (service?.isConnected(deviceAddress) == true) {
                _connectionState.value = "Connected"
            }

            repo.markConversationRead(deviceAddress)

            // Watch connection events from the shared manager
            launch {
                manager.connectionEvents.collect { event ->
                    if (event.address == deviceAddress) {
                        _connectionState.value = when (event) {
                            is ConnectionEvent.Connected -> "Connected"
                            is ConnectionEvent.Connecting -> "Connecting…"
                            is ConnectionEvent.Disconnected -> "Disconnected"
                            is ConnectionEvent.Failed -> "Failed: ${event.reason}"
                        }
                    }
                }
            }

            // Watch messages from the shared manager for THIS conversation
            launch {
                manager.incomingMessages.collect { packet ->
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
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || text.length > 2000) return
        val localAddress = manager.localAddress
        val localName = manager.localUserName

        val packet = MessagePacket(
            id = UUID.randomUUID().toString(),
            type = PacketType.TEXT_MESSAGE,
            senderAddress = localAddress,
            senderName = localName,
            content = text.trim()
        )
        viewModelScope.launch {
            manager.service?.sendPacket(deviceAddress, packet)
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
            senderAddress = manager.localAddress,
            senderName = manager.localUserName,
            content = ""
        )
        manager.service?.sendPacket(deviceAddress, packet)
    }

    fun sendTypingStop() {
        val packet = MessagePacket(
            id = UUID.randomUUID().toString(),
            type = PacketType.TYPING_STOP,
            senderAddress = manager.localAddress,
            senderName = manager.localUserName,
            content = ""
        )
        manager.service?.sendPacket(deviceAddress, packet)
    }

    override fun onCleared() {
        super.onCleared()
        // Do NOT stop the manager — NearbyFragment still needs it running
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
