package com.bluemes.app.models

/**
 * Represents a nearby BlueMes user discovered via Bluetooth.
 */
data class NearbyUser(
    val deviceAddress: String,
    val deviceName: String,
    val userName: String,
    val rssi: Int = Int.MIN_VALUE,
    val connectionState: ConnectionState = ConnectionState.DISCOVERED,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
) {
    fun signalStrengthLabel(): String = when {
        rssi >= -60 -> "Strong"
        rssi >= -75 -> "Good"
        rssi >= -90 -> "Weak"
        else -> "Unknown"
    }
}

enum class ConnectionState {
    DISCOVERED,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    FAILED
}
