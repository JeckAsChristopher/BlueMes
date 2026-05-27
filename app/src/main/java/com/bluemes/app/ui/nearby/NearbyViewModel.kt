package com.bluemes.app.ui.nearby

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bluemes.app.BlueMesApplication
import com.bluemes.app.bluetooth.BlueMesManager
import com.bluemes.app.bluetooth.ConnectionEvent
import com.bluemes.app.data.repository.ChatRepository
import com.bluemes.app.models.NearbyUser
import com.bluemes.app.models.PacketType
import com.bluemes.app.utils.UserPreferences
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NearbyViewModel(private val context: Context) : ViewModel() {

    private val db = BlueMesApplication.instance.database
    private val repo = ChatRepository(db.conversationDao(), db.messageDao())
    private val prefs = UserPreferences(context)

    // The shared manager — same instance used by ChatViewModel
    private val manager = BlueMesApplication.instance.bluetoothManager

    // Expose the manager's nearbyUsers directly; it is kept up-to-date
    // by HANDSHAKE packets as well as Bluetooth discovery broadcasts
    val nearbyUsers: StateFlow<Map<String, NearbyUser>> = manager.nearbyUsers

    fun startDiscoveryAndServer() {
        viewModelScope.launch {
            // Give the manager the local user name if not yet initialised
            if (!manager.isReady()) {
                manager.init(prefs)
                manager.start(context)
            } else {
                // Already running — just ensure discovery is scanning
                manager.restartDiscovery()
            }

            // Persist conversations for anyone who sends a handshake
            launch {
                manager.incomingMessages.collect { packet ->
                    when (packet.type) {
                        PacketType.HANDSHAKE, PacketType.HANDSHAKE_ACK -> {
                            repo.ensureConversation(packet.senderAddress, packet.senderName)
                        }
                        PacketType.TEXT_MESSAGE -> {
                            repo.saveIncomingMessage(packet)
                        }
                        else -> {}
                    }
                }
            }

            // Log connection events (optional — UI can also observe manager.connectionEvents)
            launch {
                manager.connectionEvents.collect { event ->
                    when (event) {
                        is ConnectionEvent.Connected ->
                            android.util.Log.d("NearbyVM", "Connected: ${event.address}")
                        is ConnectionEvent.Disconnected ->
                            android.util.Log.d("NearbyVM", "Disconnected: ${event.address}")
                        else -> {}
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Do NOT stop the manager here — ChatViewModel still needs it.
        // The manager lives as long as the Application.
    }
}

class NearbyViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return NearbyViewModel(context.applicationContext) as T
    }
}
