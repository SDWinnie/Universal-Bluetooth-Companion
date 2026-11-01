# 📡 Universal Bluetooth Companion (Android)

[![Release](https://img.shields.io/github/v/release/SDWinnie/Universal-Bluetooth-Companion?color=00E676&label=APK%20Release)](https://github.com/SDWinnie/Universal-Bluetooth-Companion/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android%207.0%20to%2016-1E88E5)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/Language-Kotlin%20100%25-7F52FF)](https://kotlinlang.org/)
[![Architecture](https://img.shields.io/badge/UI-Material%20Design%203-blue)](https://m3.material.io/)
[![Xiaomi HyperOS](https://img.shields.io/badge/Tested-Xiaomi%20HyperOS%202%2F3-FF6900)](https://miui.com/)

แอปพลิเคชัน Android สำหรับตรวจจับและเชื่อมต่ออุปกรณ์บลูทูธ (Bluetooth Low Energy & Classic) ทุกชนิดในระยะ **5–10 เมตร** พร้อมระบบทำงานเบื้องหลัง 24/7 (Background Foreground Service) แม้ปิดหน้าจอ และแก้ปัญหาอุปกรณ์ที่ระบบ Android ปกติขึ้นว่า *"ไม่รองรับ"* หรือ *"จับคู่ไม่ได้"*

---

## 📥 ดาวน์โหลดไฟล์ติดตั้ง (.apk)

| เวอร์ชัน | ขนาดไฟล์ | สถาปัตยกรรม | ลิงก์ดาวน์โหลด |
| :--- | :--- | :--- | :--- |
| **v1.0.0 (ล่าสุด)** | **5.1 MB** | All Android Devices (Universal) | [👉 **ดาวน์โหลด UniversalBT-Companion-v1.0.apk**](https://github.com/SDWinnie/Universal-Bluetooth-Companion/releases/download/v1.0.0/UniversalBT-Companion-v1.0.apk) |

> รองรับทั้งมือถือและแท็บเล็ต Android ทุกค่าย (เช่น **Xiaomi Pad 7 Pro**, **Xiaomi 15T Pro**, Samsung, Huawei, Lenovo, OPPO, Vivo ฯลฯ)

---

## ✨ คุณสมบัติเด่น (Key Features)

### 1. 🎯 เรดาร์ตรวจจับในระยะ 5–10 เมตร (Range-Aware BLE Scanner)
* คำนวณระยะห่างทางกายภาพจริงด้วยโมเดล **Log-Distance Path Loss** จากค่าความแรงสัญญาณ RSSI
* มีตัวกรองระยะสัญญาณในตัว:
  * **ระยะใกล้ (5 เมตร):** กรองเฉพาะอุปกรณ์ที่อยู่รอบโต๊ะทำงานหรือบริเวณใกล้เคียง (~ -50 dBm ถึง -75 dBm)
  * **ระยะกลาง (10 เมตร):** ตรวจจับอุปกรณ์ภายในห้อง (~ -75 dBm ถึง -85 dBm)
  * **ทุกระยะ (All Range):** ค้นหาอุปกรณ์บลูทูธทั้งหมดที่รับสัญญาณได้

### 2. ⚡ Direct GATT Connection (เชื่อมได้แม้เครื่องจะบอกว่าไม่รองรับ)
* ระบบตั้งค่าบลูทูธปกติของ Android มักจะรองรับเฉพาะโพรไฟล์มาตรฐาน เช่น หูฟัง (A2DP) หรือคีย์บอร์ด (Classic HID) ทำให้อุปกรณ์อย่าง **ปากกาสไตลัสบลูทูธ, ปากกาแท็บเล็ตเทียบเท่า, รีโมตชัตเตอร์กล้อง, หรือบอร์ด ESP32/Arduino** ขึ้นว่า *"ไม่รองรับ"* หรือเชื่อมต่อไม่ผ่าน
* **วิธีแก้ของแอปนี้:** ใช้ Direct GATT Client เชื่อมต่อตรงกับฮาร์ดแวร์ข้ามข้อจำกัดของ OS:
  * สแกนและเชื่อมต่อโพรไฟล์ **HID over GATT (0x1812)**
  * ดึงค่าสถานะแบตเตอรี่ **Battery Service (0x180F)**
  * สมัครรับข้อมูลแจ้งเตือน (Notifications / Indications) จาก **Custom Vendor Characteristics** ทั้งหมดโดยอัตโนมัติ

### 3. 🛡️ ทำงานเบื้องหลัง 24/7 (Background Foreground Service)
* ทำงานด้วย **Foreground Service** ประเภท `connectedDevice` ตามข้อกำหนดความปลอดภัยของ Android 14/15/16
* **ระบบ Auto-Reconnect:** เชื่อมต่อกลับให้อัตโนมัติทันทีเมื่อหยิบอุปกรณ์ขึ้นมาใช้งาน หรือเมื่ออุปกรณ์ตื่นจาก Sleep Mode
* **ปุ่มปลดล็อกประหยัดพลังงาน (Battery Whitelist):** มีปุ่มขอข้อยกเว้น Doze Mode โดยตรง ช่วยให้ **Xiaomi HyperOS / MIUI** ไม่แอบตัดการทำงานของ Service ในพื้นหลัง

### 4. 🔒 ความปลอดภัยเป็นอันดับหนึ่ง (Security by Design)
* ใช้สิทธิ์ระบบตามมาตรฐาน Android Scoped Permissions:
  * `BLUETOOTH_SCAN` พร้อมแฟลก `neverForLocation` (ไม่ละเมิดความเป็นส่วนตัวเรื่องพิกัดตำแหน่ง)
  * `BLUETOOTH_CONNECT`
  * `FOREGROUND_SERVICE_CONNECTED_DEVICE`
* ไม่มีการดักจับข้อมูลผิดกฎหมาย ไม่มีการบายพาสรหัสความปลอดภัยของอุปกรณ์อื่น ทุกอย่างทำงานผ่าน Bluetooth Protocol สากล

### 5. 🔔 Live Event Terminal & Haptic Feedback
* หน้าจอมอนิเตอร์แสดงผลการกดปุ่ม, รหัสปุ่ม (Button Code), และค่า Raw HEX สดๆ
* ระบบสั่นสัมผัสตอบสนองเบาๆ (Haptic Feedback) ทุกครั้งที่อุปกรณ์ส่งสัญญาณเข้ามา
* กระจายสัญญาณคำสั่งผ่าน **System Broadcast** (`com.sdwinnie.universalbluetooth.ACTION_STYLUS_BUTTON`) เพื่อให้แอปอื่นๆ ในเครื่อง (เช่น [SaberPlus Universal](https://github.com/SDWinnie/SaberPlus-Universal), Tasker, แอปวาดเขียน) นำไปสั่งงานปุ่มลัดต่อได้ทันที

---

## 🏗️ สถาปัตยกรรมระบบ (System Architecture)

```
[ อุปกรณ์บลูทูธภายนอก (Stylus / Remote / BLE Tag / IoT) ]
                     │ (ระยะ 5–10 เมตร / RSSI)
                     ▼
[ BluetoothLeScanner (SCAN_MODE_LOW_LATENCY) ]
                     │ กรองระยะทาง (Log-Distance Path Loss)
                     ▼
[ BluetoothGattManager (Direct GATT Connection) ]
  ├── Human Interface Device Service (0x1812)
  ├── Battery Level Service (0x180F)
  └── Custom Vendor Notify Characteristics (CCCD 0x2902)
                     │
                     ▼
[ BluetoothBackgroundService (Foreground Service 24/7) ]
  ├── Auto-Reconnect Engine
  ├── Haptic Vibration Feedback
  └── System Broadcast Intent: ACTION_STYLUS_BUTTON
                     │
                     ▼
[ แอปพลิเคชันปลายทาง (SaberPlus, Tasker, วาดภาพ, สื่อมีเดีย) ]
```

---

## 🚀 วิธีเชื่อมต่อไปยังแอปอื่น (Developer Integration)

หากคุณพัฒนาแอปพลิเคชันอื่นบน Android และต้องการรับสัญญาณปุ่มกดจากอุปกรณ์บลูทูธ สามารถลงทะเบียน `BroadcastReceiver` ได้ง่ายๆ:

```kotlin
val filter = IntentFilter("com.sdwinnie.universalbluetooth.ACTION_STYLUS_BUTTON")
context.registerReceiver(object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val buttonCode = intent?.getIntExtra("button_code", 0) ?: 0
        val rawHex = intent?.getStringExtra("raw_hex") ?: ""
        val deviceName = intent?.getStringExtra("device_name") ?: ""

        // ตัวอย่างการนำไปสั่งงาน:
        when (buttonCode) {
            1 -> triggerUndoAction()
            2 -> triggerRedoAction()
            3 -> triggerEraserToggle()
        }
    }
}, filter, Context.RECEIVER_NOT_EXPORTED)
```

---

## 🛠️ วิธีการบิลด์จาก Source Code

ต้องมี JDK 17+ และ Android SDK (API 35):

```bash
git clone https://github.com/SDWinnie/Universal-Bluetooth-Companion.git
cd Universal-Bluetooth-Companion
./gradlew assembleRelease
```

ไฟล์ APK จะอยู่ที่:
`app/build/outputs/apk/release/app-release.apk`

---

## 📄 ใบอนุญาต (License)
โปรเจกต์นี้เปิดให้ใช้งานและพัฒนาต่อภายใต้สัญญาอนุญาตแบบ MIT License
