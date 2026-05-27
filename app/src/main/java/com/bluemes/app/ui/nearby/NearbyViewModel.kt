package com.bluemes.app.ui.nearby

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bluemes.app.BlueMesApplication
import com.bluemes.app.bluetooth.BluetoothDiscoveryManager
import com.bluemes.app.bluetooth.BluetoothService
import com.bluemes.app.bluetooth.ConnectionEvent
import com.bluemes.app.data.repository.ChatRepository
import com.bluemes.app.models.NearbyUser
import com.bluemes.app.models.PacketType
import com.bluemes.app.utils.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NearbyViewModel(private val context: Context) : ViewModel() {

    private val db = BlueMesApplication.instance.database
    private val repo = ChatRepository(db.conversationDao(), db.messageDao())
    private val prefs = UserPreferences(context)

    private val _nearbyUsers = MutableStateFlow<Map<String, NearbyUser>>(emptyMap())
    val nearbyUsers: StateFlow<Map<String, NearbyUser>> = _nearbyUsers

    private var discoveryManager: BluetoothDiscoveryManager? = null
    var bluetoothService: BluetoothService? = null
        private set

    fun startDiscoveryAndServer() {
        viewModelScope.launch {
            val userName = prefs.userName.first() ?: "User"
            val btManager = context.getSystemService(BluetoothManager::class.java)
            val btAdapter = btManager?.adapter ?: return@launch
            val localAddress = try { btAdapter.address } catch (_: SecurityException) { "unknown" }

            val service = BluetoothService(btAdapter, userName, localAddress)
            bluetoothService = service
            service.startServer()

            val discovery = BluetoothDiscoveryManager(context, btAdapter)
            discoveryManager = discovery
            discovery.startDiscovery()

            // Observe discovered devices
            launch {
                discovery.nearbyUsers.collect { map ->
                    _nearbyUsers.value = map
                }
            }

            // Handle connection events — update handshake-derived user names
            launch {
                service.incomingMessages.collect { packet ->
                    if (packet.type == PacketType.HANDSHAKE || packet.type == PacketType.HANDSHAKE_ACK) {
                        discovery.updateUserName(packet.senderAddress, packet.senderName)
                        repo.ensureConversation(packet.senderAddress, packet.senderName)
                    } else if (packet.type == PacketType.TEXT_MESSAGE) {
                        repo.saveIncomingMessage(packet)
                    }
                }
            }

            // Connection events
            launch {
                service.connectionEvents.collect { event ->
                    when (event) {
                        is ConnectionEvent.Disconnected -> { /* handled by discovery receiver */ }
                        else -> {}
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        discoveryManager?.stopDiscovery()
        bluetoothService?.stopAll()
    }
}

class NearbyViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return NearbyViewModel(context.applicationContext) as T
    }
}
