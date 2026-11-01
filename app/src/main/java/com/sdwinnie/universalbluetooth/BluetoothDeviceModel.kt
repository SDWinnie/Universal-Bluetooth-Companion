// 🤖 Generated wholely or partially with Google Antigravity
package com.sdwinnie.universalbluetooth

import android.bluetooth.BluetoothDevice
import kotlin.math.pow

/**
 * Represents a discovered Bluetooth / BLE peripheral with signal metrics.
 */
data class BluetoothDeviceModel(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    var rssi: Int,
    var isConnectable: Boolean = true,
    var lastSeenMs: Long = System.currentTimeMillis()
) {
    /**
     * Estimates distance in meters using the standard Log-Distance Path Loss Model:
     * Distance = 10 ^ ((Measured Power - RSSI) / (10 * N))
     * where Measured Power ~ -59 dBm at 1m, and N (path loss exponent) ~ 2.2
     */
    fun getEstimatedDistanceMeters(): Double {
        if (rssi == 0) return -1.0
        val measuredPower = -59.0
        val n = 2.2
        val ratio = (measuredPower - rssi) / (10.0 * n)
        val dist = 10.0.pow(ratio)
        return (Math.round(dist * 10.0) / 10.0).coerceAtLeast(0.1)
    }

    /**
     * Returns true if device is within [maxMeters] (5m or 10m).
     */
    fun isWithinDistance(maxMeters: Double): Boolean {
        if (maxMeters <= 0.0) return true
        val dist = getEstimatedDistanceMeters()
        return dist in 0.0..maxMeters
    }
}
