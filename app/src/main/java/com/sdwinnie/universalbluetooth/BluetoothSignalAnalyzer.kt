// 🤖 Generated wholely or partially with Google Antigravity
package com.sdwinnie.universalbluetooth

import android.bluetooth.BluetoothClass
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure, unit-testable signal distance estimator, device identity resolver,
 * and rising-edge button packet detector.
 */
object BluetoothSignalAnalyzer {

    /**
     * Smooths noisy RSSI readings using an Exponential Moving Average (EMA).
     */
    fun smoothRssi(previousRssi: Int?, newRssi: Int): Int {
        if (newRssi >= 0 || newRssi < -127) return previousRssi ?: -85
        if (previousRssi == null || previousRssi >= 0) return newRssi
        return (previousRssi * 0.65 + newRssi * 0.35).roundToInt()
    }

    /**
     * Estimates physical distance in meters using a calibrated indoor
     * Log-Distance Path Loss Model tailored for mobile/stylus/BLE antennas.
     *
     * Typical compact BLE/Classic accessories measure ~ -65 dBm at 1 meter
     * indoors with path-loss exponent N = 2.4:
     *   -65 dBm -> ~1.0 m
     *   -77 dBm -> ~3.2 m (within 5m)
     *   -82 dBm -> ~5.1 m (~5m boundary)
     *   -89 dBm -> ~10.0 m (10m boundary)
     */
    fun estimateDistanceMeters(rssi: Int, txPower: Int? = null): Double {
        if (rssi >= 0 || rssi < -127) return -1.0
        val referenceAt1m = if (txPower != null && txPower in -90..-20) {
            // Convert advertised 0m TxPower to 1m reference (~ -41 dB free-space loss), clamped safely
            (txPower - 41.0).coerceIn(-72.0, -55.0)
        } else {
            -65.0
        }
        val pathLossExponent = 2.4
        val ratio = (referenceAt1m - rssi) / (10.0 * pathLossExponent)
        val rawMeters = 10.0.pow(ratio)
        return ((rawMeters * 10.0).roundToInt() / 10.0).coerceIn(0.1, 99.9)
    }

    /**
     * Resolves a human-friendly device name even when a BLE peripheral does not
     * broadcast a Complete Local Name in its primary advertising packet.
     */
    fun resolveDisplayName(
        rawName: String?,
        manufacturerIds: List<Int> = emptyList(),
        serviceUuids: List<UUID> = emptyList(),
        majorDeviceClass: Int? = null
    ): String {
        val cleaned = rawName?.trim().orEmpty()
        if (cleaned.isNotEmpty() && !cleaned.equals("Unknown", ignoreCase = true) && !cleaned.equals("null", ignoreCase = true)) {
            return cleaned
        }

        val serviceHint = resolveServiceHint(serviceUuids)
        val vendorName = manufacturerIds.firstNotNullOfOrNull { COMPANY_NAMES[it] }

        if (vendorName != null && serviceHint != null) {
            return "$vendorName ($serviceHint)"
        }
        if (serviceHint != null) {
            return serviceHint
        }
        if (vendorName != null) {
            return "$vendorName Bluetooth"
        }

        return when (majorDeviceClass) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> "อุปกรณ์เสียง / หูฟังบลูทูธ"
            BluetoothClass.Device.Major.PERIPHERAL -> "รีโมต / คีย์บอร์ด / ปากกา HID"
            BluetoothClass.Device.Major.PHONE -> "สมาร์ทโฟนบลูทูธ"
            BluetoothClass.Device.Major.COMPUTER -> "คอมพิวเตอร์ / แท็บเล็ต"
            BluetoothClass.Device.Major.WEARABLE -> "สมาร์ทวอทช์ / อุปกรณ์สวมใส่"
            BluetoothClass.Device.Major.HEALTH -> "เซ็นเซอร์สุขภาพ"
            BluetoothClass.Device.Major.IMAGING -> "อุปกรณ์กล้อง / พริ้นเตอร์"
            else -> "อุปกรณ์บลูทูธไร้สาย (BLE)"
        }
    }

    private fun resolveServiceHint(uuids: List<UUID>): String? {
        for (uuid in uuids) {
            val short16 = ((uuid.mostSignificantBits ushr 32) and 0xFFFFL).toInt()
            when (short16) {
                0x1812 -> return "ปากกา / รีโมต HID"
                0x1124 -> return "อุปกรณ์ควบคุม Classic HID"
                0x110B, 0x110A, 0x111E -> return "เครื่องเสียง / หูฟัง"
                0x180D -> return "เซ็นเซอร์วัดชีพจร"
                0x180F -> return "อุปกรณ์ BLE (มีแบตเตอรี่)"
                0xFFE0, 0xFFE1, 0xFFF0 -> return "โมดูลปุ่มกด / BLE Serial"
                0xFE9F, 0xFE2C -> return "อุปกรณ์ Fast Pair"
                0xFD6F, 0xFEAA, 0xFD5A -> return "Smart Tag / Beacon"
            }
        }
        return null
    }

    private val COMPANY_NAMES = mapOf(
        0x004C to "Apple",
        0x0075 to "Samsung",
        0x038F to "Xiaomi",
        0x0006 to "Microsoft",
        0x00E0 to "Google",
        0x027D to "Huawei",
        0x0059 to "Nordic BLE",
        0x02E5 to "Espressif (ESP32)",
        0x0046 to "MediaTek",
        0x001D to "Qualcomm",
        0x000F to "Broadcom",
        0x0002 to "Intel",
        0x012D to "Sony",
        0x0057 to "Harman / JBL",
        0x009E to "Bose",
        0x0157 to "Amazfit / Zepp",
        0x0087 to "Garmin",
        0x0310 to "OPPO",
        0x03A7 to "Vivo",
        0x058E to "Meta",
        0x0171 to "Amazon",
        0x004F to "Logitech"
    )
}

/**
 * Stateful rising-edge button detector per characteristic/stream.
 * Prevents double-firing when a button is released (all-zero payload)
 * and filters duplicate steady-state status packets.
 */
class ButtonPacketDetector {
    private val lastPayloads = ConcurrentHashMap<String, ByteArray>()

    data class DetectionResult(
        val isButtonPress: Boolean,
        val buttonCode: Int,
        val isRelease: Boolean
    )

    fun evaluate(sourceKey: String, bytes: ByteArray): DetectionResult {
        if (bytes.isEmpty()) {
            return DetectionResult(isButtonPress = false, buttonCode = 0, isRelease = false)
        }

        val previous = lastPayloads.put(sourceKey, bytes.copyOf())
        val allZero = bytes.all { it.toInt() == 0 }

        if (allZero) {
            val wasPressed = previous != null && previous.any { it.toInt() != 0 }
            return DetectionResult(isButtonPress = false, buttonCode = 0, isRelease = wasPressed)
        }

        // Ignore identical repeated heartbeat bytes on status characteristics unless it's a single-byte trigger
        if (previous != null && previous.contentEquals(bytes) && bytes.size > 2) {
            return DetectionResult(isButtonPress = false, buttonCode = 0, isRelease = false)
        }

        for (i in bytes.indices) {
            val unsigned = bytes[i].toInt() and 0xFF
            if (unsigned != 0) {
                val prevByte = if (previous != null && i < previous.size) (previous[i].toInt() and 0xFF) else 0
                if (unsigned != prevByte || previous == null || previous.all { it.toInt() == 0 } || bytes.size <= 2) {
                    val code = (i * 256) + unsigned
                    return DetectionResult(isButtonPress = true, buttonCode = code, isRelease = false)
                }
            }
        }

        val fallback = (bytes[0].toInt() and 0xFF).coerceAtLeast(1)
        return DetectionResult(isButtonPress = true, buttonCode = fallback, isRelease = false)
    }

    fun reset() {
        lastPayloads.clear()
    }
}
