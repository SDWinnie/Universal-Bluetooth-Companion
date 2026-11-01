// 🤖 Generated wholely or partially with Google Antigravity
package com.sdwinnie.universalbluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Persistent preferences and permission helpers designed for strict
 * Google Play Protect & Android 13–16 security compliance.
 */
object AppPrefs {
    private const val PREFS_NAME = "universal_bt_prefs"
    private const val KEY_SERVICE_ENABLED = "service_enabled"
    private const val KEY_LAST_ADDRESS = "last_device_address"
    private const val KEY_LAST_NAME = "last_device_name"
    private const val KEY_RANGE_METERS = "range_filter_meters"
    private const val KEY_ACTION_MODE = "button_action_mode"
    private const val KEY_MEDIA_SESSION_CAPTURE = "media_session_capture"
    private const val KEY_PRIVACY_DISCLOSURE_ACCEPTED = "privacy_disclosure_accepted"

    const val ACTION_MODE_MEDIA_PLAY_PAUSE = 0
    const val ACTION_MODE_VOLUME_SHUTTER = 1
    const val ACTION_MODE_MEDIA_NEXT = 2
    const val ACTION_MODE_MEDIA_PREVIOUS = 3
    const val ACTION_MODE_STYLUS_BROADCAST = 4

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
            .getInt(KEY_ACTION_MODE, ACTION_MODE_MEDIA_PLAY_PAUSE)

    fun setActionMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_ACTION_MODE, mode).apply()
    }

    fun isMediaSessionCaptureEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_MEDIA_SESSION_CAPTURE, true)

    fun setMediaSessionCaptureEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MEDIA_SESSION_CAPTURE, enabled).apply()
    }

    fun hasAcceptedPrivacyDisclosure(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PRIVACY_DISCLOSURE_ACCEPTED, false)

    fun setPrivacyDisclosureAccepted(context: Context, accepted: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PRIVACY_DISCLOSURE_ACCEPTED, accepted).apply()
    }

    fun actionModeLabel(mode: Int): String = when (mode) {
        ACTION_MODE_MEDIA_PLAY_PAUSE -> "เล่น/หยุดเพลงและวิดีโอ (Play/Pause)"
        ACTION_MODE_VOLUME_SHUTTER -> "เพิ่มเสียง / สัญญาณชัตเตอร์ (Volume+)"
        ACTION_MODE_MEDIA_NEXT -> "เพลงถัดไป / สไลด์ถัดไป (Next)"
        ACTION_MODE_MEDIA_PREVIOUS -> "เพลงก่อนหน้า / สไลด์ก่อนหน้า (Prev)"
        ACTION_MODE_STYLUS_BROADCAST -> "ส่งคำสั่งปุ่มปากกาเข้าแอปโน้ต (Stylus Bridge)"
        else -> "เล่น/หยุดเพลงและวิดีโอ (Play/Pause)"
    }
}

object PermissionHelper {

    fun requiredPermissions(): List<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
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
