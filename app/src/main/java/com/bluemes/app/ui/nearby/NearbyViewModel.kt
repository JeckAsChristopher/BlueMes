package com.bluemes.app.ui.nearby

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bluemes.app.BlueMesApplication
import com.bluemes.app.bluetooth.BlueMesManager
import com.bluemes.app.data.repository.ChatRepository
import com.bluemes.app.models.ConnectionState
import com.bluemes.app.models.NearbyUser
import com.bluemes.app.models.PacketType
import com.bluemes.app.utils.UserPreferences
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NearbyViewModel(private val ctx: Context) : ViewModel() {
    private val db   = BlueMesApplication.instance.database
    private val repo = ChatRepository(db.conversationDao(), db.messageDao())
    private val prefs = UserPreferences(ctx)
    private val mgr  = BlueMesApplication.instance.bluetoothManager

    /** Only verified BlueMes users — non-app BT devices never appear here */
    val nearbyUsers: StateFlow<Map<String, NearbyUser>> = mgr.nearbyUsers

    /** Pending connection requests that need UI approval */
    val pendingRequests: SharedFlow<BlueMesManager.PendingRequest> = mgr.pendingRequests

    fun startDiscoveryAndServer() {
        viewModelScope.launch {
            if (!mgr.isReady()) {
                mgr.init(prefs)
                mgr.start(ctx)
            } else {
                mgr.restartDiscovery()
            }

            // Persist conversations only for fully established sessions.
            // saveIncoming already calls ensureConversation internally.
            launch {
                mgr.incomingMessages.collect { p ->
                    when (p.type) {
                        PacketType.TEXT_MESSAGE -> repo.saveIncoming(p)
                        else -> {}
                    }
                }
            }

            // Create/update the conversation record when a session is fully established
            // (i.e. when the remote peer appears as CONNECTED and verified)
            launch {
                mgr.nearbyUsers.collect { users ->
                    users.values
                        .filter { it.isVerified && it.connectionState == ConnectionState.CONNECTED }
                        .forEach { user -> repo.ensureConversation(user.deviceAddress, user.userName) }
                }
            }
        }
    }

    fun approveConnection(address: String) = mgr.approveConnection(address)
    fun denyConnection(address: String)    = mgr.denyConnection(address)
}

class NearbyViewModelFactory(private val ctx: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(c: Class<T>): T {
        @Suppress("UNCHECKED_CAST") return NearbyViewModel(ctx.applicationContext) as T
    }
}
