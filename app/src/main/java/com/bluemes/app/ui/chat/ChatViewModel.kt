package com.bluemes.app.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bluemes.app.BlueMesApplication
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
    private val ctx: Context,
    val deviceAddress: String,
    val remoteUserName: String
) : ViewModel() {

    private val db   = BlueMesApplication.instance.database
    private val repo = ChatRepository(db.conversationDao(), db.messageDao())
    private val prefs = UserPreferences(ctx)

    // Single shared manager — never create a new BluetoothService here
    private val mgr = BlueMesApplication.instance.bluetoothManager

    val messages = repo.getMessages(deviceAddress)

    private val _connState = MutableStateFlow("Connecting…")
    val connectionState: StateFlow<String> = _connState

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    // Notifies ChatFragment to show "X refused to chat with you"
    private val _denied = MutableStateFlow<String?>(null)
    val denied: StateFlow<String?> = _denied

    init {
        viewModelScope.launch {
            if (!mgr.isReady()) {
                mgr.init(prefs)
                mgr.start(ctx)
            }

            // FIX: Don't call service.connect() blindly.
            // If the session was established via the server-accept path (we are the acceptor),
            // isConnected() is already true and we should NOT try to connect as client —
            // that would race, fail with timeout, and show the wrong state.
            val svc = mgr.service
            when {
                svc == null -> _connState.value = "Bluetooth unavailable"
                svc.isConnected(deviceAddress) -> _connState.value = "Connected"
                else -> {
                    val btMgr = ctx.getSystemService(android.bluetooth.BluetoothManager::class.java)
                    val dev = try { btMgr?.adapter?.getRemoteDevice(deviceAddress) } catch (_: Exception) { null }
                    if (dev != null) svc.connect(dev) else _connState.value = "Device not found"
                }
            }

            repo.markRead(deviceAddress)

            // Observe connection events for THIS peer
            launch {
                mgr.connectionEvents.collect { ev ->
                    if (ev.address != deviceAddress) return@collect
                    _connState.value = when (ev) {
                        is ConnectionEvent.Connected    -> "Connected"
                        is ConnectionEvent.Connecting   -> "Connecting…"
                        is ConnectionEvent.Disconnected -> "Disconnected"
                        // FIX: only show "Failed" if we're genuinely not connected
                        is ConnectionEvent.Failed       ->
                            if (svc?.isConnected(deviceAddress) == true) "Connected"
                            else "Failed: ${ev.reason}"
                    }
                }
            }

            // Observe messages for this conversation
            launch {
                mgr.incomingMessages.collect { p ->
                    if (p.senderAddress != deviceAddress) return@collect
                    when (p.type) {
                        PacketType.TEXT_MESSAGE -> {
                            repo.saveIncoming(p)
                            // If we're still showing "Connecting…" but messages are flowing, fix it
                            if (_connState.value != "Connected") _connState.value = "Connected"
                        }
                        PacketType.TYPING_START -> _isTyping.value = true
                        PacketType.TYPING_STOP  -> _isTyping.value = false
                        else -> {}
                    }
                }
            }

            // Observe denial events
            launch {
                mgr.connectionDenied.collect { addr ->
                    if (addr == deviceAddress) _denied.value = remoteUserName
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || text.length > 2000) return
        val packet = MessagePacket(
            id = UUID.randomUUID().toString(),
            type = PacketType.TEXT_MESSAGE,
            senderAddress = mgr.localAddress,
            senderName = mgr.localUserName,
            content = text.trim()
        )
        viewModelScope.launch {
            mgr.sendMessage(deviceAddress, packet) // manager handles AES encryption
            repo.saveOutgoing(
                recipientAddress = deviceAddress,
                recipientName = remoteUserName,
                messageId = packet.id,
                content = packet.content,   // store plaintext locally
                senderAddress = mgr.localAddress,
                senderName = mgr.localUserName,
                timestamp = packet.timestamp
            )
        }
    }

    fun sendTyping(typing: Boolean) {
        val type = if (typing) PacketType.TYPING_START else PacketType.TYPING_STOP
        val p = MessagePacket(
            id = UUID.randomUUID().toString(), type = type,
            senderAddress = mgr.localAddress, senderName = mgr.localUserName, content = ""
        )
        mgr.sendPacket(deviceAddress, p)
    }
}

class ChatViewModelFactory(private val ctx: Context, private val addr: String, private val name: String) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(c: Class<T>): T {
        @Suppress("UNCHECKED_CAST") return ChatViewModel(ctx.applicationContext, addr, name) as T
    }
}
