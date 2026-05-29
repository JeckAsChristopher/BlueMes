package com.bluemes.app.models

/**
 * A nearby device discovered via Bluetooth.
 *
 * [isVerified] is false until a valid BlueMes HANDSHAKE (with correct APP_TOKEN
 * and HMAC signature) has been received. Unverified devices are NEVER shown in
 * the Nearby screen — this is what prevents non-BlueMes devices from appearing.
 */
data class NearbyUser(
    val deviceAddress: String,
    val deviceName: String,
    val userName: String,
    val rssi: Int = Int.MIN_VALUE,
    val connectionState: ConnectionState = ConnectionState.DISCOVERED,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val isVerified: Boolean = false           // true only after valid BlueMes handshake
) {
    fun signalStrengthLabel(): String = when {
        rssi >= -60 -> "Strong"
        rssi >= -75 -> "Good"
        rssi >= -90 -> "Weak"
        else        -> "Unknown"
    }
}

enum class ConnectionState {
    DISCOVERED,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    FAILED
}
