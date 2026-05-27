package com.bluemes.app.bluetooth

import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.bluemes.app.models.ConnectionState
import com.bluemes.app.models.MessagePacket
import com.bluemes.app.models.NearbyUser
import com.bluemes.app.models.PacketType
import com.bluemes.app.utils.UserPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * App-lifetime singleton that owns the single BluetoothService and
 * BluetoothDiscoveryManager.
 *
 * THE CORE ARCHITECTURE FIX:
 * Previously, NearbyViewModel and ChatViewModel each created their own
 * BluetoothService instances. This meant:
 *  - The server socket accepting connections lived in NearbyVM's service
 *  - ChatViewModel's service never saw those connections
 *  - The receiver's UI never updated because its nearbyUsers only came from
 *    BT discovery broadcasts, not from incoming socket connections
 *
 * Now there is exactly ONE service instance. Both ViewModels subscribe to
 * this manager's shared flows. When a device connects to us (we are the
 * acceptor / receiver), the HANDSHAKE packet triggers addOrUpdateUser(),
 * which pushes the device into nearbyUsers — making the receiver's
 * NearbyFragment update in real time, even when the remote device was
 * never visible via Bluetooth discovery.
 */
class BlueMesManager private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // The single service and discovery manager for the whole app
    private var _service: BluetoothService? = null
    private var _discoveryManager: BluetoothDiscoveryManager? = null

    val service: BluetoothService? get() = _service

    // Unified nearby-users state: merges discovery results + handshake-derived users
    private val _nearbyUsers = MutableStateFlow<Map<String, NearbyUser>>(emptyMap())
    val nearbyUsers: StateFlow<Map<String, NearbyUser>> = _nearbyUsers

    // All incoming packets forwarded here — both NearbyVM and ChatVM subscribe
    private val _incomingMessages = MutableSharedFlow<MessagePacket>(extraBufferCapacity = 256)
    val incomingMessages: SharedFlow<MessagePacket> = _incomingMessages

    // All connection state changes forwarded here
    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 64)
    val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents

    // Exposed so ChatViewModel can stamp outgoing packets correctly
    var localAddress: String = "local"
        private set
    var localUserName: String = "User"
        private set

    private var started = false

    fun isReady(): Boolean = _service != null

    /** Must be called before start() to give the manager the user's chosen name. */
    suspend fun init(prefs: UserPreferences) {
        localUserName = prefs.userName.first() ?: "User"
    }

    /** Start the BT server + discovery. Safe to call multiple times — subsequent calls are no-ops. */
    fun start(context: Context) {
        if (started) return
        started = true

        val btMgr = context.getSystemService(BluetoothManager::class.java)
        val btAdapter = btMgr?.adapter ?: run {
            Log.e(TAG, "No Bluetooth adapter — cannot start")
            started = false
            return
        }

        localAddress = try { btAdapter.address } catch (_: SecurityException) { "local" }
        Log.d(TAG, "Starting as '$localUserName' ($localAddress)")

        val svc = BluetoothService(btAdapter, localUserName, localAddress)
        _service = svc
        svc.startServer()

        val disc = BluetoothDiscoveryManager(context, btAdapter)
        _discoveryManager = disc
        disc.startDiscovery()

        // Mirror discovery map into our unified nearbyUsers
        scope.launch {
            disc.nearbyUsers.collect { map ->
                // Merge: keep any handshake-derived entries that are NOT in discovery results
                _nearbyUsers.update { current ->
                    val merged = current.toMutableMap()
                    map.forEach { (addr, user) ->
                        // Only overwrite if we don't already have a better (handshake-confirmed) name
                        val existing = merged[addr]
                        if (existing == null) {
                            merged[addr] = user
                        } else {
                            // Preserve the real BlueMes userName if it was set via handshake,
                            // but update RSSI / timestamp from discovery
                            merged[addr] = existing.copy(
                                rssi = user.rssi,
                                lastSeenTimestamp = user.lastSeenTimestamp
                            )
                        }
                    }
                    merged
                }
            }
        }

        // Process incoming packets: update names, push users into UI, forward to subscribers
        scope.launch {
            svc.incomingMessages.collect { packet ->
                when (packet.type) {
                    PacketType.HANDSHAKE, PacketType.HANDSHAKE_ACK -> {
                        // *** FIX 1: Receiver-side real-time update ***
                        // When someone connects TO US (we are the acceptor), they will never
                        // appear via BT discovery because discovery only finds devices that are
                        // explicitly set discoverable. By adding them here from the handshake
                        // packet, the receiver's NearbyFragment list updates immediately.
                        _nearbyUsers.update { current ->
                            val existing = current[packet.senderAddress]
                            val updated = existing?.copy(
                                userName = packet.senderName,       // ← real BlueMes name
                                connectionState = ConnectionState.CONNECTED,
                                lastSeenTimestamp = System.currentTimeMillis()
                            ) ?: NearbyUser(
                                deviceAddress = packet.senderAddress,
                                deviceName = packet.senderAddress,  // fallback until discovery
                                userName = packet.senderName,       // ← real BlueMes name
                                connectionState = ConnectionState.CONNECTED
                            )
                            current.toMutableMap().also { it[packet.senderAddress] = updated }
                        }
                        // Also keep the discovery manager's own map in sync
                        disc.updateUserName(packet.senderAddress, packet.senderName)
                    }
                    else -> {}
                }
                // Forward every packet (including handshakes) to all subscribers
                _incomingMessages.emit(packet)
            }
        }

        // *** FIX 2: Update connection state in nearbyUsers on connect/disconnect ***
        scope.launch {
            svc.connectionEvents.collect { event ->
                when (event) {
                    is ConnectionEvent.Connected -> {
                        _nearbyUsers.update { current ->
                            current[event.address]?.let { user ->
                                current.toMutableMap().also {
                                    it[event.address] = user.copy(
                                        connectionState = ConnectionState.CONNECTED
                                    )
                                }
                            } ?: current
                        }
                    }
                    is ConnectionEvent.Disconnected -> {
                        _nearbyUsers.update { current ->
                            current[event.address]?.let { user ->
                                current.toMutableMap().also {
                                    it[event.address] = user.copy(
                                        connectionState = ConnectionState.DISCONNECTED
                                    )
                                }
                            } ?: current
                        }
                    }
                    is ConnectionEvent.Connecting -> {
                        _nearbyUsers.update { current ->
                            current[event.address]?.let { user ->
                                current.toMutableMap().also {
                                    it[event.address] = user.copy(
                                        connectionState = ConnectionState.CONNECTING
                                    )
                                }
                            } ?: current
                        }
                    }
                    else -> {}
                }
                _connectionEvents.emit(event)
            }
        }

        Log.d(TAG, "BlueMesManager started")
    }

    /** Re-trigger discovery scan (e.g. on returning to NearbyFragment). */
    fun restartDiscovery() {
        _discoveryManager?.startDiscovery() ?: run {
            // Discovery manager not yet created — nothing to do; start() will create it
            Log.w(TAG, "restartDiscovery called before start()")
        }
    }

    fun stopAll() {
        _discoveryManager?.stopDiscovery()
        _service?.stopAll()
        scope.cancel()
        started = false
        _service = null
        _discoveryManager = null
    }

    companion object {
        private const val TAG = "BlueMesManager"

        @Volatile
        private var INSTANCE: BlueMesManager? = null

        fun getInstance(context: Context): BlueMesManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: BlueMesManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
