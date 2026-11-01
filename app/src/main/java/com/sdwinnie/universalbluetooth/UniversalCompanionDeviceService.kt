// 🤖 Generated wholely or partially with Google Antigravity
package com.sdwinnie.universalbluetooth

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.Locale

/**
 * Official Android 12–16 CompanionDeviceService (100% Google Play Protect compliant).
 * The Android OS automatically binds and wakes this service whenever an associated
 * Bluetooth peripheral enters the 5–10m radio range, without needing Accessibility
 * or Boot receivers.
 */
@RequiresApi(Build.VERSION_CODES.S)
class UniversalCompanionDeviceService : CompanionDeviceService() {

    companion object {
        private const val TAG = "UniversalCDMService"
    }

    @Deprecated("Used on Android 12 / 12L (API 31-32)")
    override fun onDeviceAppeared(address: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            handleDeviceAppeared(address.uppercase(Locale.ROOT))
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        val address = associationInfo.deviceMacAddress?.toString()?.uppercase(Locale.ROOT) ?: return
        handleDeviceAppeared(address)
    }

    @Deprecated("Used on Android 12 / 12L (API 31-32)")
    override fun onDeviceDisappeared(address: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "Associated companion device left range: $address")
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        val address = associationInfo.deviceMacAddress?.toString()?.uppercase(Locale.ROOT)
        Log.d(TAG, "Associated companion device left range: $address")
    }

    private fun handleDeviceAppeared(address: String) {
        Log.i(TAG, "OS detected associated companion device in range: $address")
        if (!AppPrefs.isServiceEnabled(this)) return
        if (!PermissionHelper.canStartConnectedDeviceForegroundService(this)) return

        try {
            val intent = Intent(this, BluetoothBackgroundService::class.java).apply {
                action = BluetoothBackgroundService.ACTION_CONNECT
                putExtra(BluetoothBackgroundService.EXTRA_DEVICE_ADDRESS, address)
                putExtra(
                    BluetoothBackgroundService.EXTRA_DEVICE_NAME,
                    AppPrefs.getLastDeviceName(this@UniversalCompanionDeviceService) ?: address
                )
            }
            startForegroundService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not auto-launch foreground connection from CDM: ${e.message}")
        }
    }
}
