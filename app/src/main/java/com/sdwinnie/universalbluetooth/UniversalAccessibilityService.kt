// 🤖 Generated wholely or partially with Google Antigravity
package com.sdwinnie.universalbluetooth

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.graphics.Path
import android.provider.Settings
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import java.util.Locale

/**
 * Privacy-first Accessibility Bridge (canRetrieveWindowContent="false"):
 * - Never reads screen text, passwords, or personal content.
 * - Captures external Bluetooth HID stylus/remote button KeyEvents in background.
 * - Performs touchless vertical scroll / camera shutter tap / global Back when triggered.
 */
class UniversalAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UniversalA11yBridge"

        @Volatile
        var instance: UniversalAccessibilityService? = null
            private set

        fun isEnabledInSettings(context: Context): Boolean {
            if (instance != null) return true
            val expected = ComponentName(context, UniversalAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.split(':')
                .mapNotNull { ComponentName.unflattenFromString(it) }
                .any { it == expected }
        }
    }

    @Volatile
    private var currentForegroundPackage: String = ""

    private var lastHidTriggerTimeMs: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "UniversalAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank()) {
                currentForegroundPackage = pkg
            }
        }
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        val keyEvent = event ?: return false
        if (!AppPrefs.isServiceEnabled(this) || !AppPrefs.isInterceptHidKeysEnabled(this)) {
            return false
        }

        val keyCode = keyEvent.keyCode
        // Never intercept critical OS navigation or power buttons
        if (keyCode == KeyEvent.KEYCODE_POWER ||
            keyCode == KeyEvent.KEYCODE_HOME ||
            keyCode == KeyEvent.KEYCODE_BACK ||
            keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
            keyCode == KeyEvent.KEYCODE_SLEEP ||
            keyCode == KeyEvent.KEYCODE_WAKEUP
        ) {
            return false
        }

        if (!isSupportedRemoteKeyCode(keyCode) || !isExternalBluetoothInput(keyEvent)) {
            return false
        }

        val mode = AppPrefs.getActionMode(this)
        // If user is in a Camera app and wants Camera Shutter, let native Volume/Camera HID key pass straight to Camera!
        if (mode == AppPrefs.ACTION_MODE_CAMERA_SHUTTER && isCameraAppInForeground() &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                keyCode == KeyEvent.KEYCODE_CAMERA ||
                keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0) {
                Log.d(TAG, "Passing native Bluetooth shutter key $keyCode directly to Camera app")
            }
            return false
        }

        if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0) {
            val now = System.currentTimeMillis()
            if (now - lastHidTriggerTimeMs >= 180L) {
                lastHidTriggerTimeMs = now
                val inputName = InputDevice.getDevice(keyEvent.deviceId)?.name ?: "BT HID"
                ActionExecutor.execute(
                    context = this,
                    buttonCode = keyCode,
                    triggerSource = "HID (${KeyEvent.keyCodeToString(keyCode)})",
                    deviceName = inputName
                )
            }
            return true
        }

        // Consume matching ACTION_UP for intercepted remote keys so volume bar doesn't pop up
        return keyEvent.action == KeyEvent.ACTION_UP
    }

    fun isCameraAppInForeground(): Boolean {
        val pkg = currentForegroundPackage.lowercase(Locale.ROOT)
        return pkg.contains("camera") || pkg.contains("gcam") || pkg.contains("opencamera")
    }

    fun performVerticalScroll(scrollDown: Boolean): Boolean {
        val dm = resources.displayMetrics
        val centerX = dm.widthPixels * 0.5f
        val startY = if (scrollDown) dm.heightPixels * 0.74f else dm.heightPixels * 0.26f
        val endY = if (scrollDown) dm.heightPixels * 0.24f else dm.heightPixels * 0.76f

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 280L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    fun performCameraShutterTap(): Boolean {
        val dm = resources.displayMetrics
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val tapX = if (isLandscape) dm.widthPixels * 0.88f else dm.widthPixels * 0.50f
        val tapY = if (isLandscape) dm.heightPixels * 0.50f else dm.heightPixels * 0.87f

        val path = Path().apply {
            moveTo(tapX, tapY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 65L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    fun performBackAction(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    private fun isSupportedRemoteKeyCode(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_FOCUS,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY,
            KeyEvent.KEYCODE_STYLUS_BUTTON_SECONDARY,
            KeyEvent.KEYCODE_STYLUS_BUTTON_TERTIARY,
            KeyEvent.KEYCODE_STYLUS_BUTTON_TAIL -> true
            else -> false
        }
    }

    private fun isExternalBluetoothInput(event: KeyEvent): Boolean {
        if (event.deviceId <= 0) return false
        val device = InputDevice.getDevice(event.deviceId) ?: return false
        if (device.isVirtual) return false

        val lowerName = device.name.lowercase(Locale.ROOT)
        // Exclude phone/tablet built-in chassis GPIO / PMIC side volume & power buttons
        val isBuiltInSoCButton = device.vendorId == 0 && device.productId == 0 && (
            lowerName.contains("gpio") ||
                lowerName.contains("pmic") ||
                lowerName.contains("qpnp") ||
                lowerName.contains("mtk-") ||
                lowerName.contains("pwrkey") ||
                lowerName.contains("pon") ||
                lowerName.contains("sec_key") ||
                lowerName == "keypad"
            )
        return !isBuiltInSoCButton
    }
}
