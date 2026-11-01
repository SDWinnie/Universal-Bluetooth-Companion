// 🤖 Generated wholely or partially with Google Antigravity
package com.sdwinnie.universalbluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    private lateinit var binding: ActivityMainBinding
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var isScanning = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val discoveredDevices = Collections.synchronizedMap(LinkedHashMap<String, BluetoothDeviceModel>())
    private lateinit var deviceAdapter: DeviceAdapter
    private var currentRangeFilterMeters = 10.0 // Default 10 meters

    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Permissions launcher
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startBleScan()
        } else {
            Toast.makeText(this, "กรุณาอนุญาตสิทธิ์บลูทูธเพื่อเริ่มตรวจจับอุปกรณ์", Toast.LENGTH_LONG).show()
        }
    }

    // Broadcast receiver from background service
    private val serviceEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothBackgroundService.BROADCAST_EVENT) {
                val eventType = intent.getStringExtra(BluetoothBackgroundService.EXTRA_EVENT_TYPE) ?: ""
                val message = intent.getStringExtra(BluetoothBackgroundService.EXTRA_MESSAGE) ?: ""
                val deviceName = intent.getStringExtra(BluetoothBackgroundService.EXTRA_DEVICE_NAME) ?: ""
                val buttonCode = intent.getIntExtra(BluetoothBackgroundService.EXTRA_BUTTON_CODE, 0)
                val hex = intent.getStringExtra(BluetoothBackgroundService.EXTRA_RAW_HEX) ?: ""

                appendLog("[$eventType] $message")

                when (eventType) {
                    "CONNECTED", "SERVICES_READY" -> {
                        binding.cardConnectedDevice.visibility = View.VISIBLE
                        binding.tvConnectedName.text = deviceName
                        binding.tvConnectedDetails.text = message
                    }
                    "DISCONNECTED" -> {
                        binding.cardConnectedDevice.visibility = View.GONE
                    }
                    "DATA" -> {
                        binding.tvConnectedDetails.text = "ปุ่มล่าสุด: $buttonCode (HEX: $hex)"
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = btManager.adapter
        bleScanner = bluetoothAdapter?.bluetoothLeScanner

        setupRecyclerView()
        setupListeners()
        updateServiceCardUi(isServiceRunning())

        binding.tvEventLog.movementMethod = ScrollingMovementMethod()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(BluetoothBackgroundService.BROADCAST_EVENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceEventReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(serviceEventReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(serviceEventReceiver)
    }

    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter { selectedModel ->
            connectToDevice(selectedModel)
        }
        binding.rvDevices.layoutManager = LinearLayoutManager(this)
        binding.rvDevices.adapter = deviceAdapter
    }

    private fun setupListeners() {
        // Background Service Toggle
        binding.switchBackgroundService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startBackgroundService()
            } else {
                stopBackgroundService()
            }
        }

        // Distance Filter Chips
        binding.chipGroupDistance.setOnCheckedStateChangeListener { _, checkedIds ->
            currentRangeFilterMeters = when {
                checkedIds.contains(R.id.chip5m) -> 5.0
                checkedIds.contains(R.id.chip10m) -> 10.0
                else -> 0.0 // All range
            }
            refreshFilteredList()
        }

        // Scan button
        binding.btnToggleScan.setOnClickListener {
            if (isScanning) {
                stopBleScan()
            } else {
                checkPermissionsAndScan()
            }
        }

        // Disconnect button
        binding.btnDisconnect.setOnClickListener {
            val intent = Intent(this, BluetoothBackgroundService::class.java).apply {
                action = BluetoothBackgroundService.ACTION_DISCONNECT
            }
            startService(intent)
            binding.cardConnectedDevice.visibility = View.GONE
        }

        // Clear log
        binding.btnClearLog.setOnClickListener {
            binding.tvEventLog.text = ""
        }

        // Battery optimization exemption helper
        binding.btnIgnoreBatteryOpt.setOnClickListener {
            requestBatteryOptimizationExemption()
        }
    }

    private fun checkPermissionsAndScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "กรุณาเปิดบลูทูธก่อนเริ่มค้นหา", Toast.LENGTH_SHORT).show()
            return
        }

        val requiredPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionsLauncher.launch(missing.toTypedArray())
        } else {
            startBleScan()
        }
    }

    private fun startBleScan() {
        if (isScanning) return
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bleScanner == null) {
            Toast.makeText(this, "ไม่สามารถเข้าถึงตัวสแกนบลูทูธได้", Toast.LENGTH_SHORT).show()
            return
        }

        discoveredDevices.clear()
        refreshFilteredList()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        try {
            bleScanner?.startScan(null, settings, bleScanCallback)
            isScanning = true
            binding.btnToggleScan.text = getString(R.string.btn_stop_scan)
            binding.progressScan.visibility = View.VISIBLE
            appendLog("[Radar] เริ่มการสแกนความถี่สูงในระยะที่กำหนด...")
        } catch (e: Exception) {
            appendLog("[Scan Error] ${e.message}")
        }
    }

    private fun stopBleScan() {
        if (!isScanning) return
        try {
            bleScanner?.stopScan(bleScanCallback)
        } catch (e: Exception) {
            // Ignore
        }
        isScanning = false
        binding.btnToggleScan.text = getString(R.string.btn_scan)
        binding.progressScan.visibility = View.GONE
        appendLog("[Radar] หยุดการสแกนแล้ว")
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val name = result.scanRecord?.deviceName ?: device.name ?: "Unknown"
            val address = device.address ?: return
            val rssi = result.rssi

            val isNew = !discoveredDevices.containsKey(address)
            val model = discoveredDevices.getOrPut(address) {
                BluetoothDeviceModel(device, name, address, rssi)
            }
            model.rssi = rssi
            model.lastSeenMs = System.currentTimeMillis()

            if (isNew) {
                mainHandler.post { refreshFilteredList() }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            appendLog("[Scan Failed] ข้อผิดพลาดรหัส $errorCode")
            stopBleScan()
        }
    }

    private fun refreshFilteredList() {
        val filtered = discoveredDevices.values.filter { model ->
            model.isWithinDistance(currentRangeFilterMeters)
        }.sortedByDescending { it.rssi }

        deviceAdapter.updateList(filtered)
        binding.tvDeviceListHeader.text = "อุปกรณ์ที่ตรวจพบ (${filtered.size} เครื่อง):"
    }

    private fun connectToDevice(model: BluetoothDeviceModel) {
        stopBleScan()
        appendLog("[Connect] กำลังเริ่มเชื่อมต่อกับ ${model.name} (${model.address})...")

        // Ensure background service is running and pass the target address
        val serviceIntent = Intent(this, BluetoothBackgroundService::class.java).apply {
            action = BluetoothBackgroundService.ACTION_CONNECT
            putExtra(BluetoothBackgroundService.EXTRA_DEVICE_ADDRESS, model.address)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        binding.switchBackgroundService.isChecked = true
        updateServiceCardUi(true)
    }

    private fun startBackgroundService() {
        val serviceIntent = Intent(this, BluetoothBackgroundService::class.java).apply {
            action = BluetoothBackgroundService.ACTION_START_SERVICE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        updateServiceCardUi(true)
        appendLog("[Service] เริ่มต้นบริการเบื้องหลังเรียบร้อย (Foreground Mode)")
    }

    private fun stopBackgroundService() {
        val serviceIntent = Intent(this, BluetoothBackgroundService::class.java).apply {
            action = BluetoothBackgroundService.ACTION_STOP_SERVICE
        }
        startService(serviceIntent)
        updateServiceCardUi(false)
        binding.cardConnectedDevice.visibility = View.GONE
        appendLog("[Service] หยุดบริการเบื้องหลังแล้ว")
    }

    private fun updateServiceCardUi(running: Boolean) {
        if (running) {
            binding.tvServiceStatusTitle.text = "บริการเบื้องหลัง: ทำงานอยู่ (Active 24/7)"
            binding.tvServiceStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.connected_green))
            binding.tvServiceStatusDesc.text = "พร้อมตรวจจับและเชื่อมต่ออุปกรณ์บลูทูธ แม้ปิดหน้าจอหรือสลับแอป"
            binding.switchBackgroundService.isChecked = true
        } else {
            binding.tvServiceStatusTitle.text = "บริการเบื้องหลัง: ยังไม่ได้เริ่มทำงาน"
            binding.tvServiceStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.disconnected_red))
            binding.tvServiceStatusDesc.text = "เปิดสวิตช์ด้านบนเพื่อให้แอปจับสัญญาณปุ่มกด/ปากกาได้ตลอดเวลาแม้จอดับ"
            binding.switchBackgroundService.isChecked = false
        }
    }

    private fun isServiceRunning(): Boolean {
        return BluetoothBackgroundService.isRunning
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                }
            } else {
                Toast.makeText(this, "แอปได้รับการยกเว้นการประหยัดแบตเตอรี่เรียบร้อยแล้ว", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun appendLog(msg: String) {
        val time = timeFormatter.format(Date())
        val current = binding.tvEventLog.text.toString()
        val newText = "[$time] $msg\n$current".take(3000)
        binding.tvEventLog.text = newText
    }
}
