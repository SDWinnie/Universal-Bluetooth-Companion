// 🤖 Generated wholely or partially with Google Antigravity
package com.sdwinnie.universalbluetooth

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.*
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import java.util.*

/**
 * 24/7 Background Foreground Service that keeps Bluetooth peripherals
 * connected and responsive even when the screen is off or app is closed.
 * Fully compatible with Android 14/15/16 and Xiaomi HyperOS.
 */
@SuppressLint("MissingPermission")
class BluetoothBackgroundService : Service(), BluetoothGattManager.GattEventListener {

    companion object {
        const val TAG = "BTBackgroundService"
        const val CHANNEL_ID = "universal_bt_companion_channel"
        const val NOTIFICATION_ID = 1001

        var isRunning: Boolean = false

        // Actions to Service
        const val ACTION_START_SERVICE = "com.sdwinnie.universalbluetooth.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.sdwinnie.universalbluetooth.STOP_SERVICE"
        const val ACTION_CONNECT = "com.sdwinnie.universalbluetooth.CONNECT"
        const val ACTION_DISCONNECT = "com.sdwinnie.universalbluetooth.DISCONNECT"
        const val EXTRA_DEVICE_ADDRESS = "device_address"

        // Broadcasts from Service to System / Other Apps
        const val BROADCAST_EVENT = "com.sdwinnie.universalbluetooth.EVENT"
        const val BROADCAST_STYLUS_BUTTON = "com.sdwinnie.universalbluetooth.ACTION_STYLUS_BUTTON"
        const val EXTRA_EVENT_TYPE = "event_type"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_BUTTON_CODE = "button_code"
        const val EXTRA_RAW_HEX = "raw_hex"
        const val EXTRA_DEVICE_NAME = "device_name"
    }

    private var gattManager: BluetoothGattManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var lastConnectedDeviceName: String = "ไม่มี"
    private var lastBatteryLevel: Int = -1

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "BluetoothBackgroundService onCreate")
        gattManager = BluetoothGattManager(this, this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UniversalBT:ServiceWakeLock")
        wakeLock?.setReferenceCounted(false)
        wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes rolling safe lock

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SERVICE
        Log.d(TAG, "onStartCommand action: $action")

        when (action) {
            ACTION_STOP_SERVICE -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_DISCONNECT -> {
                gattManager?.disconnect()
                updateNotification("ตัดการเชื่อมต่อแล้ว — รอคำสั่งใหม่")
            }
            ACTION_CONNECT -> {
                val address = intent?.getStringExtra(EXTRA_DEVICE_ADDRESS)
                if (!address.isNullOrEmpty()) {
                    connectToAddress(address)
                }
            }
            ACTION_START_SERVICE -> {
                startAsForeground()
            }
        }

        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = buildNotification("พร้อมตรวจจับและเชื่อมต่ออุปกรณ์เบื้องหลัง (5–10m)")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun connectToAddress(address: String) {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = btManager.adapter
        if (adapter != null && adapter.isEnabled) {
            try {
                val device = adapter.getRemoteDevice(address)
                lastConnectedDeviceName = device.name ?: device.address
                updateNotification("กำลังเชื่อมต่อ: $lastConnectedDeviceName")
                gattManager?.connect(device, autoReconnect = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get remote device: ${e.message}")
            }
        }
    }

    private fun stopForegroundService() {
        gattManager?.disconnect()
        wakeLock?.let { if (it.isHeld) it.release() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        gattManager?.disconnect()
        wakeLock?.let { if (it.isHeld) it.release() }
        Log.d(TAG, "BluetoothBackgroundService onDestroy")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- GATT Events ---

    override fun onConnectionStateChanged(state: Int, statusMessage: String) {
        val isConnected = state == BluetoothProfile.STATE_CONNECTED
        val statusText = if (isConnected) {
            "เชื่อมต่อสำเร็จ: $lastConnectedDeviceName"
        } else {
            "รอการเชื่อมต่อ (Auto-Reconnect กำลังทำงาน)"
        }
        updateNotification(statusText)

        broadcastUiUpdate(
            type = if (isConnected) "CONNECTED" else "DISCONNECTED",
            message = statusMessage,
            deviceName = lastConnectedDeviceName
        )
    }

    override fun onServicesDiscovered(serviceCount: Int) {
        updateNotification("พร้อมใช้งาน: $lastConnectedDeviceName ($serviceCount บริการ)")
        broadcastUiUpdate("SERVICES_READY", "ค้นพบบริการ $serviceCount รายการ พร้อมรับสัญญาณปุ่มกด", lastConnectedDeviceName)
    }

    override fun onBatteryLevelRead(batteryPercent: Int) {
        lastBatteryLevel = batteryPercent
        updateNotification("เชื่อมต่อ: $lastConnectedDeviceName (แบตเตอรี่ $batteryPercent%)")
        broadcastUiUpdate("BATTERY", "ระดับแบตเตอรี่: $batteryPercent%", lastConnectedDeviceName)
    }

    override fun onDataReceived(characteristicUuid: UUID, rawBytes: ByteArray, buttonCode: Int) {
        val hex = rawBytes.joinToString(" ") { "%02X".format(it) }
        Log.d(TAG, "onDataReceived buttonCode=$buttonCode, hex=$hex")

        // 1. Provide tactile feedback
        triggerHapticClick()

        // 2. Broadcast internal UI log
        broadcastUiUpdate("DATA", "รับค่าปุ่ม: $buttonCode (HEX: $hex)", lastConnectedDeviceName, buttonCode, hex)

        // 3. Broadcast system-wide event for other apps (e.g. SaberPlus or Tasker)
        val systemIntent = Intent(BROADCAST_STYLUS_BUTTON).apply {
            putExtra(EXTRA_BUTTON_CODE, buttonCode)
            putExtra(EXTRA_RAW_HEX, hex)
            putExtra(EXTRA_DEVICE_NAME, lastConnectedDeviceName)
            setPackage(null) // Available for apps listening to this action
        }
        sendBroadcast(systemIntent)
    }

    override fun onError(errorCode: Int, message: String) {
        broadcastUiUpdate("ERROR", message, lastConnectedDeviceName)
    }

    private fun triggerHapticClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(35)
        }
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
                description = "แสดงสถานะการเชื่อมต่อบลูทูธและปุ่มกดเบื้องหลัง"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BluetoothBackgroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Universal BT Companion")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "หยุดทำงาน", stopPendingIntent)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(statusText))
    }
}
