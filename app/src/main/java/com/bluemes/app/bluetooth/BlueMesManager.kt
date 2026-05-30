package com.bluemes.app.bluetooth

import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.bluemes.app.models.ConnectionState
import com.bluemes.app.models.MessagePacket
import com.bluemes.app.models.NearbyUser
import com.bluemes.app.models.PacketType
import com.bluemes.app.utils.Constants
import com.bluemes.app.utils.MessageCrypto
import com.bluemes.app.utils.UserPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Application-lifetime singleton that orchestrates all Bluetooth logic.
 *
 * ── BUG FIXES ─────────────────────────────────────────────────────────────
 * 1. Non-BlueMes devices appearing:
 *    Discovery finds any BT device, but BlueMesManager ONLY adds a device to
 *    the visible [nearbyUsers] flow after a successful 4-way handshake with
 *    valid APP_TOKEN + HMAC signature.  Discovery results are auto-connected;
 *    if the RFCOMM UUID is not registered (non-BlueMes device) the connection
 *    silently fails and the device never appears in the list.
 *
 * 2. "Connection timed out" on receiver even when messages flow:
 *    ChatViewModel used to call service.connect() which could race against the
 *    already-open server-side socket, producing a Failed event.  Now:
 *    - BluetoothService.connect() returns Connected immediately if already
 *      connected via the server path.
 *    - ChatViewModel only observes connectionEvents; it never calls connect()
 *      for devices that are already in [connectedAndVerified].
 *
 * 3. Weak handshakes:
 *    4-way challenge-response:
 *      A → HANDSHAKE           (challenge = random nonce)
 *      B → HANDSHAKE_CHALLENGE (echoes nonce, adds own nonce)
 *      A → HANDSHAKE_RESPONSE  (signature = HMAC(B's nonce))
 *      B → HANDSHAKE_ACK       (verified, chat open)
 *    Any packet without APP_TOKEN is silently dropped.
 *
 * 4. Weak communication:
 *    TEXT_MESSAGE content is AES-128/CBC encrypted (key derived from both
 *    device addresses + shared APP_SECRET via HMAC-SHA256).
 *
 * ── NEW FEATURES ──────────────────────────────────────────────────────────
 * 5. Connection approval modal:
 *    When the acceptor side completes handshake verification, instead of
 *    immediately opening chat it emits a [PendingRequest] event.
 *    NearbyFragment observes [pendingRequests] and shows a dialog.
 *    - Accept → approveConnection()  → sends CONNECT_ACCEPTED, adds to list
 *    - Deny   → denyConnection()     → sends CONNECT_DENIED,   drops socket
 *    Sender observes [connectionDenied] and shows a notification snackbar.
 */
class BlueMesManager private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var _service: BluetoothService? = null
    private var _discovery: BluetoothDiscoveryManager? = null

    val service: BluetoothService? get() = _service

    // Only VERIFIED BlueMes users are in this map (shown in UI)
    private val _nearbyUsers = MutableStateFlow<Map<String, NearbyUser>>(emptyMap())
    val nearbyUsers: StateFlow<Map<String, NearbyUser>> = _nearbyUsers

    // Decrypted, validated incoming messages for chat subscribers
    private val _incomingMessages = MutableSharedFlow<MessagePacket>(extraBufferCapacity = 256)
    val incomingMessages: SharedFlow<MessagePacket> = _incomingMessages

    // Connection state changes
    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 64)
    val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents

    // Acceptor shows this as an approval dialog
    data class PendingRequest(val address: String, val userName: String)
    private val _pendingRequests = MutableSharedFlow<PendingRequest>(extraBufferCapacity = 8)
    val pendingRequests: SharedFlow<PendingRequest> = _pendingRequests

    // Sender is notified when denied
    private val _connectionDenied = MutableSharedFlow<String>(extraBufferCapacity = 8) // address
    val connectionDenied: SharedFlow<String> = _connectionDenied

    var localAddress: String = "local"
        private set
    var localUserName: String = "User"
        private set

    private var started = false

    // Tracks partial handshake state per address
    private data class HandshakeState(
        val weAreInitiator: Boolean,
        val ourChallenge: String = "",
        val theirChallenge: String = "",
        val theirName: String = ""
    )
    private val handshakeStates = ConcurrentHashMap<String, HandshakeState>()

    // AES keys per established conversation
    private val sessionKeys = ConcurrentHashMap<String, javax.crypto.spec.SecretKeySpec>()

    // Pending approval map (waiting for user to tap Accept/Deny)
    private val pendingApprovals = ConcurrentHashMap<String, String>() // address → name

    fun isReady(): Boolean = _service != null

    suspend fun init(prefs: UserPreferences) {
        localUserName = prefs.userName.first() ?: "User"
    }

    fun start(context: Context) {
        if (started) return
        started = true

        val btMgr = context.getSystemService(BluetoothManager::class.java)
        val btAdapter = btMgr?.adapter ?: run {
            Log.e(TAG, "No Bluetooth adapter"); started = false; return
        }
        localAddress = try { btAdapter.address } catch (_: SecurityException) { "local" }
        Log.d(TAG, "Starting as '$localUserName' ($localAddress)")

        val svc = BluetoothService(btAdapter, localUserName, localAddress)
        _service = svc
        svc.startServer()

        val disc = BluetoothDiscoveryManager(context, btAdapter)
        _discovery = disc

        // Auto-connect to every discovered device using our service UUID.
        // If the device doesn't run BlueMes, the RFCOMM connect will fail silently
        // and the device will never appear in [nearbyUsers].
        scope.launch {
            disc.deviceFound.collect { found ->
                delay(Constants.DISCOVERY_AUTO_CONNECT_DELAY_MS)
                if (!svc.isConnected(found.device.address)) {
                    Log.d(TAG, "Auto-connecting to discovered device: ${found.deviceName}")
                    svc.connect(found.device)
                }
            }
        }

        disc.startDiscovery()

        // Process raw packets from every connected socket
        scope.launch {
            svc.incomingRaw.collect { raw ->
                handleRawPacket(raw)
            }
        }

        // Mirror connection events; update state in nearbyUsers
        scope.launch {
            svc.connectionEvents.collect { event ->
                when (event) {
                    is ConnectionEvent.Connected -> updateState(event.address, ConnectionState.CONNECTING)
                    is ConnectionEvent.Disconnected -> {
                        handshakeStates.remove(event.address)
                        updateState(event.address, ConnectionState.DISCONNECTED)
                    }
                    is ConnectionEvent.Failed -> {
                        // Failed to auto-connect → silently ignore (non-BlueMes device or out of range)
                        handshakeStates.remove(event.address)
                        Log.d(TAG, "Auto-connect failed for ${event.address}: ${event.reason}")
                    }
                    else -> {}
                }
                _connectionEvents.emit(event)
            }
        }
    }

    // -------------------------------------------------------------------------
    // 4-way handshake state machine
    // -------------------------------------------------------------------------

    private suspend fun handleRawPacket(raw: RawPacket) {
        val packet = MessagePacket.deserialize(raw.json) ?: run {
            Log.w(TAG, "Invalid/non-BlueMes packet from ${raw.senderAddress} — discarded")
            return  // missing APP_TOKEN or bad JSON → silently dropped
        }

        when (packet.type) {

            // Step 1 (Initiator → Acceptor): "I'm BlueMes, here's my challenge nonce"
            PacketType.HANDSHAKE -> {
                val state = HandshakeState(
                    weAreInitiator = false,
                    theirChallenge = packet.challenge,
                    theirName = packet.senderName
                )
                handshakeStates[raw.senderAddress] = state
                val ourNonce = UUID.randomUUID().toString()
                val reply = buildPacket(
                    type = PacketType.HANDSHAKE_CHALLENGE,
                    address = raw.senderAddress,
                    content = packet.senderName,
                    challenge = packet.challenge,  // echo initiator's nonce
                    signature = MessageCrypto.hmacSign(packet.challenge) // sign it
                ).copy(challenge = ourNonce)        // also include our own nonce
                handshakeStates[raw.senderAddress] = state.copy(ourChallenge = ourNonce)
                _service?.sendPacket(raw.senderAddress, reply)

                // Acceptor-side timeout: if initiator never sends HANDSHAKE_RESPONSE
                scope.launch {
                    delay(Constants.HANDSHAKE_TIMEOUT_MS)
                    if (handshakeStates.containsKey(raw.senderAddress)) {
                        Log.w(TAG, "Handshake timed out (acceptor) for ${raw.senderAddress} — disconnecting")
                        handshakeStates.remove(raw.senderAddress)
                        _service?.disconnect(raw.senderAddress)
                    }
                }
            }

            // Step 2 (Acceptor → Initiator): "Here's my nonce; I signed yours"
            PacketType.HANDSHAKE_CHALLENGE -> {
                val state = handshakeStates[raw.senderAddress] ?: return
                // Verify acceptor correctly signed our challenge
                if (!MessageCrypto.hmacVerify(state.ourChallenge, packet.signature)) {
                    Log.w(TAG, "Handshake HMAC failed from ${raw.senderAddress} — dropping")
                    _service?.disconnect(raw.senderAddress); return
                }
                val updatedState = state.copy(
                    theirChallenge = packet.challenge,
                    theirName = packet.senderName
                )
                handshakeStates[raw.senderAddress] = updatedState
                val response = buildPacket(
                    type = PacketType.HANDSHAKE_RESPONSE,
                    address = raw.senderAddress,
                    content = packet.senderName,
                    challenge = packet.challenge,
                    signature = MessageCrypto.hmacSign(packet.challenge)
                )
                _service?.sendPacket(raw.senderAddress, response)
            }

            // Step 3 (Initiator → Acceptor): "I signed your nonce"
            PacketType.HANDSHAKE_RESPONSE -> {
                val state = handshakeStates[raw.senderAddress] ?: return
                if (!MessageCrypto.hmacVerify(state.ourChallenge, packet.signature)) {
                    Log.w(TAG, "Handshake response HMAC invalid from ${raw.senderAddress}")
                    _service?.disconnect(raw.senderAddress); return
                }
                // Initiator is verified. Now ask the acceptor's USER for approval.
                val name = state.theirName.ifBlank { packet.senderName }
                pendingApprovals[raw.senderAddress] = name
                // Send ACK to initiator while we wait for user approval
                // (initiator will show "waiting for approval" state)
                val ack = buildPacket(
                    type = PacketType.CONNECT_REQUEST,
                    address = raw.senderAddress,
                    content = localUserName
                )
                _service?.sendPacket(raw.senderAddress, ack)
                // Emit to UI — NearbyFragment will show the dialog
                _pendingRequests.emit(PendingRequest(raw.senderAddress, name))
            }

            // Step 4a (Acceptor → Initiator): User tapped Accept
            PacketType.CONNECT_ACCEPTED -> {
                val name = packet.senderName
                establishSession(raw.senderAddress, name)
                _connectionEvents.emit(ConnectionEvent.Connected(raw.senderAddress))
            }

            // Step 4b (Acceptor → Initiator): User tapped Deny
            PacketType.CONNECT_DENIED -> {
                handshakeStates.remove(raw.senderAddress)
                _connectionDenied.emit(raw.senderAddress)
                _service?.disconnect(raw.senderAddress)
            }

            // Initiator is notified to wait while acceptor shows dialog
            PacketType.CONNECT_REQUEST -> {
                // Add to nearbyUsers if not already present so the initiator sees
                // the device in the list with CONNECTING state while awaiting approval
                val name = packet.senderName.ifBlank { raw.senderAddress }
                _nearbyUsers.update { map ->
                    val existing = map[raw.senderAddress]
                    if (existing != null) {
                        map.toMutableMap().also {
                            it[raw.senderAddress] = existing.copy(
                                userName = name,
                                connectionState = ConnectionState.CONNECTING
                            )
                        }
                    } else {
                        map.toMutableMap().also {
                            it[raw.senderAddress] = NearbyUser(
                                deviceAddress = raw.senderAddress,
                                deviceName = raw.senderAddress,
                                userName = name,
                                connectionState = ConnectionState.CONNECTING,
                                isVerified = false
                            )
                        }
                    }
                }
            }

            PacketType.TEXT_MESSAGE -> {
                val key = sessionKeys[raw.senderAddress]
                val plaintext = if (key != null) {
                    MessageCrypto.decrypt(packet.content, key) ?: run {
                        Log.w(TAG, "Decrypt failed from ${raw.senderAddress}"); return
                    }
                } else {
                    packet.content // fallback (should not happen in normal flow)
                }
                _incomingMessages.emit(packet.copy(content = plaintext))
            }

            PacketType.TYPING_START, PacketType.TYPING_STOP, PacketType.READ_RECEIPT -> {
                _incomingMessages.emit(packet)
            }

            PacketType.DISCONNECT -> {
                _service?.disconnect(raw.senderAddress)
            }

            else -> {
                _incomingMessages.emit(packet)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Connection approval (called by NearbyFragment on user tap)
    // -------------------------------------------------------------------------

    fun approveConnection(address: String) {
        val name = pendingApprovals.remove(address) ?: return
        scope.launch {
            val ack = buildPacket(PacketType.CONNECT_ACCEPTED, address, localUserName)
            _service?.sendPacket(address, ack)
            establishSession(address, name)
        }
    }

    fun denyConnection(address: String) {
        val name = pendingApprovals.remove(address) ?: ""
        scope.launch {
            val deny = buildPacket(PacketType.CONNECT_DENIED, address, "")
            _service?.sendPacket(address, deny)
            delay(300) // give the packet time to flush
            _service?.disconnect(address)
            handshakeStates.remove(address)
            Log.d(TAG, "Denied connection from $address ($name)")
        }
    }

    // -------------------------------------------------------------------------
    // Initiate a handshake toward a known device
    // -------------------------------------------------------------------------

    fun initiateHandshake(address: String) {
        scope.launch {
            val nonce = UUID.randomUUID().toString()
            handshakeStates[address] = HandshakeState(weAreInitiator = true, ourChallenge = nonce)
            val hs = buildPacket(PacketType.HANDSHAKE, address, localUserName, challenge = nonce)
            _service?.sendPacket(address, hs)

            // If the handshake is not completed within the timeout, clean up the orphaned state
            delay(Constants.HANDSHAKE_TIMEOUT_MS)
            if (handshakeStates.containsKey(address)) {
                Log.w(TAG, "Handshake timed out for $address — disconnecting")
                handshakeStates.remove(address)
                _service?.disconnect(address)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Send an encrypted text message
    // -------------------------------------------------------------------------

    fun sendMessage(address: String, packet: MessagePacket) {
        scope.launch {
            val key = sessionKeys[address]
            val finalPacket = if (key != null && packet.type == PacketType.TEXT_MESSAGE) {
                packet.copy(content = MessageCrypto.encrypt(packet.content, key))
            } else packet
            _service?.sendPacket(address, finalPacket)
        }
    }

    fun sendPacket(address: String, packet: MessagePacket) {
        _service?.sendPacket(address, packet)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun establishSession(address: String, userName: String) {
        val key = MessageCrypto.deriveKey(localAddress, address)
        sessionKeys[address] = key
        handshakeStates.remove(address)
        _nearbyUsers.update { map ->
            val existing = map[address]
            map.toMutableMap().also {
                it[address] = (existing ?: NearbyUser(address, address, userName))
                    .copy(userName = userName, connectionState = ConnectionState.CONNECTED, isVerified = true)
            }
        }
        Log.d(TAG, "Session established with $address ($userName)")
    }

    private fun updateState(address: String, state: ConnectionState) {
        _nearbyUsers.update { map ->
            val u = map[address] ?: return@update map
            map.toMutableMap().also { it[address] = u.copy(connectionState = state) }
        }
    }

    private fun buildPacket(
        type: PacketType,
        address: String,
        content: String,
        challenge: String = "",
        signature: String = ""
    ) = MessagePacket(
        id = UUID.randomUUID().toString(),
        type = type,
        senderAddress = localAddress,
        senderName = localUserName,
        content = content,
        challenge = challenge,
        signature = signature
    )

    fun restartDiscovery() { _discovery?.startDiscovery() }

    fun stopAll() {
        _discovery?.stopDiscovery()
        _service?.stopAll()
        scope.cancel()
        started = false
    }

    companion object {
        private const val TAG = "BlueMesManager"

        @Volatile private var INSTANCE: BlueMesManager? = null
        fun getInstance(ctx: Context): BlueMesManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: BlueMesManager(ctx.applicationContext).also { INSTANCE = it }
            }
    }
}
