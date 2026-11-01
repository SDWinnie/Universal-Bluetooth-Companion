// 🤖 Generated wholely or partially with Google Antigravity
package com.sdwinnie.universalbluetooth

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent

/**
 * Executes real cross-app background actions when a Bluetooth stylus, shutter,
 * clicker, or GATT/RFCOMM button is pressed.
 */
object ActionExecutor {

    private const val TAG = "ActionExecutor"

    data class ExecutionReport(
        val success: Boolean,
        val summary: String
    )

    fun execute(
        context: Context,
        buttonCode: Int,
        triggerSource: String,
        deviceName: String = AppPrefs.getLastDeviceName(context) ?: "Universal BT"
    ): ExecutionReport {
        triggerHapticClick(context)

        val mode = AppPrefs.getActionMode(context)

        // 1. Always emit explicit stylus/button broadcast for companion note apps & automation tools
        val broadcastIntent = Intent(BluetoothBackgroundService.BROADCAST_STYLUS_BUTTON).apply {
            putExtra(BluetoothBackgroundService.EXTRA_BUTTON_CODE, buttonCode)
            putExtra(BluetoothBackgroundService.EXTRA_DEVICE_NAME, deviceName)
            putExtra("trigger_source", triggerSource)
            putExtra("action_mode", mode)
        }
        context.sendBroadcast(broadcastIntent)

        // 2. Execute user-selected cross-app action
        val report = when (mode) {
            AppPrefs.ACTION_MODE_MEDIA_PLAY_PAUSE -> dispatchMediaPlayPause(context)
            AppPrefs.ACTION_MODE_CAMERA_SHUTTER -> executeCameraOrVolumeShutter(context)
            AppPrefs.ACTION_MODE_SCROLL_DOWN -> executeScroll(scrollDown = true)
            AppPrefs.ACTION_MODE_SCROLL_UP -> executeScroll(scrollDown = false)
            AppPrefs.ACTION_MODE_BACK_STYLUS -> executeBackAndStylusBridge()
            else -> executeCameraOrVolumeShutter(context)
        }

        Log.d(TAG, "Executed mode=$mode source=$triggerSource code=$buttonCode -> ${report.summary}")

        // 3. Notify UI live monitor if MainActivity is open
        val uiIntent = Intent(BluetoothBackgroundService.BROADCAST_EVENT).apply {
            putExtra(BluetoothBackgroundService.EXTRA_EVENT_TYPE, "ACTION")
            putExtra(
                BluetoothBackgroundService.EXTRA_MESSAGE,
                "${report.summary} [ปุ่ม #$buttonCode • $triggerSource]"
            )
            putExtra(BluetoothBackgroundService.EXTRA_DEVICE_NAME, deviceName)
            putExtra(BluetoothBackgroundService.EXTRA_BUTTON_CODE, buttonCode)
            setPackage(context.packageName)
        }
        context.sendBroadcast(uiIntent)

        return report
    }

    private fun dispatchMediaPlayPause(context: Context): ExecutionReport {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ExecutionReport(false, "ไม่พบระบบเสียง (AudioManager)")

        val now = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
        val upEvent = KeyEvent(now, now + 20, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
        return ExecutionReport(true, "สั่งเล่น/หยุดมีเดีย (Play/Pause) สำเร็จ")
    }

    private fun executeCameraOrVolumeShutter(context: Context): ExecutionReport {
        val a11y = UniversalAccessibilityService.instance
        if (a11y != null && a11y.isCameraAppInForeground()) {
            val tapped = a11y.performCameraShutterTap()
            if (tapped) {
                return ExecutionReport(true, "สั่งลั่นชัตเตอร์กล้องถ่ายรูปสำเร็จ")
            }
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null) {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI
            )
            return ExecutionReport(
                true,
                if (a11y == null) {
                    "ส่งสัญญาณปุ่มชัตเตอร์/เพิ่มเสียงสำเร็จ (เปิด Accessibility หากต้องการแตะชัตเตอร์ในแอปกล้อง)"
                } else {
                    "ส่งสัญญาณปุ่มชัตเตอร์/เพิ่มเสียงสำเร็จ"
                }
            )
        }
        return ExecutionReport(false, "ไม่สามารถส่งคำสั่งชัตเตอร์ได้")
    }

    private fun executeScroll(scrollDown: Boolean): ExecutionReport {
        val a11y = UniversalAccessibilityService.instance
            ?: return ExecutionReport(
                false,
                "ต้องเปิดสิทธิ์ 'ควบคุมข้ามแอป (Accessibility)' ก่อนจึงจะสั่งเลื่อนหน้าจออัตโนมัติได้"
            )

        val ok = a11y.performVerticalScroll(scrollDown = scrollDown)
        val label = if (scrollDown) "เลื่อนหน้าจอลง (หน้าถัดไป)" else "เลื่อนหน้าจอขึ้น (หน้าก่อน)"
        return if (ok) {
            ExecutionReport(true, "สั่ง$label สำเร็จ")
        } else {
            ExecutionReport(false, "สั่ง$label ไม่สำเร็จ")
        }
    }

    private fun executeBackAndStylusBridge(): ExecutionReport {
        val a11y = UniversalAccessibilityService.instance
        return if (a11y != null) {
            val ok = a11y.performBackAction()
            ExecutionReport(ok, "สั่งย้อนกลับ (Back) + ส่งสัญญาณปุ่มปากกาสำเร็จ")
        } else {
            ExecutionReport(
                true,
                "ส่ง Broadcast ปุ่มปากกาแล้ว (เปิด Accessibility หากต้องการสั่งปุ่มย้อนกลับข้ามแอป)"
            )
        }
    }

    private fun triggerHapticClick(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(35)
            }
        } catch (_: Exception) {
        }
    }
}
