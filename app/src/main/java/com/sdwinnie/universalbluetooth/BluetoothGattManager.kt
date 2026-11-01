// 🤖 Generated wholely or partially with Google Antigravity
package com.sdwinnie.universalbluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.InputStream
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Universal Multi-Protocol Bluetooth Connection Engine:
 * 1. Direct BLE GATT (with sequential op queue, watchdog, MTU 247, and automatic HOGP/HID bonding).
 * 2. Dual-Mode transport fallback (TRANSPORT_LE -> TRANSPORT_AUTO -> Classic).
 * 3. Classic Bluetooth Engine (Secure Simple Pairing + HID/Audio Profile proxy + RFCOMM/SPP stream).
 */
@SuppressLint("MissingPermission")
class BluetoothGattManager(
    private val context: Context,
    private val listener: GattEventListener
) {

    interface GattEventListener {
        fun onConnectionStateChanged(state: Int, protocolLabel: String, statusMessage: String)
        fun onServicesDiscovered(serviceCount: Int, notifyCharCount: Int)
        fun onBatteryLevelRead(batteryPercent: Int)
        fun onRemoteRssiUpdated(rssi: Int)
        fun onButtonTriggered(buttonCode: Int, rawBytes: ByteArray, sourceLabel: String)
        fun onRawPacketLogged(sourceLabel: String, rawBytes: ByteArray, isRelease: Boolean)
        fun onError(errorCode: Int, message: String)
    }

    companion object {
        private const val TAG = "UniversalBtEngine"
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        val HID_SERVICE_UUID: UUID = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
        private const val PROFILE_HID_HOST = 4
        private const val OP_TIMEOUT_MS = 2500L
    }

    private sealed class GattOp {
        data class RequestMtu(val mtu: Int) : GattOp()
        data class ReadChar(val characteristic: BluetoothGattCharacteristic) : GattOp()
        data class WriteDesc(val descriptor: BluetoothGattDescriptor, val value: ByteArray) : GattOp()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var bluetoothGatt: BluetoothGatt? = null
    private var classicSocket: BluetoothSocket? = null
    private var classicJob: Job? = null
    private var targetDevice: BluetoothDevice? = null
    private var isAutoReconnectEnabled = true
    private var isConnectedInternal = false
    private var activeProtocol = "BLE GATT"
    private var leRetryCount = 0
    private var bondRequestedForCurrentSession = false

    private val opQueue = ConcurrentLinkedQueue<GattOp>()
    private var isOpInFlight = false
    private val packetDetector = ButtonPacketDetector()

    private val opWatchdog = Runnable {
        if (isOpInFlight) {
            Log.w(TAG, "GATT operation timed out; advancing queue")
            isOpInFlight = false
            bluetoothGatt?.let { processNextGattOp(it) }
        }
    }

    private val rssiPollRunnable = object : Runnable {
        override fun run() {
            val gatt = bluetoothGatt
            if (isConnectedInternal && gatt != null && !isOpInFlight && opQueue.isEmpty()) {
                try {
                    gatt.readRemoteRssi()
                } catch (_: Exception) {
                }
            }
            if (isConnectedInternal && bluetoothGatt != null) {
                mainHandler.postDelayed(this, 5000L)
            }
        }
    }

    private var bondReceiverRegistered = false
    private val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            if (device == null || device.address != targetDevice?.address) return

            when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                BluetoothDevice.BOND_BONDING -> {
                    listener.onConnectionStateChanged(
                        BluetoothProfile.STATE_CONNECTING,
                        activeProtocol,
                        "กำลังจับคู่เข้ารหัสความปลอดภัย (Secure Pairing)..."
                    )
                }
                BluetoothDevice.BOND_BONDED -> {
                    Log.d(TAG, "Device bonded successfully: ${device.address}")
                    val gatt = bluetoothGatt
                    if (gatt != null) {
                        // Re-subscribe protected HID/Vendor characteristics after encryption is established
                        mainHandler.postDelayed({ gatt.discoverServices() }, 400L)
                    } else if (device.type == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
                        connectClassicTransport(device)
                    }
                }
            }
        }
    }

    val isConnected: Boolean
        get() = isConnectedInternal

    val connectedDevice: BluetoothDevice?
        get() = targetDevice

    fun connect(device: BluetoothDevice, autoReconnect: Boolean = true) {
        if (!PermissionHelper.hasBluetoothConnectPermission(context)) {
            listener.onError(-2, "ยังไม่ได้รับสิทธิ์ BLUETOOTH_CONNECT")
            return
        }

        cleanupCurrentResources(notifyDisconnected = false)
        targetDevice = device
        isAutoReconnectEnabled = autoReconnect
        leRetryCount = 0
        bondRequestedForCurrentSession = false
        packetDetector.reset()
        registerBondReceiverIfNeeded()

        val displayName = device.name ?: device.address
        when (device.type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> {
                activeProtocol = "Classic Bluetooth"
                listener.onConnectionStateChanged(
                    BluetoothProfile.STATE_CONNECTING,
                    activeProtocol,
                    "กำลังเชื่อมต่อโหมด Classic กับ $displayName..."
                )
                connectClassicTransport(device)
            }
            else -> {
                activeProtocol = if (device.type == BluetoothDevice.DEVICE_TYPE_DUAL) "Dual GATT" else "BLE GATT"
                listener.onConnectionStateChanged(
                    BluetoothProfile.STATE_CONNECTING,
                    activeProtocol,
                    "กำลังเชื่อมต่อตรง (Direct GATT) กับ $displayName..."
                )
                startGattAttempt(device, useAutoTransport = false)
            }
        }
    }

    fun disconnect() {
        isAutoReconnectEnabled = false
        targetDevice = null
        unregisterBondReceiverQuietly()
        cleanupCurrentResources(notifyDisconnected = true)
    }

    fun shutdown() {
        disconnect()
        ioScope.cancel()
    }

    private fun cleanupCurrentResources(notifyDisconnected: Boolean) {
        mainHandler.removeCallbacksAndMessages(null)
        opQueue.clear()
        isOpInFlight = false
        isConnectedInternal = false

        classicJob?.cancel()
        classicJob = null
        try {
            classicSocket?.close()
        } catch (_: Exception) {
        }
        classicSocket = null

        val gattToClose = bluetoothGatt
        bluetoothGatt = null
        if (gattToClose != null) {
            try {
                gattToClose.disconnect()
            } catch (_: Exception) {
            }
            try {
                gattToClose.close()
            } catch (_: Exception) {
            }
        }

        if (notifyDisconnected) {
            listener.onConnectionStateChanged(
                BluetoothProfile.STATE_DISCONNECTED,
                activeProtocol,
                "ตัดการเชื่อมต่อเรียบร้อยแล้ว"
            )
        }
    }

    private fun startGattAttempt(device: BluetoothDevice, useAutoTransport: Boolean) {
        mainHandler.postDelayed({
            if (targetDevice?.address != device.address) return@postDelayed
            try {
                val transport = if (useAutoTransport) {
                    BluetoothDevice.TRANSPORT_AUTO
                } else {
                    BluetoothDevice.TRANSPORT_LE
                }
                bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(context, false, gattCallback, transport)
                } else {
                    device.connectGatt(context, false, gattCallback)
                }
            } catch (e: Exception) {
                Log.e(TAG, "connectGatt exception: ${e.message}", e)
                listener.onError(-1, "เชื่อมต่อ GATT ล้มเหลว: ${e.localizedMessage}")
            }
        }, 250L)
    }

    private fun connectClassicTransport(device: BluetoothDevice) {
        classicJob?.cancel()
        classicJob = ioScope.launch {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            try {
                adapter?.cancelDiscovery()
            } catch (_: Exception) {
            }

            // 1. Initiate official Secure Simple Pairing if not yet bonded
            if (device.bondState == BluetoothDevice.BOND_NONE && !bondRequestedForCurrentSession) {
                bondRequestedForCurrentSession = true
                try {
                    val started = device.createBond()
                    Log.d(TAG, "Classic createBond started=$started")
                    if (started) {
                        // Wait up to 12s for user/Just-Works bonding, then continue
                        var waited = 0
                        while (isActive && device.bondState == BluetoothDevice.BOND_BONDING && waited < 12000) {
                            delay(300)
                            waited += 300
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "createBond exception: ${e.message}")
                }
            }

            // 2. Request OS Profile Connection (HID Input Device / Audio) if supported
            val profileConnected = tryConnectSystemProfiles(adapter, device)

            // 3. Attempt RFCOMM / SPP data socket (for serial clickers, gamepads, microcontrollers, custom boards)
            try {
                device.fetchUuidsWithSdp()
            } catch (_: Exception) {
            }
            delay(350)

            val candidateUuids = linkedSetOf<UUID>()
            device.uuids?.mapNotNull { it?.uuid }?.forEach { candidateUuids.add(it) }
            candidateUuids.add(SPP_UUID)

            var openedSocket: BluetoothSocket? = null
            for (uuid in candidateUuids) {
                if (!isActive) return@launch
                openedSocket = tryOpenRfcommSocket(device, uuid, secure = true)
                    ?: tryOpenRfcommSocket(device, uuid, secure = false)
                if (openedSocket != null) break
            }

            if (openedSocket != null) {
                classicSocket = openedSocket
                isConnectedInternal = true
                activeProtocol = "Classic RFCOMM/SPP"
                withContext(Dispatchers.Main) {
                    listener.onConnectionStateChanged(
                        BluetoothProfile.STATE_CONNECTED,
                        activeProtocol,
                        "เชื่อมต่อสายข้อมูล Classic RFCOMM สำเร็จ พร้อมรับคำสั่ง!"
                    )
                    listener.onServicesDiscovered(candidateUuids.size, 1)
                }
                readRfcommLoop(openedSocket.inputStream, device)
            } else if (profileConnected || device.bondState == BluetoothDevice.BOND_BONDED) {
                // Device is paired/connected as a system HID/Audio peripheral!
                isConnectedInternal = true
                activeProtocol = "Classic HID / System Profile"
                withContext(Dispatchers.Main) {
                    listener.onConnectionStateChanged(
                        BluetoothProfile.STATE_CONNECTED,
                        activeProtocol,
                        "จับคู่และเชื่อมต่อโพรไฟล์ระบบสำเร็จ (พร้อมรับปุ่มกดผ่าน Background Bridge)"
                    )
                    listener.onServicesDiscovered(candidateUuids.size, 0)
                }
            } else {
                withContext(Dispatchers.Main) {
                    listener.onError(133, "อุปกรณ์ไม่ตอบสนองการเชื่อมต่อ (ตรวจสอบว่าเปิดโหมดจับคู่และอยู่ในระยะ 5–10m)")
                    listener.onConnectionStateChanged(
                        BluetoothProfile.STATE_DISCONNECTED,
                        activeProtocol,
                        "เชื่อมต่อไม่สำเร็จ"
                    )
                }
                scheduleAutoReconnectIfNeeded()
            }
        }
    }

    private fun tryOpenRfcommSocket(device: BluetoothDevice, uuid: UUID, secure: Boolean): BluetoothSocket? {
        var sock: BluetoothSocket? = null
        return try {
            sock = if (secure) {
                device.createRfcommSocketToServiceRecord(uuid)
            } else {
                device.createInsecureRfcommSocketToServiceRecord(uuid)
            }
            sock.connect()
            sock
        } catch (_: Exception) {
            try {
                sock?.close()
            } catch (_: Exception) {
            }
            null
        }
    }

    private suspend fun readRfcommLoop(input: InputStream, device: BluetoothDevice) {
        val buf = ByteArray(64)
        try {
            while (currentCoroutineContext().isActive) {
                val readCount = input.read(buf)
                if (readCount <= 0) break
                val packet = buf.copyOfRange(0, readCount)
                handleIncomingPacket("RFCOMM:${device.address}", packet)
            }
        } catch (e: Exception) {
            Log.d(TAG, "RFCOMM stream ended: ${e.message}")
        } finally {
            isConnectedInternal = false
            try {
                classicSocket?.close()
            } catch (_: Exception) {
            }
            classicSocket = null
            withContext(Dispatchers.Main) {
                listener.onConnectionStateChanged(
                    BluetoothProfile.STATE_DISCONNECTED,
                    activeProtocol,
                    "สายข้อมูล Classic ถูกตัดการเชื่อมต่อ"
                )
            }
            scheduleAutoReconnectIfNeeded()
        }
    }

    private fun tryConnectSystemProfiles(adapter: BluetoothAdapter?, device: BluetoothDevice): Boolean {
        if (adapter == null) return false
        var requested = false
        val profiles = listOf(PROFILE_HID_HOST, BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
        for (profileId in profiles) {
            try {
                adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        try {
                            val connectMethod = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                            connectMethod.invoke(proxy, device)
                        } catch (_: Exception) {
                        } finally {
                            adapter.closeProfileProxy(profile, proxy)
                        }
                    }

                    override fun onServiceDisconnected(profile: Int) {}
                }, profileId)
                requested = true
            } catch (_: Exception) {
            }
        }
        return requested && device.bondState == BluetoothDevice.BOND_BONDED
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                // CRITICAL: Always close failed BluetoothGatt instance to prevent exhausting Android's 6-client limit!
                try {
                    gatt.close()
                } catch (_: Exception) {
                }
                if (bluetoothGatt === gatt) {
                    bluetoothGatt = null
                }
                isConnectedInternal = false
                opQueue.clear()
                isOpInFlight = false

                val dev = targetDevice
                if (dev != null && leRetryCount == 0) {
                    leRetryCount = 1
                    Log.w(TAG, "GATT status $status on first attempt; retrying with TRANSPORT_AUTO")
                    startGattAttempt(dev, useAutoTransport = true)
                    return
                }
                if (dev != null && leRetryCount == 1 && dev.type != BluetoothDevice.DEVICE_TYPE_LE) {
                    leRetryCount = 2
                    Log.w(TAG, "GATT failed on Dual/Unknown device; falling back to Classic Bluetooth Engine")
                    activeProtocol = "Classic Fallback"
                    connectClassicTransport(dev)
                    return
                }

                listener.onError(status, "สัญญาณ GATT ขัดข้อง (รหัส $status)")
                listener.onConnectionStateChanged(
                    BluetoothProfile.STATE_DISCONNECTED,
                    activeProtocol,
                    "การเชื่อมต่อขัดข้อง (รหัส $status)"
                )
                scheduleAutoReconnectIfNeeded()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    isConnectedInternal = true
                    leRetryCount = 0
                    listener.onConnectionStateChanged(
                        BluetoothProfile.STATE_CONNECTED,
                        activeProtocol,
                        "เชื่อมต่อสำเร็จ! กำลังเปิดช่องสัญญาณความเร็วสูง..."
                    )
                    try {
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    } catch (_: Exception) {
                    }
                    mainHandler.postDelayed({
                        if (bluetoothGatt === gatt) {
                            gatt.discoverServices()
                        }
                    }, 450L)
                    mainHandler.postDelayed(rssiPollRunnable, 4000L)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    isConnectedInternal = false
                    opQueue.clear()
                    isOpInFlight = false
                    try {
                        gatt.close()
                    } catch (_: Exception) {
                    }
                    if (bluetoothGatt === gatt) {
                        bluetoothGatt = null
                    }
                    listener.onConnectionStateChanged(
                        BluetoothProfile.STATE_DISCONNECTED,
                        activeProtocol,
                        "อุปกรณ์ตัดการเชื่อมต่อ"
                    )
                    scheduleAutoReconnectIfNeeded()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onError(status, "ค้นหาบริการ GATT ล้มเหลว ($status)")
                return
            }

            opQueue.clear()
            isOpInFlight = false

            val services = gatt.services
            val hasHidService = gatt.getService(HID_SERVICE_UUID) != null

            // Request bonding automatically for HOGP/HID styluses & shutter remotes if unbonded
            val dev = gatt.device
            if (hasHidService && dev.bondState == BluetoothDevice.BOND_NONE && !bondRequestedForCurrentSession) {
                bondRequestedForCurrentSession = true
                activeProtocol = "BLE HID (Encrypted)"
                try {
                    dev.createBond()
                } catch (_: Exception) {
                }
            }

            // 1. Negotiate larger MTU first (queued!)
            opQueue.add(GattOp.RequestMtu(247))

            // 2. Queue Battery level read if available
            val batteryChar = gatt.getService(BATTERY_SERVICE_UUID)?.getCharacteristic(BATTERY_LEVEL_UUID)
            if (batteryChar != null && (batteryChar.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                opQueue.add(GattOp.ReadChar(batteryChar))
            }

            // 3. Queue ALL notify/indicate characteristics safely one-by-one
            var notifyCount = 0
            for (service in services) {
                for (characteristic in service.characteristics) {
                    val props = characteristic.properties
                    val canNotify = (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                    val canIndicate = (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                    if (!canNotify && !canIndicate) continue

                    notifyCount++
                    try {
                        gatt.setCharacteristicNotification(characteristic, true)
                    } catch (_: Exception) {
                    }
                    val cccd = characteristic.getDescriptor(CCCD_UUID)
                    if (cccd != null) {
                        val value = if (canNotify) {
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        } else {
                            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        }
                        opQueue.add(GattOp.WriteDesc(cccd, value))
                    }
                }
            }

            listener.onServicesDiscovered(services.size, notifyCount)
            processNextGattOp(gatt)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            completeCurrentOpAndContinue(gatt)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            @Suppress("DEPRECATION")
            val bytes = characteristic.value
            handleCharReadResult(gatt, characteristic, bytes, status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            handleCharReadResult(gatt, characteristic, value, status)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                val data = characteristic.value ?: return
                handleIncomingPacket(characteristic.uuid.toString(), data)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingPacket(characteristic.uuid.toString(), value)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if ((status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION ||
                    status == BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION ||
                    status == 137) &&
                !bondRequestedForCurrentSession &&
                gatt.device.bondState == BluetoothDevice.BOND_NONE
            ) {
                bondRequestedForCurrentSession = true
                Log.i(TAG, "Protected characteristic requires encryption; triggering createBond()")
                try {
                    gatt.device.createBond()
                } catch (_: Exception) {
                }
            }
            completeCurrentOpAndContinue(gatt)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                listener.onRemoteRssiUpdated(rssi)
            }
        }
    }

    private fun handleCharReadResult(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray?,
        status: Int
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_UUID && value != null) {
            val pct = value.firstOrNull()?.toInt()?.and(0xFF) ?: -1
            if (pct in 0..100) {
                listener.onBatteryLevelRead(pct)
            }
        }
        completeCurrentOpAndContinue(gatt)
    }

    private fun completeCurrentOpAndContinue(gatt: BluetoothGatt) {
        mainHandler.removeCallbacks(opWatchdog)
        isOpInFlight = false
        processNextGattOp(gatt)
    }

    private fun processNextGattOp(gatt: BluetoothGatt) {
        if (isOpInFlight) return
        val next = opQueue.poll() ?: return
        isOpInFlight = true
        mainHandler.postDelayed(opWatchdog, OP_TIMEOUT_MS)

        val started = try {
            when (next) {
                is GattOp.RequestMtu -> gatt.requestMtu(next.mtu)
                is GattOp.ReadChar -> gatt.readCharacteristic(next.characteristic)
                is GattOp.WriteDesc -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(next.descriptor, next.value) == BluetoothStatusCodes.SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        next.descriptor.value = next.value
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(next.descriptor)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "GATT op threw: ${e.message}")
            false
        }

        if (!started) {
            mainHandler.removeCallbacks(opWatchdog)
            isOpInFlight = false
            mainHandler.post { processNextGattOp(gatt) }
        }
    }

    private fun handleIncomingPacket(sourceKey: String, data: ByteArray) {
        if (data.isEmpty()) return
        val detection = packetDetector.evaluate(sourceKey, data)
        listener.onRawPacketLogged(sourceKey, data, detection.isRelease)
        if (detection.isButtonPress) {
            listener.onButtonTriggered(detection.buttonCode, data, sourceKey)
        }
    }

    private fun scheduleAutoReconnectIfNeeded() {
        val dev = targetDevice ?: return
        if (!isAutoReconnectEnabled) return
        mainHandler.postDelayed({
            if (isAutoReconnectEnabled && !isConnectedInternal && targetDevice?.address == dev.address) {
                Log.d(TAG, "Auto-reconnecting to ${dev.address}...")
                connect(dev, autoReconnect = true)
            }
        }, 3500L)
    }

    private fun registerBondReceiverIfNeeded() {
        if (bondReceiverRegistered) return
        try {
            val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            ContextCompat.registerReceiver(
                context,
                bondStateReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            bondReceiverRegistered = true
        } catch (_: Exception) {
        }
    }

    private fun unregisterBondReceiverQuietly() {
        if (!bondReceiverRegistered) return
        try {
            context.unregisterReceiver(bondStateReceiver)
        } catch (_: Exception) {
        }
        bondReceiverRegistered = false
    }
}
