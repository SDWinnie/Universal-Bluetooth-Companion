// 🤖 Generated wholely or partially with Google Antigravity
package com.sdwinnie.universalbluetooth

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 24/7 Background Foreground Service providing:
 * 1. Dual-Engine Bluetooth Radar (Unfiltered BLE + Classic BR/EDR + Paired/Connected devices).
 * 2. Universal Multi-Protocol Connection Engine (Direct GATT + Encrypted BLE HID + Classic RFCOMM/Profiles).
 * 3. Crash-proof Android 14/15/16 ForegroundService lifecycle & Xiaomi HyperOS persistence.
 */
@SuppressLint("MissingPermission")
class BluetoothBackgroundService : Service(), BluetoothGattManager.GattEventListener {

    companion object {
        const val TAG = "BTBackgroundService"
        const val CHANNEL_ID = "universal_bt_companion_channel"
        const val NOTIFICATION_ID = 1001

        private const val MIN_BLE_SCAN_INTERVAL_MS = 6600L
        private const val BLE_SCAN_CYCLE_MS = 10 * 60 * 1000L // Cycle every 10m to prevent Android 30m BLE scan kill
        private const val CLASSIC_REDISCOVERY_MS = 25_000L
        private const val STALE_DEVICE_TTL_MS = 35_000L
        private const val WAKELOCK_RENEW_INTERVAL_MS = 8 * 60 * 1000L
        private const val UI_BROADCAST_THROTTLE_MS = 600L

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var isRadarScanning: Boolean = false
            private set

        @Volatile
        var connectedAddress: String? = null
            private set

        @Volatile
        var connectedName: String? = null
            private set

        @Volatile
        var connectedStatusDetail: String = ""
            private set

        private val discoveredDevicesMap = ConcurrentHashMap<String, BluetoothDeviceModel>()

        fun getDiscoveredDevicesSnapshot(): List<BluetoothDeviceModel> {
            val now = System.currentTimeMillis()
            val activeAddr = connectedAddress
            val iter = discoveredDevicesMap.entries.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                val m = entry.value
                val isCurrentlyConnected = (m.address == activeAddr) || m.isConnected
                if (!isCurrentlyConnected && !m.isBonded && (now - m.lastSeenMs) > STALE_DEVICE_TTL_MS) {
                    iter.remove()
                }
            }
            return discoveredDevicesMap.values.map { model ->
                model.copy(isConnected = (model.address == activeAddr) || model.isConnected)
            }
        }

        fun clearDiscoveredDevices() {
            val keepConnected = connectedAddress?.let { discoveredDevicesMap[it] }
            discoveredDevicesMap.clear()
            if (keepConnected != null) {
                discoveredDevicesMap[keepConnected.address] = keepConnected
            }
        }

        // Actions to Service
        const val ACTION_START_SERVICE = "com.sdwinnie.universalbluetooth.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.sdwinnie.universalbluetooth.STOP_SERVICE"
        const val ACTION_START_RADAR = "com.sdwinnie.universalbluetooth.START_RADAR"
        const val ACTION_STOP_RADAR = "com.sdwinnie.universalbluetooth.STOP_RADAR"
        const val ACTION_CONNECT = "com.sdwinnie.universalbluetooth.CONNECT"
        const val ACTION_DISCONNECT = "com.sdwinnie.universalbluetooth.DISCONNECT"
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val EXTRA_DEVICE_NAME = "device_name"

        // Broadcasts from Service to UI / System / Companion Apps
        const val BROADCAST_EVENT = "com.sdwinnie.universalbluetooth.EVENT"
        const val BROADCAST_DEVICES_UPDATED = "com.sdwinnie.universalbluetooth.DEVICES_UPDATED"
        const val BROADCAST_STYLUS_BUTTON = "com.sdwinnie.universalbluetooth.ACTION_STYLUS_BUTTON"
        const val EXTRA_EVENT_TYPE = "event_type"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_BUTTON_CODE = "button_code"
        const val EXTRA_RAW_HEX = "raw_hex"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var gattManager: BluetoothGattManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var isForegroundStarted = false
    private var isBleScanCallbackActive = false
    private var lastBleScanStartMs = 0L
    private var isUiBroadcastPending = false
    private var discoveryReceiverRegistered = false

    private var lastConnectedDeviceName: String = "อุปกรณ์บลูทูธ"
    private var lastBatteryLevel: Int = -1

    private val wakeLockRenewRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                acquireOrRenewWakeLock()
                mainHandler.postDelayed(this, WAKELOCK_RENEW_INTERVAL_MS)
            }
        }
    }

    private val bleScanCycleRunnable = object : Runnable {
        override fun run() {
            if (isRunning && isRadarScanning) {
                Log.d(TAG, "Cycling BLE scan to avoid Android 30-min scan timeout")
                stopBleScannerInternal()
                mainHandler.postDelayed({
                    if (isRunning && isRadarScanning) {
                        startBleScannerInternal()
                    }
                }, 1200L)
                mainHandler.postDelayed(this, BLE_SCAN_CYCLE_MS)
            }
        }
    }

    private val classicRediscoveryRunnable = object : Runnable {
        override fun run() {
            if (isRunning && isRadarScanning) {
                triggerClassicDiscoverySafe()
                mainHandler.postDelayed(this, CLASSIC_REDISCOVERY_MS)
            }
        }
    }

    private val throttledUiDevicesBroadcast = Runnable {
        isUiBroadcastPending = false
        val intent = Intent(BROADCAST_DEVICES_UPDATED).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private val classicDiscoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice = extractDevice(intent) ?: return
                    val rssiShort = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, (-78).toShort())
                    val rssi = rssiShort.toInt()
                    val extraName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                    val btClass: BluetoothClass? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS, BluetoothClass::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS)
                    }

                    upsertDiscoveredDevice(
                        device = device,
                        rawName = extraName ?: safeDeviceName(device),
                        rssi = rssi,
                        txPower = null,
                        isConnectable = true,
                        manufacturerIds = emptyList(),
                        serviceUuids = emptyList(),
                        majorClass = btClass?.majorDeviceClass ?: safeMajorClass(device)
                    )
                }

                BluetoothDevice.ACTION_NAME_CHANGED -> {
                    val device = extractDevice(intent) ?: return
                    val updatedName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                    if (!updatedName.isNullOrBlank()) {
                        val existing = discoveredDevicesMap[device.address]
                        if (existing != null) {
                            existing.name = updatedName.trim()
                            existing.lastSeenMs = System.currentTimeMillis()
                            scheduleDevicesUpdatedBroadcast()
                        }
                    }
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF) {
                        stopBleScannerInternal()
                        broadcastUiUpdate("STATUS", "บลูทูธของเครื่องถูกปิด — รอการเปิดใหม่อัตโนมัติ", "")
                    } else if (state == BluetoothAdapter.STATE_ON && isRunning) {
                        bleScanner = bluetoothAdapter?.bluetoothLeScanner
                        if (isRadarScanning) {
                            startRadarScan()
                        }
                        val savedAddr = AppPrefs.getLastDeviceAddress(this@BluetoothBackgroundService)
                        if (!savedAddr.isNullOrBlank() && gattManager?.isConnected != true) {
                            connectToAddress(savedAddr, AppPrefs.getLastDeviceName(this@BluetoothBackgroundService))
                        }
                    }
                }
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val scanResult = result ?: return
            handleBleScanResult(scanResult)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleBleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            isBleScanCallbackActive = false
            if (errorCode == SCAN_FAILED_ALREADY_STARTED) {
                isBleScanCallbackActive = true
                return
            }
            Log.w(TAG, "BLE Scan failed with code=$errorCode")
            broadcastUiUpdate("ERROR", "เรดาร์ BLE แจ้งรหัส $errorCode — กำลังรีเซ็ตตัวสแกนอัตโนมัติ", "")
            if (isRunning && isRadarScanning) {
                mainHandler.postDelayed({ startBleScannerInternal() }, MIN_BLE_SCAN_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BluetoothBackgroundService onCreate")
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        gattManager = BluetoothGattManager(this, this)

        createNotificationChannel()
        registerClassicDiscoveryReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SERVICE
        Log.d(TAG, "onStartCommand action=$action")

        if (action == ACTION_STOP_SERVICE) {
            AppPrefs.setServiceEnabled(this, false)
            stopServiceCleanly()
            return START_NOT_STICKY
        }

        // CRITICAL ANDROID 14/15/16 FIX:
        // Every startForegroundService() invocation MUST call startForeground() synchronously
        // and verify BLUETOOTH_CONNECT / BLUETOOTH_SCAN permission first!
        if (!ensureForeground("เรดาร์พร้อมตรวจจับ (5–10m) และเชื่อมต่อเบื้องหลัง 24/7")) {
            AppPrefs.setServiceEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }

        AppPrefs.setServiceEnabled(this, true)

        when (action) {
            ACTION_START_SERVICE -> {
                startRadarScan()
                val savedAddr = AppPrefs.getLastDeviceAddress(this)
                if (!savedAddr.isNullOrBlank() && gattManager?.isConnected != true) {
                    connectToAddress(savedAddr, AppPrefs.getLastDeviceName(this))
                }
            }

            ACTION_START_RADAR -> {
                startRadarScan()
            }

            ACTION_STOP_RADAR -> {
                stopRadarScan()
            }

            ACTION_CONNECT -> {
                val address = intent?.getStringExtra(EXTRA_DEVICE_ADDRESS)
                val nameHint = intent?.getStringExtra(EXTRA_DEVICE_NAME)
                if (!address.isNullOrBlank()) {
                    connectToAddress(address, nameHint)
                }
            }

            ACTION_DISCONNECT -> {
                AppPrefs.setLastDevice(this, null, null)
                connectedAddress = null
                connectedName = null
                connectedStatusDetail = ""
                gattManager?.disconnect()
                scheduleDevicesUpdatedBroadcast()
                updateNotification("ตัดการเชื่อมต่อแล้ว — เรดาร์เบื้องหลังยังทำงานอยู่")
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (isRunning && AppPrefs.isServiceEnabled(this)) {
            updateNotification(
                if (connectedAddress != null) {
                    "ทำงานเบื้องหลังต่อ: ${connectedName ?: connectedAddress}"
                } else {
                    "ทำงานเบื้องหลัง 24/7 (พร้อมรับสัญญาณบลูทูธ)"
                }
            )
        }
    }

    override fun onDestroy() {
        isRunning = false
        isRadarScanning = false
        mainHandler.removeCallbacksAndMessages(null)
        stopBleScannerInternal()
        cancelClassicDiscoverySafe()
        unregisterClassicDiscoveryReceiver()
        gattManager?.shutdown()
        releaseWakeLockQuietly()
        super.onDestroy()
        Log.d(TAG, "BluetoothBackgroundService onDestroy")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureForeground(statusText: String): Boolean {
        if (!PermissionHelper.canStartConnectedDeviceForegroundService(this)) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT/BLUETOOTH_SCAN for connectedDevice FGS")
            isRunning = false
            broadcastUiUpdate("ERROR", "กรุณาอนุญาตสิทธิ์บลูทูธก่อนเปิดบริการเบื้องหลัง", "")
            return false
        }

        return try {
            val notification = buildNotification(statusText)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            if (!isRunning) {
                isRunning = true
                acquireOrRenewWakeLock()
                mainHandler.removeCallbacks(wakeLockRenewRunnable)
                mainHandler.postDelayed(wakeLockRenewRunnable, WAKELOCK_RENEW_INTERVAL_MS)
            }
            isForegroundStarted = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed safely: ${e.message}", e)
            isRunning = false
            broadcastUiUpdate("ERROR", "เริ่มบริการเบื้องหลังไม่สำเร็จ: ${e.localizedMessage}", "")
            false
        }
    }

    private fun startRadarScan() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            broadcastUiUpdate("ERROR", "กรุณาเปิดบลูทูธของเครื่องก่อนสแกนเรดาร์", "")
            return
        }
        if (!PermissionHelper.hasBluetoothScanPermission(this)) {
            broadcastUiUpdate("ERROR", "ยังไม่ได้รับสิทธิ์ BLUETOOTH_SCAN / ตำแหน่งที่ตั้ง", "")
            return
        }

        isRadarScanning = true
        seedBondedAndConnectedDevices()
        scheduleDevicesUpdatedBroadcast()

        startBleScannerInternal()

        mainHandler.removeCallbacks(bleScanCycleRunnable)
        mainHandler.postDelayed(bleScanCycleRunnable, BLE_SCAN_CYCLE_MS)

        // Start Classic Bluetooth discovery alongside BLE (unless mid-connection)
        triggerClassicDiscoverySafe()
        mainHandler.removeCallbacks(classicRediscoveryRunnable)
        mainHandler.postDelayed(classicRediscoveryRunnable, CLASSIC_REDISCOVERY_MS)

        broadcastUiUpdate("RADAR_STARTED", "เรดาร์ Dual-Engine (BLE + Classic) กำลังสแกนรอบตัว...", "")
    }

    private fun stopRadarScan() {
        isRadarScanning = false
        mainHandler.removeCallbacks(bleScanCycleRunnable)
        mainHandler.removeCallbacks(classicRediscoveryRunnable)
        stopBleScannerInternal()
        cancelClassicDiscoverySafe()
        broadcastUiUpdate("RADAR_STOPPED", "หยุดสแกนเรดาร์ชั่วคราว", "")
    }

    private fun startBleScannerInternal() {
        if (isBleScanCallbackActive) return
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled || !PermissionHelper.hasBluetoothScanPermission(this)) return

        val elapsed = System.currentTimeMillis() - lastBleScanStartMs
        if (elapsed < MIN_BLE_SCAN_INTERVAL_MS) {
            val waitMs = MIN_BLE_SCAN_INTERVAL_MS - elapsed + 100L
            mainHandler.postDelayed({
                if (isRunning && isRadarScanning && !isBleScanCallbackActive) {
                    startBleScannerInternal()
                }
            }, waitMs)
            return
        }

        bleScanner = adapter.bluetoothLeScanner
        val scanner = bleScanner ?: return

        val settingsBuilder = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0L)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settingsBuilder.setLegacy(false) // Capture BOTH Bluetooth 4.x Legacy & Bluetooth 5.x Extended packets
        }

        try {
            lastBleScanStartMs = System.currentTimeMillis()
            scanner.startScan(null, settingsBuilder.build(), bleScanCallback)
            isBleScanCallbackActive = true
        } catch (e: Exception) {
            Log.e(TAG, "startScan exception: ${e.message}")
            isBleScanCallbackActive = false
        }
    }

    private fun stopBleScannerInternal() {
        if (!isBleScanCallbackActive) return
        try {
            bleScanner?.stopScan(bleScanCallback)
        } catch (_: Exception) {
        }
        isBleScanCallbackActive = false
    }

    private fun triggerClassicDiscoverySafe() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled || !PermissionHelper.hasBluetoothScanPermission(this)) return
        // Avoid running Classic inquiry right while establishing a new connection
        if (gattManager?.connectedDevice != null && gattManager?.isConnected == false) return
        try {
            if (!adapter.isDiscovering) {
                adapter.startDiscovery()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Classic startDiscovery skipped: ${e.message}")
        }
    }

    private fun cancelClassicDiscoverySafe() {
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
            }
        } catch (_: Exception) {
        }
    }

    private fun seedBondedAndConnectedDevices() {
        if (!PermissionHelper.hasBluetoothConnectPermission(this)) return
        val adapter = bluetoothAdapter ?: return

        // 1. Check active GATT-connected devices
        val gattConnectedSet = mutableSetOf<String>()
        try {
            bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT)?.forEach { dev ->
                gattConnectedSet.add(dev.address)
                upsertDiscoveredDevice(
                    device = dev,
                    rawName = safeDeviceName(dev),
                    rssi = -58,
                    txPower = null,
                    isConnectable = true,
                    manufacturerIds = emptyList(),
                    serviceUuids = emptyList(),
                    majorClass = safeMajorClass(dev),
                    forceSystemConnected = true
                )
            }
        } catch (_: Exception) {
        }

        // 2. Check active Audio / Headset profile connected devices via 100% public BluetoothProfile API
        val publicProfiles = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
        for (profileId in publicProfiles) {
            try {
                adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        try {
                            proxy.connectedDevices?.forEach { dev ->
                                upsertDiscoveredDevice(
                                    device = dev,
                                    rawName = safeDeviceName(dev),
                                    rssi = -58,
                                    txPower = null,
                                    isConnectable = true,
                                    manufacturerIds = emptyList(),
                                    serviceUuids = emptyList(),
                                    majorClass = safeMajorClass(dev),
                                    forceSystemConnected = true
                                )
                            }
                        } catch (_: Exception) {
                        } finally {
                            adapter.closeProfileProxy(profile, proxy)
                        }
                    }

                    override fun onServiceDisconnected(profile: Int) {}
                }, profileId)
            } catch (_: Exception) {
            }
        }

        // 3. Check OS-bonded devices (highlight active ones in 5–10m radar; keep offline paired ones in All Range)
        try {
            adapter.bondedDevices?.forEach { dev ->
                val sysConnected = gattConnectedSet.contains(dev.address) ||
                    (dev.address == connectedAddress)

                val existing = discoveredDevicesMap[dev.address]
                if (sysConnected) {
                    upsertDiscoveredDevice(
                        device = dev,
                        rawName = safeDeviceName(dev),
                        rssi = existing?.rssi?.takeIf { it > -90 } ?: -58,
                        txPower = existing?.txPower,
                        isConnectable = true,
                        manufacturerIds = emptyList(),
                        serviceUuids = emptyList(),
                        majorClass = safeMajorClass(dev),
                        forceSystemConnected = true
                    )
                } else if (existing == null) {
                    // Offline paired device: RSSI -98 (~24m) so it won't clutter 5m/10m live radar unless it broadcasts,
                    // yet appears under "All Range" for 1-tap force reconnect!
                    val resolvedName = BluetoothSignalAnalyzer.resolveDisplayName(
                        rawName = safeDeviceName(dev),
                        majorDeviceClass = safeMajorClass(dev)
                    )
                    discoveredDevicesMap[dev.address] = BluetoothDeviceModel(
                        address = dev.address,
                        name = resolvedName,
                        rssi = -98,
                        txPower = null,
                        deviceType = safeDeviceType(dev),
                        isBonded = true,
                        isConnected = false,
                        isConnectable = true,
                        lastSeenMs = System.currentTimeMillis()
                    )
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun handleBleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val record = result.scanRecord

        val manufacturerIds = mutableListOf<Int>()
        val mfgData = record?.manufacturerSpecificData
        if (mfgData != null) {
            for (i in 0 until mfgData.size()) {
                manufacturerIds.add(mfgData.keyAt(i))
            }
        }

        val serviceUuids = record?.serviceUuids?.mapNotNull { it?.uuid } ?: emptyList()

        val txPower = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            result.txPower != ScanResult.TX_POWER_NOT_PRESENT
        ) {
            result.txPower
        } else {
            record?.txPowerLevel?.takeIf { it in -90..20 }
        }

        val connectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            result.isConnectable
        } else {
            true
        }

        val advertisedName = record?.deviceName?.takeIf { it.isNotBlank() } ?: safeDeviceName(device)

        upsertDiscoveredDevice(
            device = device,
            rawName = advertisedName,
            rssi = result.rssi,
            txPower = txPower,
            isConnectable = connectable,
            manufacturerIds = manufacturerIds,
            serviceUuids = serviceUuids,
            majorClass = safeMajorClass(device)
        )
    }

    private fun upsertDiscoveredDevice(
        device: BluetoothDevice,
        rawName: String?,
        rssi: Int,
        txPower: Int?,
        isConnectable: Boolean,
        manufacturerIds: List<Int>,
        serviceUuids: List<UUID>,
        majorClass: Int?,
        forceSystemConnected: Boolean = false
    ) {
        val address = device.address ?: return
        val existing = discoveredDevicesMap[address]
        val smoothedRssi = BluetoothSignalAnalyzer.smoothRssi(
            previousRssi = existing?.rssi?.takeIf { it > -97 },
            newRssi = rssi
        )

        val candidateName = BluetoothSignalAnalyzer.resolveDisplayName(
            rawName = rawName,
            manufacturerIds = manufacturerIds,
            serviceUuids = serviceUuids,
            majorDeviceClass = majorClass
        )

        val finalName = when {
            !rawName.isNullOrBlank() && !rawName.equals("Unknown", ignoreCase = true) -> rawName.trim()
            existing != null && !existing.name.startsWith("อุปกรณ์บลูทูธ") -> existing.name
            else -> candidateName
        }

        val devType = safeDeviceType(device).let {
            if (it == BluetoothDevice.DEVICE_TYPE_UNKNOWN && existing != null) existing.deviceType else it
        }
        val bonded = safeIsBonded(device)
        val isConn = forceSystemConnected || (address == connectedAddress)

        if (existing != null) {
            existing.name = finalName
            existing.rssi = smoothedRssi
            if (txPower != null) existing.txPower = txPower
            if (devType != BluetoothDevice.DEVICE_TYPE_UNKNOWN) existing.deviceType = devType
            existing.isBonded = bonded
            existing.isConnected = isConn
            existing.isConnectable = isConnectable || existing.isConnectable
            existing.lastSeenMs = System.currentTimeMillis()
        } else {
            discoveredDevicesMap[address] = BluetoothDeviceModel(
                address = address,
                name = finalName,
                rssi = smoothedRssi,
                txPower = txPower,
                deviceType = if (devType == BluetoothDevice.DEVICE_TYPE_UNKNOWN) BluetoothDevice.DEVICE_TYPE_LE else devType,
                isBonded = bonded,
                isConnected = isConn,
                isConnectable = isConnectable,
                lastSeenMs = System.currentTimeMillis()
            )
        }

        scheduleDevicesUpdatedBroadcast()
    }

    private fun scheduleDevicesUpdatedBroadcast() {
        if (isUiBroadcastPending) return
        isUiBroadcastPending = true
        mainHandler.postDelayed(throttledUiDevicesBroadcast, UI_BROADCAST_THROTTLE_MS)
    }

    private fun connectToAddress(address: String, nameHint: String?) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            broadcastUiUpdate("ERROR", "บลูทูธปิดอยู่ ไม่สามารถเชื่อมต่อ $address ได้", nameHint ?: address)
            return
        }
        if (!PermissionHelper.hasBluetoothConnectPermission(this)) {
            broadcastUiUpdate("ERROR", "ยังไม่ได้รับสิทธิ์ BLUETOOTH_CONNECT", nameHint ?: address)
            return
        }

        // Pause Classic inquiry so 2.4GHz radio connects immediately without GATT 133 collision
        cancelClassicDiscoverySafe()

        try {
            val device = adapter.getRemoteDevice(address)
            val resolvedName = nameHint?.takeIf { it.isNotBlank() }
                ?: discoveredDevicesMap[address]?.name
                ?: safeDeviceName(device)
                ?: address

            lastConnectedDeviceName = resolvedName
            AppPrefs.setLastDevice(this, address, resolvedName)
            CompanionAssociationHelper.startPresenceObservationIfPossible(this, address)
            updateNotification("กำลังเชื่อมต่อ: $resolvedName...")
            gattManager?.connect(device, autoReconnect = true)
        } catch (e: Exception) {
            Log.e(TAG, "connectToAddress failed: ${e.message}", e)
            broadcastUiUpdate("ERROR", "เชื่อมต่อไม่สำเร็จ: ${e.localizedMessage}", nameHint ?: address)
        }
    }

    private fun stopServiceCleanly() {
        isRunning = false
        isRadarScanning = false
        connectedAddress = null
        connectedName = null
        connectedStatusDetail = ""
        mainHandler.removeCallbacksAndMessages(null)
        stopBleScannerInternal()
        cancelClassicDiscoverySafe()
        gattManager?.disconnect()
        releaseWakeLockQuietly()
        if (isForegroundStarted) {
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {
            }
            isForegroundStarted = false
        }
        stopSelf()
    }

    // --- GattEventListener Implementation ---

    override fun onConnectionStateChanged(state: Int, protocolLabel: String, statusMessage: String) {
        val activeDev = gattManager?.connectedDevice
        when (state) {
            BluetoothProfile.STATE_CONNECTED -> {
                connectedAddress = activeDev?.address
                connectedName = lastConnectedDeviceName
                connectedStatusDetail = "[$protocolLabel] $statusMessage"
                connectedAddress?.let { addr ->
                    discoveredDevicesMap[addr]?.isConnected = true
                }
                scheduleDevicesUpdatedBroadcast()
                updateNotification("เชื่อมต่อแล้ว ($protocolLabel): $lastConnectedDeviceName")
                broadcastUiUpdate("CONNECTED", "[$protocolLabel] $statusMessage", lastConnectedDeviceName)
            }

            BluetoothProfile.STATE_CONNECTING -> {
                connectedStatusDetail = "[$protocolLabel] $statusMessage"
                updateNotification("กำลังเชื่อมต่อ ($protocolLabel): $lastConnectedDeviceName")
                broadcastUiUpdate("CONNECTING", "[$protocolLabel] $statusMessage", lastConnectedDeviceName)
            }

            BluetoothProfile.STATE_DISCONNECTED -> {
                val prevAddr = connectedAddress ?: activeDev?.address
                if (prevAddr != null) {
                    discoveredDevicesMap[prevAddr]?.isConnected = false
                }
                connectedAddress = null
                connectedStatusDetail = statusMessage
                scheduleDevicesUpdatedBroadcast()
                updateNotification("รอเชื่อมต่ออัตโนมัติ (เรดาร์เบื้องหลังทำงานอยู่)")
                broadcastUiUpdate("DISCONNECTED", statusMessage, lastConnectedDeviceName)
            }
        }
    }

    override fun onServicesDiscovered(serviceCount: Int, notifyCharCount: Int) {
        val detail = "เปิดรับสัญญาณสำเร็จ ($serviceCount บริการ • ดักจับ $notifyCharCount ช่องสัญญาณปุ่มกด)"
        connectedStatusDetail = detail
        updateNotification("พร้อมรับคำสั่ง: $lastConnectedDeviceName ($notifyCharCount ช่องสัญญาณ)")
        broadcastUiUpdate("SERVICES_READY", detail, lastConnectedDeviceName)
    }

    override fun onBatteryLevelRead(batteryPercent: Int) {
        lastBatteryLevel = batteryPercent
        connectedStatusDetail = "พร้อมใช้งาน • แบตเตอรี่อุปกรณ์ $batteryPercent%"
        updateNotification("เชื่อมต่อ: $lastConnectedDeviceName (แบตเตอรี่ $batteryPercent%)")
        broadcastUiUpdate("BATTERY", "ระดับแบตเตอรี่อุปกรณ์: $batteryPercent%", lastConnectedDeviceName)
    }

    override fun onRemoteRssiUpdated(rssi: Int) {
        val addr = connectedAddress ?: return
        val model = discoveredDevicesMap[addr]
        if (model != null) {
            model.rssi = BluetoothSignalAnalyzer.smoothRssi(model.rssi, rssi)
            model.lastSeenMs = System.currentTimeMillis()
            scheduleDevicesUpdatedBroadcast()
        }
    }

    override fun onButtonTriggered(buttonCode: Int, rawBytes: ByteArray, sourceLabel: String) {
        val hex = rawBytes.joinToString(" ") { "%02X".format(it) }
        val shortSource = sourceLabel.takeLast(8)
        Log.d(TAG, "Button triggered! code=$buttonCode hex=$hex source=$shortSource")
        ActionExecutor.execute(
            context = this,
            buttonCode = buttonCode,
            triggerSource = "GATT/SPP ($hex)",
            deviceName = lastConnectedDeviceName
        )
    }

    override fun onRawPacketLogged(sourceLabel: String, rawBytes: ByteArray, isRelease: Boolean) {
        val hex = rawBytes.joinToString(" ") { "%02X".format(it) }
        val tag = if (isRelease) "ปล่อยปุ่ม" else "สัญญาณดิบ"
        broadcastUiUpdate("PACKET", "$tag [$hex]", lastConnectedDeviceName, 0, hex)
    }

    override fun onError(errorCode: Int, message: String) {
        broadcastUiUpdate("ERROR", message, lastConnectedDeviceName)
    }

    // --- Helpers ---

    private fun acquireOrRenewWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UniversalBT:RadarServiceLock").apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.acquire(10 * 60 * 1000L)
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLockQuietly() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
    }

    private fun registerClassicDiscoveryReceiver() {
        if (discoveryReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        try {
            ContextCompat.registerReceiver(
                this,
                classicDiscoveryReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            discoveryReceiverRegistered = true
        } catch (_: Exception) {
        }
    }

    private fun unregisterClassicDiscoveryReceiver() {
        if (!discoveryReceiverRegistered) return
        try {
            unregisterReceiver(classicDiscoveryReceiver)
        } catch (_: Exception) {
        }
        discoveryReceiverRegistered = false
    }

    private fun extractDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private fun safeDeviceName(device: BluetoothDevice): String? =
        try {
            if (PermissionHelper.hasBluetoothConnectPermission(this)) device.name else null
        } catch (_: Exception) {
            null
        }

    private fun safeDeviceType(device: BluetoothDevice): Int =
        try {
            if (PermissionHelper.hasBluetoothConnectPermission(this)) device.type else BluetoothDevice.DEVICE_TYPE_UNKNOWN
        } catch (_: Exception) {
            BluetoothDevice.DEVICE_TYPE_UNKNOWN
        }

    private fun safeIsBonded(device: BluetoothDevice): Boolean =
        try {
            PermissionHelper.hasBluetoothConnectPermission(this) && device.bondState == BluetoothDevice.BOND_BONDED
        } catch (_: Exception) {
            false
        }

    private fun safeMajorClass(device: BluetoothDevice): Int? =
        try {
            if (PermissionHelper.hasBluetoothConnectPermission(this)) device.bluetoothClass?.majorDeviceClass else null
        } catch (_: Exception) {
            null
        }

    private fun broadcastUiUpdate(
        type: String,
        message: String,
        deviceName: String,
        buttonCode: Int = 0,
        hex: String = ""
    ) {
        val intent = Intent(BROADCAST_EVENT).apply {
            putExtra(EXTRA_EVENT_TYPE, type)
            putExtra(EXTRA_MESSAGE, message)
            putExtra(EXTRA_DEVICE_NAME, deviceName)
            putExtra(EXTRA_BUTTON_CODE, buttonCode)
            putExtra(EXTRA_RAW_HEX, hex)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Universal Bluetooth Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "แสดงสถานะเรดาร์ตรวจจับและการเชื่อมต่ออุปกรณ์บลูทูธ 24/7"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BluetoothBackgroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Universal BT Companion (24/7)")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "หยุดบริการ", stopPendingIntent)
            .build()
    }

    private fun updateNotification(statusText: String) {
        if (!isForegroundStarted) return
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID, buildNotification(statusText))
        } catch (_: Exception) {
        }
    }
}
