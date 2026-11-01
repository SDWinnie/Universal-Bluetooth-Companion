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
 * 100% Play-Protect-Compliant Action Executor:
 * Uses ONLY official Android AudioManager Media/Volume APIs and Companion Broadcasts.
 * Requires ZERO AccessibilityService and ZERO Restricted Settings.
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

        // 1. Emit companion stylus/remote button broadcast for drawing/note apps (e.g. SaberPlus Universal)
        val broadcastIntent = Intent(BluetoothBackgroundService.BROADCAST_STYLUS_BUTTON).apply {
            putExtra(BluetoothBackgroundService.EXTRA_BUTTON_CODE, buttonCode)
            putExtra(BluetoothBackgroundService.EXTRA_DEVICE_NAME, deviceName)
            putExtra("trigger_source", triggerSource)
            putExtra("action_mode", mode)
        }
        context.sendBroadcast(broadcastIntent)

        // 2. Execute selected official system action
        val report = when (mode) {
            AppPrefs.ACTION_MODE_MEDIA_PLAY_PAUSE ->
                dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "สั่งเล่น/หยุดมีเดีย (Play/Pause) สำเร็จ")

            AppPrefs.ACTION_MODE_MEDIA_NEXT ->
                dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT, "สั่งเลื่อนเพลง/สไลด์ถัดไป (Next) สำเร็จ")

            AppPrefs.ACTION_MODE_MEDIA_PREVIOUS ->
                dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS, "สั่งย้อนเพลง/สไลด์ก่อนหน้า (Previous) สำเร็จ")

            AppPrefs.ACTION_MODE_VOLUME_SHUTTER ->
                executeVolumeShutterSignal(context)

            AppPrefs.ACTION_MODE_STYLUS_BROADCAST ->
                ExecutionReport(true, "ส่งคำสั่งปุ่มปากกา (#$buttonCode) ไปยังแอปวาดเขียน/โน้ตสำเร็จ")

            else ->
                dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "สั่งเล่น/หยุดมีเดีย (Play/Pause) สำเร็จ")
        }

        Log.d(TAG, "Executed mode=$mode source=$triggerSource code=$buttonCode -> ${report.summary}")

        // 3. Notify in-app Live Event Monitor (restricted strictly to our own package)
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

    private fun dispatchMediaKey(context: Context, keyCode: Int, successMsg: String): ExecutionReport {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ExecutionReport(false, "ไม่พบระบบจัดการเสียง (AudioManager)")

        val now = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
        val upEvent = KeyEvent(now, now + 20, KeyEvent.ACTION_UP, keyCode, 0)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
        return ExecutionReport(true, successMsg)
    }

    private fun executeVolumeShutterSignal(context: Context): ExecutionReport {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ExecutionReport(false, "ไม่พบระบบจัดการเสียง (AudioManager)")

        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            AudioManager.FLAG_SHOW_UI
        )
        return ExecutionReport(true, "ส่งสัญญาณเพิ่มเสียง / ปุ่มชัตเตอร์ Volume+ สำเร็จ")
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
