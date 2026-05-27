package com.bluemes.app.models

import com.google.gson.Gson

/**
 * Wire-format message packet exchanged between BlueMes devices.
 */
data class MessagePacket(
    val id: String,
    val type: PacketType,
    val senderAddress: String,
    val senderName: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val protocolVersion: Int = PROTOCOL_VERSION
) {
    fun serialize(): String = Gson().toJson(this)

    companion object {
        const val PROTOCOL_VERSION = 1
        private val gson = Gson()

        fun deserialize(json: String): MessagePacket? = try {
            val packet = gson.fromJson(json, MessagePacket::class.java)
            if (packet.protocolVersion == PROTOCOL_VERSION) packet else null
        } catch (e: Exception) {
            null
        }
    }
}

enum class PacketType {
    HANDSHAKE,
    HANDSHAKE_ACK,
    TEXT_MESSAGE,
    TYPING_START,
    TYPING_STOP,
    READ_RECEIPT,
    DISCONNECT
}
