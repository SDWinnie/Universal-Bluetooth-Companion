// 🤖 Generated wholely or partially with Google Antigravity
package com.sdwinnie.universalbluetooth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Automatically resumes the 24/7 Universal Bluetooth Companion service after
 * device reboot or app update, provided the user enabled the service and
 * granted Bluetooth runtime permissions.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        if (!AppPrefs.isServiceEnabled(context)) return
        if (!PermissionHelper.canStartConnectedDeviceForegroundService(context)) {
            Log.w("BootReceiver", "Skipping auto-start on boot: Bluetooth permissions not granted")
            return
        }

        try {
            val serviceIntent = Intent(context, BluetoothBackgroundService::class.java).apply {
                this.action = BluetoothBackgroundService.ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("BootReceiver", "Failed to auto-start BluetoothBackgroundService: ${e.message}")
        }
    }
}
