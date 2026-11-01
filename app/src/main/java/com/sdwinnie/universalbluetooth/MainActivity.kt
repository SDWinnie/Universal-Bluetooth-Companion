// 🤖 Generated wholely or partially with Google Antigravity
package com.sdwinnie.universalbluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.sdwinnie.universalbluetooth.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {

    private sealed class PendingTask {
        object StartServiceAndRadar : PendingTask()
        object StartRadarOnly : PendingTask()
        data class ConnectDevice(val model: BluetoothDeviceModel) : PendingTask()
    }

    private lateinit var binding: ActivityMainBinding
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var deviceAdapter: DeviceAdapter

    private var currentRangeFilterMeters = 10.0
    private var isUpdatingSwitchProgrammatically = false
    private var pendingTask: PendingTask? = null
    private var receiverRegistered = false

    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        refreshSystemStatusBadges()
        if (PermissionHelper.canStartConnectedDeviceForegroundService(this)) {
            val task = pendingTask
            pendingTask = null
            if (task != null) {
                executePendingTask(task)
            }
        } else {
            pendingTask = null
            setServiceSwitchSilently(BluetoothBackgroundService.isRunning)
            Toast.makeText(
                this,
                "กรุณาอนุญาตสิทธิ์บลูทูธและตำแหน่งที่ตั้งเพื่อให้ระบบตรวจจับและทำงานเบื้องหลังได้",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (bluetoothAdapter?.isEnabled == true) {
            val task = pendingTask
            pendingTask = null
            if (task != null) {
                executePendingTask(task)
            }
        } else {
            pendingTask = null
            setServiceSwitchSilently(BluetoothBackgroundService.isRunning)
            Toast.makeText(this, "ต้องเปิดบลูทูธก่อนเริ่มสแกนหรือเชื่อมต่อ", Toast.LENGTH_SHORT).show()
        }
    }

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothBackgroundService.BROADCAST_DEVICES_UPDATED -> {
                    refreshDeviceListFromService()
                    syncRadarUiState()
                }

                BluetoothBackgroundService.BROADCAST_EVENT -> {
                    val eventType = intent.getStringExtra(BluetoothBackgroundService.EXTRA_EVENT_TYPE).orEmpty()
                    val message = intent.getStringExtra(BluetoothBackgroundService.EXTRA_MESSAGE).orEmpty()
                    val deviceName = intent.getStringExtra(BluetoothBackgroundService.EXTRA_DEVICE_NAME).orEmpty()

                    appendLog("[$eventType] $message")
                    syncServiceAndConnectionUi(eventType, deviceName, message)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = btManager?.adapter

        currentRangeFilterMeters = AppPrefs.getRangeMeters(this)

        setupRecyclerView()
        restoreSavedFilterAndActionChips()
        setupListeners()

        binding.tvEventLog.movementMethod = ScrollingMovementMethod()
        binding.rvDevices.itemAnimator = null // Smooth live RSSI updates without blink

        // Auto-resume background service if user previously enabled it and permissions exist
        if (AppPrefs.isServiceEnabled(this) &&
            !BluetoothBackgroundService.isRunning &&
            PermissionHelper.canStartConnectedDeviceForegroundService(this)
        ) {
            sendCommandToService(BluetoothBackgroundService.ACTION_START_SERVICE)
        }
    }

    override fun onStart() {
        super.onStart()
        registerServiceReceiver()
    }

    override fun onResume() {
        super.onResume()
        refreshSystemStatusBadges()
        updateServiceCardUi(BluetoothBackgroundService.isRunning)
        syncRadarUiState()
        syncConnectedCardFromServiceSnapshot()
        refreshDeviceListFromService()
    }

    override fun onStop() {
        unregisterServiceReceiver()
        super.onStop()
    }

    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter { selectedModel ->
            ensureReadyAndExecute(PendingTask.ConnectDevice(selectedModel))
        }
        binding.rvDevices.layoutManager = LinearLayoutManager(this)
        binding.rvDevices.adapter = deviceAdapter
    }

    private fun restoreSavedFilterAndActionChips() {
        when (currentRangeFilterMeters) {
            5.0 -> binding.chip5m.isChecked = true
            10.0 -> binding.chip10m.isChecked = true
            else -> binding.chipAll.isChecked = true
        }

        when (AppPrefs.getActionMode(this)) {
            AppPrefs.ACTION_MODE_MEDIA_PLAY_PAUSE -> binding.chipActionMedia.isChecked = true
            AppPrefs.ACTION_MODE_CAMERA_SHUTTER -> binding.chipActionShutter.isChecked = true
            AppPrefs.ACTION_MODE_SCROLL_DOWN -> binding.chipActionScrollDown.isChecked = true
            AppPrefs.ACTION_MODE_SCROLL_UP -> binding.chipActionScrollUp.isChecked = true
            AppPrefs.ACTION_MODE_BACK_STYLUS -> binding.chipActionBack.isChecked = true
            else -> binding.chipActionShutter.isChecked = true
        }
    }

    private fun setupListeners() {
        binding.switchBackgroundService.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSwitchProgrammatically) return@setOnCheckedChangeListener
            if (isChecked) {
                ensureReadyAndExecute(PendingTask.StartServiceAndRadar)
            } else {
                stopBackgroundService()
            }
        }

        binding.btnEnableLocation.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            } catch (_: Exception) {
            }
        }

        binding.btnIgnoreBatteryOpt.setOnClickListener {
            requestBatteryOptimizationExemption()
        }

        binding.btnAccessibilityBridge.setOnClickListener {
            openAccessibilitySettings()
        }

        binding.chipGroupActionMode.setOnCheckedStateChangeListener { _, checkedIds ->
            val mode = when {
                checkedIds.contains(R.id.chipActionMedia) -> AppPrefs.ACTION_MODE_MEDIA_PLAY_PAUSE
                checkedIds.contains(R.id.chipActionShutter) -> AppPrefs.ACTION_MODE_CAMERA_SHUTTER
                checkedIds.contains(R.id.chipActionScrollDown) -> AppPrefs.ACTION_MODE_SCROLL_DOWN
                checkedIds.contains(R.id.chipActionScrollUp) -> AppPrefs.ACTION_MODE_SCROLL_UP
                checkedIds.contains(R.id.chipActionBack) -> AppPrefs.ACTION_MODE_BACK_STYLUS
                else -> AppPrefs.ACTION_MODE_CAMERA_SHUTTER
            }
            AppPrefs.setActionMode(this, mode)
            appendLog("[Action] ตั้งค่าปุ่มกดเป็น: ${AppPrefs.actionModeLabel(mode)}")
        }

        binding.btnTestAction.setOnClickListener {
            val report = ActionExecutor.execute(
                context = this,
                buttonCode = 999,
                triggerSource = "ปุ่มทดสอบในแอป",
                deviceName = "Test Trigger"
            )
            Toast.makeText(this, report.summary, Toast.LENGTH_SHORT).show()
        }

        binding.chipGroupDistance.setOnCheckedStateChangeListener { _, checkedIds ->
            currentRangeFilterMeters = when {
                checkedIds.contains(R.id.chip5m) -> 5.0
                checkedIds.contains(R.id.chip10m) -> 10.0
                else -> 0.0
            }
            AppPrefs.setRangeMeters(this, currentRangeFilterMeters)
            refreshDeviceListFromService()
        }

        binding.btnToggleScan.setOnClickListener {
            if (BluetoothBackgroundService.isRadarScanning) {
                sendCommandToService(BluetoothBackgroundService.ACTION_STOP_RADAR)
                binding.btnToggleScan.text = getString(R.string.btn_scan)
                binding.progressScan.visibility = View.GONE
            } else {
                BluetoothBackgroundService.clearDiscoveredDevices()
                refreshDeviceListFromService()
                ensureReadyAndExecute(PendingTask.StartRadarOnly)
            }
        }

        binding.btnDisconnect.setOnClickListener {
            if (BluetoothBackgroundService.isRunning) {
                val intent = Intent(this, BluetoothBackgroundService::class.java).apply {
                    action = BluetoothBackgroundService.ACTION_DISCONNECT
                }
                startService(intent)
            }
            binding.cardConnectedDevice.visibility = View.GONE
            refreshDeviceListFromService()
        }

        binding.btnClearLog.setOnClickListener {
            binding.tvEventLog.text = ""
        }
    }

    private fun ensureReadyAndExecute(task: PendingTask) {
        val missing = PermissionHelper.missingPermissions(this)
        if (missing.isNotEmpty()) {
            pendingTask = task
            requestPermissionsLauncher.launch(missing.toTypedArray())
            return
        }

        if (bluetoothAdapter == null) {
            setServiceSwitchSilently(false)
            Toast.makeText(this, "อุปกรณ์นี้ไม่รองรับฮาร์ดแวร์บลูทูธ", Toast.LENGTH_LONG).show()
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            pendingTask = task
            try {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (_: Exception) {
                Toast.makeText(this, "กรุณาเปิดบลูทูธในแถบตั้งค่าด่วน", Toast.LENGTH_LONG).show()
            }
            return
        }

        executePendingTask(task)
    }

    private fun executePendingTask(task: PendingTask) {
        when (task) {
            is PendingTask.StartServiceAndRadar -> {
                AppPrefs.setServiceEnabled(this, true)
                sendCommandToService(BluetoothBackgroundService.ACTION_START_SERVICE)
                updateServiceCardUi(true)
                binding.btnToggleScan.text = getString(R.string.btn_stop_scan)
                binding.progressScan.visibility = View.VISIBLE
                appendLog("[Service] เริ่มบริการเบื้องหลัง 24/7 + เรดาร์ตรวจจับรอบทิศ")
            }

            is PendingTask.StartRadarOnly -> {
                AppPrefs.setServiceEnabled(this, true)
                sendCommandToService(BluetoothBackgroundService.ACTION_START_RADAR)
                updateServiceCardUi(true)
                binding.btnToggleScan.text = getString(R.string.btn_stop_scan)
                binding.progressScan.visibility = View.VISIBLE
            }

            is PendingTask.ConnectDevice -> {
                val model = task.model
                AppPrefs.setServiceEnabled(this, true)
                appendLog("[Connect] สั่งเชื่อมต่อตรงไปยัง ${model.name} (${model.address})...")

                val intent = Intent(this, BluetoothBackgroundService::class.java).apply {
                    action = BluetoothBackgroundService.ACTION_CONNECT
                    putExtra(BluetoothBackgroundService.EXTRA_DEVICE_ADDRESS, model.address)
                    putExtra(BluetoothBackgroundService.EXTRA_DEVICE_NAME, model.name)
                }
                startServiceForegroundSafe(intent)
                updateServiceCardUi(true)

                binding.cardConnectedDevice.visibility = View.VISIBLE
                binding.tvConnectedBadge.text = "กำลังเชื่อมต่ออุปกรณ์..."
                binding.tvConnectedBadge.setTextColor(ContextCompat.getColor(this, R.color.signal_medium))
                binding.tvConnectedName.text = model.name
                binding.tvConnectedDetails.text = "กำลังเปิดช่องสัญญาณกับ ${model.address}..."
            }
        }
    }

    private fun sendCommandToService(actionStr: String) {
        val intent = Intent(this, BluetoothBackgroundService::class.java).apply {
            action = actionStr
        }
        startServiceForegroundSafe(intent)
    }

    private fun startServiceForegroundSafe(intent: Intent) {
        if (!PermissionHelper.canStartConnectedDeviceForegroundService(this)) {
            Toast.makeText(this, "กรุณาอนุญาตสิทธิ์บลูทูธก่อนใช้งาน", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            appendLog("[Error] เริ่ม Service ไม่สำเร็จ: ${e.localizedMessage}")
        }
    }

    private fun stopBackgroundService() {
        AppPrefs.setServiceEnabled(this, false)
        try {
            val stopIntent = Intent(this, BluetoothBackgroundService::class.java).apply {
                action = BluetoothBackgroundService.ACTION_STOP_SERVICE
            }
            startService(stopIntent)
        } catch (_: Exception) {
        }
        updateServiceCardUi(false)
        binding.btnToggleScan.text = getString(R.string.btn_scan)
        binding.progressScan.visibility = View.GONE
        binding.cardConnectedDevice.visibility = View.GONE
        appendLog("[Service] หยุดการทำงานเบื้องหลังแล้ว")
    }

    private fun refreshDeviceListFromService() {
        val allDevices = BluetoothBackgroundService.getDiscoveredDevicesSnapshot()
        val liveCount = allDevices.count { it.rssi > -97 || it.isConnected }

        val filtered = allDevices.filter { model ->
            if (currentRangeFilterMeters > 0.0 && model.rssi <= -97 && !model.isConnected) {
                false
            } else {
                model.isWithinDistance(currentRangeFilterMeters)
            }
        }.sortedWith(
            compareByDescending<BluetoothDeviceModel> { it.isConnected }
                .thenByDescending { it.rssi }
        )

        deviceAdapter.updateList(filtered)

        val rangeLabel = when (currentRangeFilterMeters) {
            5.0 -> "ระยะ 5 เมตร"
            10.0 -> "ระยะ 10 เมตร"
            else -> "ทุกระยะ"
        }
        binding.tvDeviceListHeader.text =
            "อุปกรณ์ใน$rangeLabel: ${filtered.size} เครื่อง (ตรวจพบรอบตัว $liveCount เครื่อง)"
    }

    private fun syncServiceAndConnectionUi(eventType: String, deviceName: String, message: String) {
        updateServiceCardUi(BluetoothBackgroundService.isRunning)
        syncRadarUiState()

        when (eventType) {
            "CONNECTING" -> {
                binding.cardConnectedDevice.visibility = View.VISIBLE
                binding.tvConnectedBadge.text = "กำลังเชื่อมต่อ..."
                binding.tvConnectedBadge.setTextColor(ContextCompat.getColor(this, R.color.signal_medium))
                if (deviceName.isNotBlank()) binding.tvConnectedName.text = deviceName
                binding.tvConnectedDetails.text = message
            }

            "CONNECTED", "SERVICES_READY", "BATTERY" -> {
                binding.cardConnectedDevice.visibility = View.VISIBLE
                binding.tvConnectedBadge.text = "เชื่อมต่อแล้ว (Active 24/7)"
                binding.tvConnectedBadge.setTextColor(ContextCompat.getColor(this, R.color.connected_green))
                if (deviceName.isNotBlank()) binding.tvConnectedName.text = deviceName
                binding.tvConnectedDetails.text = message
                refreshDeviceListFromService()
            }

            "ACTION" -> {
                if (BluetoothBackgroundService.connectedAddress != null) {
                    binding.tvConnectedDetails.text = message
                }
            }

            "DISCONNECTED" -> {
                if (BluetoothBackgroundService.connectedAddress == null) {
                    binding.cardConnectedDevice.visibility = View.GONE
                }
                refreshDeviceListFromService()
            }
        }
    }

    private fun syncConnectedCardFromServiceSnapshot() {
        val addr = BluetoothBackgroundService.connectedAddress
        if (addr != null) {
            binding.cardConnectedDevice.visibility = View.VISIBLE
            binding.tvConnectedBadge.text = "เชื่อมต่อแล้ว (Active 24/7)"
            binding.tvConnectedBadge.setTextColor(ContextCompat.getColor(this, R.color.connected_green))
            binding.tvConnectedName.text = BluetoothBackgroundService.connectedName ?: addr
            binding.tvConnectedDetails.text = BluetoothBackgroundService.connectedStatusDetail
                .ifBlank { "พร้อมรับสัญญาณปุ่มกดเบื้องหลัง" }
        } else {
            binding.cardConnectedDevice.visibility = View.GONE
        }
    }

    private fun syncRadarUiState() {
        if (BluetoothBackgroundService.isRadarScanning) {
            binding.btnToggleScan.text = getString(R.string.btn_stop_scan)
            binding.progressScan.visibility = View.VISIBLE
        } else {
            binding.btnToggleScan.text = getString(R.string.btn_scan)
            binding.progressScan.visibility = View.GONE
        }
    }

    private fun updateServiceCardUi(running: Boolean) {
        setServiceSwitchSilently(running)
        if (running) {
            binding.tvServiceStatusTitle.text = "บริการเบื้องหลัง: ทำงานอยู่ (Active 24/7)"
            binding.tvServiceStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.connected_green))
            binding.tvServiceStatusDesc.text =
                "เรดาร์และการเชื่อมต่อตรงทำงานต่อเนื่อง แม้ปิดหน้าจอหรือสลับไปใช้แอปอื่น"
        } else {
            binding.tvServiceStatusTitle.text = "บริการเบื้องหลัง: ยังไม่ได้เริ่มทำงาน"
            binding.tvServiceStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.disconnected_red))
            binding.tvServiceStatusDesc.text =
                "เปิดสวิตช์มุมขวาบนเพื่อรันเรดาร์และรับคำสั่งปุ่มกดตลอด 24/7 แม้ปิดหน้าจอ"
        }
    }

    private fun setServiceSwitchSilently(checked: Boolean) {
        if (binding.switchBackgroundService.isChecked == checked) return
        isUpdatingSwitchProgrammatically = true
        binding.switchBackgroundService.isChecked = checked
        isUpdatingSwitchProgrammatically = false
    }

    private fun refreshSystemStatusBadges() {
        val locationOn = PermissionHelper.isLocationServiceEnabled(this)
        binding.cardLocationWarning.visibility = if (locationOn) View.GONE else View.VISIBLE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val exempted = pm?.isIgnoringBatteryOptimizations(packageName) == true
            binding.btnIgnoreBatteryOpt.text = if (exempted) {
                "✓ ปลดล็อกแบตเตอรี่ HyperOS เรียบร้อยแล้ว"
            } else {
                "ปลดล็อกแบตเตอรี่ HyperOS (ป้องกันระบบสั่งปิด)"
            }
        }

        val a11yEnabled = UniversalAccessibilityService.isEnabledInSettings(this)
        binding.btnAccessibilityBridge.text = if (a11yEnabled) {
            "✓ เปิดสิทธิ์ควบคุมข้ามแอป (Accessibility) แล้ว"
        } else {
            "เปิดสิทธิ์ควบคุมข้ามแอป (เลื่อนหน้าจอ / รับปุ่ม HID)"
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm?.isIgnoringBatteryOptimizations(packageName) != true) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            } else {
                Toast.makeText(this, "ปลดล็อกการประหยัดแบตเตอรี่เรียบร้อยแล้ว", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openAccessibilitySettings() {
        Toast.makeText(
            this,
            "เลือก 'แอปที่ดาวน์โหลด' -> 'Universal BT Companion' แล้วกดเปิดใช้งาน",
            Toast.LENGTH_LONG
        ).show()
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
        }
    }

    private fun registerServiceReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothBackgroundService.BROADCAST_EVENT)
            addAction(BluetoothBackgroundService.BROADCAST_DEVICES_UPDATED)
        }
        ContextCompat.registerReceiver(
            this,
            serviceReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun unregisterServiceReceiver() {
        if (!receiverRegistered) return
        try {
            unregisterReceiver(serviceReceiver)
        } catch (_: Exception) {
        }
        receiverRegistered = false
    }

    private fun appendLog(msg: String) {
        val time = timeFormatter.format(Date())
        val current = binding.tvEventLog.text.toString()
        val newText = "[$time] $msg\n$current".take(3600)
        binding.tvEventLog.text = newText
    }
}
