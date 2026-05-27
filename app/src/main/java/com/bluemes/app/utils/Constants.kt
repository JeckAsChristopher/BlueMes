package com.bluemes.app.utils

import java.util.UUID

object Constants {
    // BlueMes service UUID — unique identifier for this app's Bluetooth service
    val BLUEMES_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
    const val BLUEMES_SERVICE_NAME = "BlueMes"

    // Bluetooth discovery timeouts
    const val DISCOVERY_DURATION_SECONDS = 300
    const val RECONNECT_DELAY_MS = 3000L
    const val CONNECTION_TIMEOUT_MS = 10000L

    // Message buffer
    const val SOCKET_BUFFER_SIZE = 4096
    const val MAX_MESSAGE_LENGTH = 2000

    // Protocol
    const val PACKET_DELIMITER = "\n"

    // Intent extras
    const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
    const val EXTRA_USER_NAME = "extra_user_name"
}
