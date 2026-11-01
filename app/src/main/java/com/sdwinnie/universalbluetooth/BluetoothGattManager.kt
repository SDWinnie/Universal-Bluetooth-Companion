// 🤖 Generated wholely or partially with Google Antigravity
package com.sdwinnie.universalbluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Direct GATT Client Manager that connects to any Bluetooth peripheral
 * even when the host Android OS Settings reports "Device not supported".
 */
@SuppressLint("MissingPermission")
class BluetoothGattManager(
    private val context: Context,
    private val listener: GattEventListener
) {

    interface GattEventListener {
        fun onConnectionStateChanged(state: Int, statusMessage: String)
        fun onServicesDiscovered(serviceCount: Int)
        fun onBatteryLevelRead(batteryPercent: Int)
        fun onDataReceived(characteristicUuid: UUID, rawBytes: ByteArray, buttonCode: Int)
        fun onError(errorCode: Int, message: String)
    }

    companion object {
        private const val TAG = "UniversalGattManager"
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        val HID_SERVICE_UUID: UUID = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")
        val HID_REPORT_UUID: UUID = UUID.fromString("00002a4d-0000-1000-8000-00805f9b34fb")
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var targetDevice: BluetoothDevice? = null
    private var isAutoReconnectEnabled = true
    private val mainHandler = Handler(Looper.getMainLooper())
    private val descriptorWriteQueue = ConcurrentLinkedQueue<BluetoothGattDescriptor>()
    private var isWritingDescriptor = false

    val isConnected: Boolean
        get() = bluetoothGatt != null

    val connectedDevice: BluetoothDevice?
        get() = targetDevice

    /**
     * Connects directly to any BLE peripheral using TRANSPORT_LE.
     * This bypasses the Android system's restrictive Bluetooth settings checks.
     */
    fun connect(device: BluetoothDevice, autoReconnect: Boolean = true) {
        disconnect()
        targetDevice = device
        isAutoReconnectEnabled = autoReconnect
        listener.onConnectionStateChanged(BluetoothProfile.STATE_CONNECTING, "กำลังเชื่อมต่อกับ ${device.name ?: device.address}...")

        mainHandler.postDelayed({
            try {
                bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    device.connectGatt(context, false, gattCallback)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}", e)
                listener.onError(-1, "เชื่อมต่อล้มเหลว: ${e.localizedMessage}")
            }
        }, 300)
    }

    fun disconnect() {
        isAutoReconnectEnabled = false
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing GATT", e)
        }
        bluetoothGatt = null
        targetDevice = null
        descriptorWriteQueue.clear()
        isWritingDescriptor = false
        listener.onConnectionStateChanged(BluetoothProfile.STATE_DISCONNECTED, "ตัดการเชื่อมต่อแล้ว")
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                val errMsg = "GATT ข้อผิดพลาดสถานะ $status"
                Log.w(TAG, errMsg)
                if (isAutoReconnectEnabled && targetDevice != null) {
                    mainHandler.postDelayed({
                        Log.d(TAG, "Attempting auto-reconnect...")
                        targetDevice?.let { connect(it, true) }
                    }, 2000)
                } else {
                    listener.onError(status, errMsg)
                    listener.onConnectionStateChanged(BluetoothProfile.STATE_DISCONNECTED, "การเชื่อมต่อขัดข้อง ($status)")
                }
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    listener.onConnectionStateChanged(BluetoothProfile.STATE_CONNECTED, "เชื่อมต่อสำเร็จ! กำลังสำรวจโพรไฟล์...")
                    // Request high connection priority for responsive button/pen clicks
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    mainHandler.postDelayed({
                        gatt.discoverServices()
                    }, 600)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    listener.onConnectionStateChanged(BluetoothProfile.STATE_DISCONNECTED, "อุปกรณ์ตัดการเชื่อมต่อ")
                    gatt.close()
                    bluetoothGatt = null
                    if (isAutoReconnectEnabled && targetDevice != null) {
                        mainHandler.postDelayed({
                            targetDevice?.let { connect(it, true) }
                        }, 2500)
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onError(status, "ค้นหาบริการ (Services) ล้มเหลว")
                return
            }

            val services = gatt.services
            Log.d(TAG, "Discovered ${services.size} services")
            listener.onServicesDiscovered(services.size)

            // 1. Read battery level if available
            val batteryChar = gatt.getService(BATTERY_SERVICE_UUID)?.getCharacteristic(BATTERY_LEVEL_UUID)
            if (batteryChar != null && (batteryChar.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0)) {
                gatt.readCharacteristic(batteryChar)
            }

            // 2. Scan and subscribe to ALL notify/indicate characteristics
            // This covers HID Reports, Stylus Buttons, Selfie Remotes, and Custom IoT protocols
            for (service in services) {
                for (characteristic in service.characteristics) {
                    val props = characteristic.properties
                    val canNotify = (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                    val canIndicate = (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0

                    if (canNotify || canIndicate) {
                        enableNotification(gatt, characteristic, canIndicate)
                    }
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_UUID) {
                val value = characteristic.value?.firstOrNull()?.toInt() ?: -1
                if (value in 0..100) {
                    listener.onBatteryLevelRead(value)
                }
            }
        }

        @Deprecated("Deprecated in Java for API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value ?: return
            handleIncomingBytes(characteristic.uuid, data)
        }

        // For Android 13+ (API 33)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingBytes(characteristic.uuid, value)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            isWritingDescriptor = false
            processNextDescriptor(gatt)
        }
    }

    private fun enableNotification(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        isIndicate: Boolean
    ) {
        gatt.setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(CCCD_UUID)
        if (cccd != null) {
            descriptorWriteQueue.add(cccd)
            if (!isWritingDescriptor) {
                processNextDescriptor(gatt)
            }
        }
    }

    private fun processNextDescriptor(gatt: BluetoothGatt) {
        val next = descriptorWriteQueue.poll() ?: return
        isWritingDescriptor = true
        val descriptorValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(next, descriptorValue)
        } else {
            @Suppress("DEPRECATION")
            next.value = descriptorValue
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(next)
        }
    }

    private fun handleIncomingBytes(uuid: UUID, data: ByteArray) {
        if (data.isEmpty()) return
        val buttonCode = parseButtonCode(data)
        listener.onDataReceived(uuid, data, buttonCode)
    }

    /**
     * Parses standard HID / Stylus / Remote button reports:
     * Non-zero bytes indicate active button presses.
     */
    private fun parseButtonCode(bytes: ByteArray): Int {
        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 0xFF
            if (b != 0) {
                return (i * 256) + b
            }
        }
        return 0
    }
}
