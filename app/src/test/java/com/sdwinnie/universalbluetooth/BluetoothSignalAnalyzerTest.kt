// 🤖 Generated wholely or partially with Google Antigravity
package com.sdwinnie.universalbluetooth

import android.bluetooth.BluetoothClass
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BluetoothSignalAnalyzerTest {

    @Test
    fun `estimateDistanceMeters accurately maps indoor 1m, 5m, and 10m boundaries`() {
        val d1m = BluetoothSignalAnalyzer.estimateDistanceMeters(-65)
        assertEquals(1.0, d1m, 0.15)

        // Typical compact BLE stylus/shutter at ~3m indoors (-76 dBm) must be inside 5m filter
        val d3m = BluetoothSignalAnalyzer.estimateDistanceMeters(-76)
        assertTrue("Expected ~2.9m to be <= 5.0m, got $d3m", d3m in 2.0..4.5)

        // ~5m boundary (-81 dBm)
        val d5m = BluetoothSignalAnalyzer.estimateDistanceMeters(-81)
        assertTrue("Expected -81 dBm to be <= 5.0m, got $d5m", d5m in 4.0..5.0)

        // ~10m boundary (-89 dBm)
        val d10m = BluetoothSignalAnalyzer.estimateDistanceMeters(-89)
        assertEquals(10.0, d10m, 0.2)

        // Out of 10m range (-95 dBm)
        val dFar = BluetoothSignalAnalyzer.estimateDistanceMeters(-95)
        assertTrue("Expected -95 dBm to exceed 10m, got $dFar", dFar > 15.0)

        // Invalid RSSI
        assertEquals(-1.0, BluetoothSignalAnalyzer.estimateDistanceMeters(0), 0.01)
    }

    @Test
    fun `smoothRssi dampens sudden multipath spikes cleanly`() {
        val initial = BluetoothSignalAnalyzer.smoothRssi(null, -70)
        assertEquals(-70, initial)

        val smoothed = BluetoothSignalAnalyzer.smoothRssi(-70, -80)
        assertTrue("Smoothed RSSI should sit between -70 and -80, got $smoothed", smoothed in -75..-73)
    }

    @Test
    fun `resolveDisplayName identifies nameless BLE styluses, tags, and vendors`() {
        val hidUuid = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")
        val xiaomiStylus = BluetoothSignalAnalyzer.resolveDisplayName(
            rawName = null,
            manufacturerIds = listOf(0x038F),
            serviceUuids = listOf(hidUuid)
        )
        assertEquals("Xiaomi (ปากกา / รีโมต HID)", xiaomiStylus)

        val realNamePreserved = BluetoothSignalAnalyzer.resolveDisplayName(
            rawName = "Xiaomi Smart Pen 2",
            manufacturerIds = listOf(0x038F),
            serviceUuids = listOf(hidUuid)
        )
        assertEquals("Xiaomi Smart Pen 2", realNamePreserved)

        val classicAudio = BluetoothSignalAnalyzer.resolveDisplayName(
            rawName = "Unknown",
            majorDeviceClass = BluetoothClass.Device.Major.AUDIO_VIDEO
        )
        assertEquals("อุปกรณ์เสียง / หูฟังบลูทูธ", classicAudio)
    }

    @Test
    fun `ButtonPacketDetector triggers on rising edge and suppresses 00 00 release double-trigger`() {
        val detector = ButtonPacketDetector()
        val charKey = "0000ffe1-0000-1000-8000-00805f9b34fb"

        // 1. Press button -> must trigger
        val press1 = detector.evaluate(charKey, byteArrayOf(0x01, 0x00))
        assertTrue(press1.isButtonPress)
        assertFalse(press1.isRelease)
        assertEquals(1, press1.buttonCode)

        // 2. Release button (00 00) -> must NOT trigger action, must mark isRelease = true
        val release1 = detector.evaluate(charKey, byteArrayOf(0x00, 0x00))
        assertFalse(release1.isButtonPress)
        assertTrue(release1.isRelease)

        // 3. Press second button -> must trigger again cleanly
        val press2 = detector.evaluate(charKey, byteArrayOf(0x00, 0x02))
        assertTrue(press2.isButtonPress)
        assertFalse(press2.isRelease)
        assertEquals(258, press2.buttonCode)

        // 4. Multi-byte steady heartbeat should not repeat-fire unless a byte changes
        val statusStream = "vendor-status-stream"
        val firstStatus = detector.evaluate(statusStream, byteArrayOf(0x10, 0x20, 0x30, 0x00))
        assertTrue(firstStatus.isButtonPress)

        val duplicateHeartbeat = detector.evaluate(statusStream, byteArrayOf(0x10, 0x20, 0x30, 0x00))
        assertFalse("Identical 4-byte status packet should not re-trigger", duplicateHeartbeat.isButtonPress)

        val buttonBitChanged = detector.evaluate(statusStream, byteArrayOf(0x10, 0x20, 0x30, 0x01))
        assertTrue("Changed byte in status packet must trigger", buttonBitChanged.isButtonPress)
    }

    @Test
    fun `BluetoothDeviceModel keeps connected devices visible regardless of range filter`() {
        val farConnected = BluetoothDeviceModel(
            address = "AA:BB:CC:11:22:33",
            name = "Connected Pen",
            rssi = -96,
            isConnected = true
        )
        assertTrue(farConnected.isWithinDistance(5.0))
        assertTrue(farConnected.isWithinDistance(10.0))

        val farDisconnected = farConnected.copy(isConnected = false)
        assertFalse(farDisconnected.isWithinDistance(5.0))
        assertFalse(farDisconnected.isWithinDistance(10.0))
        assertTrue(farDisconnected.isWithinDistance(0.0))
    }
}
