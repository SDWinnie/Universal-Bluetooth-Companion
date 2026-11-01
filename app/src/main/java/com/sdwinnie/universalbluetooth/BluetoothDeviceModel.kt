// 🤖 Generated wholely or partially with Google Antigravity
package com.sdwinnie.universalbluetooth

import android.bluetooth.BluetoothDevice

/**
 * Represents any discovered BLE, Classic, Dual-Mode, or Paired Bluetooth peripheral.
 */
data class BluetoothDeviceModel(
    val address: String,
    var name: String,
    var rssi: Int,
    var txPower: Int? = null,
    var deviceType: Int = BluetoothDevice.DEVICE_TYPE_UNKNOWN,
    var isBonded: Boolean = false,
    var isConnected: Boolean = false,
    var isConnectable: Boolean = true,
    var lastSeenMs: Long = System.currentTimeMillis()
) {
    fun getEstimatedDistanceMeters(): Double =
        BluetoothSignalAnalyzer.estimateDistanceMeters(rssi, txPower)

    fun isWithinDistance(maxMeters: Double): Boolean {
        if (isConnected) return true
        if (maxMeters <= 0.0) return true
        val dist = getEstimatedDistanceMeters()
        if (dist < 0.0) return true
        return dist <= maxMeters
    }

    fun getTransportLabel(): String {
        val base = when (deviceType) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
            BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual (BLE+Classic)"
            BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
            else -> "Bluetooth"
        }
        return if (isBonded) "$base • จับคู่แล้ว" else base
    }
}
