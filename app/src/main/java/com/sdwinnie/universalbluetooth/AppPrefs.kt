// 🤖 Generated wholely or partially with Google Antigravity
package com.sdwinnie.universalbluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Persistent preferences and permission helpers for 24/7 operation.
 */
object AppPrefs {
    private const val PREFS_NAME = "universal_bt_prefs"
    private const val KEY_SERVICE_ENABLED = "service_enabled"
    private const val KEY_LAST_ADDRESS = "last_device_address"
    private const val KEY_LAST_NAME = "last_device_name"
    private const val KEY_RANGE_METERS = "range_filter_meters"
    private const val KEY_ACTION_MODE = "button_action_mode"
    private const val KEY_INTERCEPT_HID_KEYS = "intercept_hid_keys"

    const val ACTION_MODE_MEDIA_PLAY_PAUSE = 0
    const val ACTION_MODE_CAMERA_SHUTTER = 1
    const val ACTION_MODE_SCROLL_DOWN = 2
    const val ACTION_MODE_SCROLL_UP = 3
    const val ACTION_MODE_BACK_STYLUS = 4

    fun isServiceEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SERVICE_ENABLED, false)

    fun setServiceEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }

    fun getLastDeviceAddress(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_ADDRESS, null)

    fun getLastDeviceName(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_NAME, null)

    fun setLastDevice(context: Context, address: String?, name: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_ADDRESS, address)
            .putString(KEY_LAST_NAME, name)
            .apply()
    }

    fun getRangeMeters(context: Context): Double =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_RANGE_METERS, 10.0f).toDouble()

    fun setRangeMeters(context: Context, meters: Double) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_RANGE_METERS, meters.toFloat()).apply()
    }

    fun getActionMode(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_ACTION_MODE, ACTION_MODE_CAMERA_SHUTTER)

    fun setActionMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_ACTION_MODE, mode).apply()
    }

    fun isInterceptHidKeysEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_INTERCEPT_HID_KEYS, true)

    fun setInterceptHidKeysEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_INTERCEPT_HID_KEYS, enabled).apply()
    }

    fun actionModeLabel(mode: Int): String = when (mode) {
        ACTION_MODE_MEDIA_PLAY_PAUSE -> "เล่น/หยุดมีเดีย (Play/Pause)"
        ACTION_MODE_CAMERA_SHUTTER -> "กดชัตเตอร์กล้อง / Volume"
        ACTION_MODE_SCROLL_DOWN -> "เลื่อนหน้าจอลง (TikTok/PDF)"
        ACTION_MODE_SCROLL_UP -> "เลื่อนหน้าจอขึ้น (หน้าก่อน)"
        ACTION_MODE_BACK_STYLUS -> "ย้อนกลับ (Back) + ส่งเข้าแอปโน้ต"
        else -> "กดชัตเตอร์กล้อง / Volume"
    }
}

object PermissionHelper {

    fun requiredPermissions(): List<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        // Required across Android versions for Classic Bluetooth discovery & unfiltered BLE beacons
        list.add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list
    }

    fun missingPermissions(context: Context): List<String> =
        requiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    /**
     * Checks specifically whether Android 14+ FOREGROUND_SERVICE_CONNECTED_DEVICE
     * prerequisites (BLUETOOTH_CONNECT or BLUETOOTH_SCAN) are granted so startForeground()
     * can NEVER throw a SecurityException.
     */
    fun canStartConnectedDeviceForegroundService(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val hasConnect = ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        val hasScan = ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
        return hasConnect || hasScan
    }

    fun hasBluetoothScanPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasBluetoothConnectPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isLocationServiceEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }
}
