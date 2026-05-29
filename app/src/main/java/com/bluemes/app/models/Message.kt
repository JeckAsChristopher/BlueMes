package com.bluemes.app.models

import com.bluemes.app.utils.Constants
import com.google.gson.Gson

/**
 * Wire-format packet exchanged between BlueMes peers.
 *
 * Security fields:
 *  - [appToken]   must equal Constants.APP_TOKEN or the packet is discarded.
 *                 This is the first filter that blocks non-BlueMes devices.
 *  - [challenge]  random nonce sent in HANDSHAKE; receiver signs it with HMAC.
 *  - [signature]  HMAC-SHA256(APP_SECRET, challenge) — proves both devices
 *                 share the same secret without transmitting it.
 *  - [content]    for TEXT_MESSAGE: AES-128/CBC encrypted, format "ivB64:ctB64".
 *                 For other packet types: plaintext (handshake metadata, etc.).
 */
data class MessagePacket(
    val id: String,
    val type: PacketType,
    val senderAddress: String,
    val senderName: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val protocolVersion: Int = PROTOCOL_VERSION,
    // Security
    val appToken: String = Constants.APP_TOKEN,
    val challenge: String = "",       // HANDSHAKE: random nonce; HANDSHAKE_RESPONSE: same nonce
    val signature: String = ""        // HANDSHAKE_RESPONSE: HMAC(challenge)
) {
    fun serialize(): String = Gson().toJson(this)

    companion object {
        const val PROTOCOL_VERSION = 2
        private val gson = Gson()

        fun deserialize(json: String): MessagePacket? = try {
            val p = gson.fromJson(json, MessagePacket::class.java) ?: return null
            // Hard reject anything that doesn't carry the correct app token
            if (p.appToken != Constants.APP_TOKEN) return null
            if (p.protocolVersion != PROTOCOL_VERSION) return null
            p
        } catch (_: Exception) {
            null
        }
    }
}

enum class PacketType {
    // Handshake sequence (4-way):
    //   Initiator  → HANDSHAKE           (carries challenge nonce)
    //   Acceptor   → HANDSHAKE_CHALLENGE (echoes nonce, adds its own)
    //   Initiator  → HANDSHAKE_RESPONSE  (signs acceptor's nonce)
    //   Acceptor   → HANDSHAKE_ACK       (verified — connection is live)
    HANDSHAKE,
    HANDSHAKE_CHALLENGE,
    HANDSHAKE_RESPONSE,
    HANDSHAKE_ACK,

    // Connection approval
    CONNECT_REQUEST,   // acceptor asks user to approve; sender is notified to wait
    CONNECT_ACCEPTED,  // receiver approved — chat can begin
    CONNECT_DENIED,    // receiver declined — sender is notified

    // Messaging
    TEXT_MESSAGE,
    TYPING_START,
    TYPING_STOP,
    READ_RECEIPT,

    // Control
    DISCONNECT
}
