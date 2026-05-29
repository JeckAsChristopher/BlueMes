package com.bluemes.app.utils

import java.util.UUID

object Constants {
    // BlueMes RFCOMM service UUID — only devices running BlueMes register this
    val BLUEMES_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
    const val BLUEMES_SERVICE_NAME = "BlueMes"

    // --- Security ---
    // App-level token: every valid BlueMes handshake must contain this string.
    // Devices not running BlueMes will never produce a packet with this token,
    // so non-app devices are silently filtered out at the handshake layer.
    const val APP_TOKEN = "BLUEMES_PROTO_V2_2024"

    // Shared secret used as the HMAC key for challenge-response and as the
    // AES key derivation input. Both devices must have the same value.
    const val APP_SECRET = "BM$3cur3K3y#2024!OfflineChat"

    // --- Timing ---
    const val DISCOVERY_AUTO_CONNECT_DELAY_MS = 800L   // brief pause before auto-connecting found device
    const val RECONNECT_DELAY_MS = 4000L
    const val CONNECTION_TIMEOUT_MS = 12000L
    const val HANDSHAKE_TIMEOUT_MS = 6000L             // if no handshake within this time, drop socket

    // --- I/O ---
    const val SOCKET_BUFFER_SIZE = 4096
    const val MAX_MESSAGE_LENGTH = 2000
    const val PACKET_DELIMITER = "\n"

    // --- Intent extras ---
    const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
    const val EXTRA_USER_NAME = "extra_user_name"
}
