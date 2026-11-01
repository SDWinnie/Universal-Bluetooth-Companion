// 🤖 Generated wholely or partially with Google Antigravity
package com.sdwinnie.universalbluetooth

import android.bluetooth.le.ScanFilter
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Official Android CompanionDeviceManager integration:
 * Grants legitimate OS-blessed background presence monitoring & Play Protect trust
 * without Restricted Settings or Accessibility permissions.
 */
object CompanionAssociationHelper {

    private const val TAG = "CompanionAssocHelper"

    fun isSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return context.getSystemService(Context.COMPANION_DEVICE_SERVICE) != null
    }

    fun isDeviceAssociated(context: Context, address: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val cdm = context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
            ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                cdm.myAssociations.any { info ->
                    info.deviceMacAddress?.toString()?.equals(address, ignoreCase = true) == true
                }
            } else {
                @Suppress("DEPRECATION")
                cdm.associations.any { it.equals(address, ignoreCase = true) }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun startPresenceObservationIfPossible(context: Context, address: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        if (!isDeviceAssociated(context, address)) return false
        val cdm = context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
            ?: return false
        return try {
            @Suppress("DEPRECATION")
            cdm.startObservingDevicePresence(address.uppercase(Locale.ROOT))
            Log.i(TAG, "OS presence monitoring active for $address")
            true
        } catch (e: Exception) {
            Log.w(TAG, "startObservingDevicePresence failed: ${e.message}")
            false
        }
    }

    fun requestAssociation(
        context: Context,
        address: String,
        onLaunchDialog: (IntentSender) -> Unit,
        onAssociated: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onError("ฟีเจอร์ลงทะเบียนคู่เครื่องรองรับบน Android 8.0 ขึ้นไป")
            return
        }

        val cdm = context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
        if (cdm == null) {
            onError("อุปกรณ์นี้ไม่รองรับบริการ CompanionDeviceManager")
            return
        }

        val normalizedAddress = address.uppercase(Locale.ROOT)
        if (isDeviceAssociated(context, normalizedAddress)) {
            startPresenceObservationIfPossible(context, normalizedAddress)
            onAssociated(normalizedAddress)
            return
        }

        val classicFilter = BluetoothDeviceFilter.Builder()
            .setAddress(normalizedAddress)
            .build()

        val bleFilter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(ScanFilter.Builder().setDeviceAddress(normalizedAddress).build())
            .build()

        val request = AssociationRequest.Builder()
            .addDeviceFilter(classicFilter)
            .addDeviceFilter(bleFilter)
            .setSingleDevice(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            cdm.associate(
                request,
                ContextCompat.getMainExecutor(context),
                object : CompanionDeviceManager.Callback() {
                    override fun onAssociationPending(intentSender: IntentSender) {
                        onLaunchDialog(intentSender)
                    }

                    override fun onAssociationCreated(associationInfo: AssociationInfo) {
                        val mac = associationInfo.deviceMacAddress?.toString()?.uppercase(Locale.ROOT)
                            ?: normalizedAddress
                        startPresenceObservationIfPossible(context, mac)
                        onAssociated(mac)
                    }

                    override fun onFailure(error: CharSequence?) {
                        onError(error?.toString() ?: "ไม่พบอุปกรณ์ขณะขอลงทะเบียนคู่เครื่อง (กรุณาเปิดอุปกรณ์ให้อยู่ในระยะ)")
                    }
                }
            )
        } else {
            @Suppress("DEPRECATION")
            cdm.associate(
                request,
                object : CompanionDeviceManager.Callback() {
                    @Deprecated("Deprecated in API 33")
                    override fun onDeviceFound(chooserLauncher: IntentSender) {
                        onLaunchDialog(chooserLauncher)
                    }

                    override fun onFailure(error: CharSequence?) {
                        onError(error?.toString() ?: "ไม่พบอุปกรณ์ขณะขอลงทะเบียนคู่เครื่อง")
                    }
                },
                Handler(Looper.getMainLooper())
            )
        }
    }
}
