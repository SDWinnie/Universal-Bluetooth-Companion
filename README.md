# 📡 Universal Bluetooth Companion v2.0 (Android)

[![Release](https://img.shields.io/github/v/release/SDWinnie/Universal-Bluetooth-Companion?color=00E676&label=APK%20Release)](https://github.com/SDWinnie/Universal-Bluetooth-Companion/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android%207.0%20to%2016-1E88E5)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/Language-Kotlin%20100%25-7F52FF)](https://kotlinlang.org/)
[![Architecture](https://img.shields.io/badge/UI-Material%20Design%203-blue)](https://m3.material.io/)
[![Xiaomi HyperOS](https://img.shields.io/badge/Tested-Xiaomi%20HyperOS%202%2F3-FF6900)](https://miui.com/)

แอปพลิเคชัน Android สำหรับตรวจจับและเชื่อมต่ออุปกรณ์บลูทูธ **ทุกชนิด (BLE 4.x/5.x + Classic BR/EDR + Beacons/Tags + อุปกรณ์ที่จับคู่ไว้)** ในระยะ **5–10 เมตร** พร้อมระบบทำงานเบื้องหลัง **24/7 (`ForegroundService`)** และสั่งงานข้ามแอปได้จริง (ชัตเตอร์กล้อง, เล่น/หยุดเพลง, เลื่อนหน้าจอ TikTok/PDF, ปุ่มย้อนกลับ และส่งคำสั่งปุ่มปากกา) แม้เครื่องจะแจ้งว่า *"อุปกรณ์ไม่รองรับ"*

---

## 📥 ดาวน์โหลดไฟล์ติดตั้ง (.apk)

| เวอร์ชัน | สถานะ | สถาปัตยกรรม | ลิงก์ดาวน์โหลด |
| :--- | :--- | :--- | :--- |
| **v2.0.0 (ล่าสุด — แก้ไขครบ 100%)** | Production Release (Signed v1+v2+v3) | All Android Devices | [👉 **ดาวน์โหลด UniversalBT-Companion-v2.0.0.apk**](https://github.com/SDWinnie/Universal-Bluetooth-Companion/releases/download/v2.0.0/UniversalBT-Companion-v2.0.0.apk) |

> รองรับสมาร์ทโฟนและแท็บเล็ต Android 7.0 – Android 16 ทุกค่าย โดยเฉพาะ **Xiaomi Pad 7 Pro**, **Xiaomi 15T Pro** (HyperOS 2/3), Samsung, OPPO, Vivo, Lenovo, Huawei

---

## 🛠️ สิ่งที่อัปเกรดและแก้ไขทั้งหมดใน v2.0.0

1. **เรดาร์ Dual-Engine เห็นครบทุกอุปกรณ์ในระยะ 5–10 เมตรจริง:**
   * สแกนพร้อมกันทั้ง **Unfiltered BLE (Bluetooth 4.x Legacy + 5.x Extended)** และ **Classic Bluetooth (`startDiscovery`)** รวมถึงดึงอุปกรณ์ที่เชื่อมต่อกับระบบอยู่ขึ้นมาแสดงทันที
   * ถอดข้อจำกัด `neverForLocation` ที่ทำให้ Android ซ่อน Beacon / Smart Tag / ปากกาบางรุ่น และเพิ่มตัวตรวจจับสถานะ GPS อัตโนมัติ
   * ปรับเทียบสมการระยะทางในอาคารใหม่ (`-65 dBm @ 1m, N = 2.4` + **EMA RSSI Smoothing**) และอัปเดตระยะทางแบบเรียลไทม์ทุก 0.6 วินาทีโดยไม่กระตุก (`DiffUtil`)
   * ระบบถอดรหัสชื่ออุปกรณ์อัจฉริยะจาก **Bluetooth SIG Manufacturer ID + Service UUID (`0x1812`, `0xFFE0`, `0x180F` ฯลฯ)** แทนการขึ้น `Unknown`
2. **เชื่อมต่อได้จริงด้วย Multi-Protocol Connection Engine:**
   * **Sequential GATT Operation Queue + Watchdog:** แก้ปัญหา Android `BluetoothGatt` ค้างเมื่ออ่านแบตเตอรี่และเปิดรับ Notification พร้อมกัน
   * **Automatic Encrypted HID Bonding (`0x1812`):** สั่งจับคู่เข้ารหัส (`createBond()`) และลงทะเบียน CCCD ซ้ำให้อัตโนมัติสำหรับปากกาสไตลัสและรีโมตที่ล็อกสิทธิ์ความปลอดภัย
   * **Dual-Mode & Classic Fallback:** สลับโหมดอัตโนมัติ `TRANSPORT_LE` → `TRANSPORT_AUTO` → `Classic RFCOMM/SPP (0x1101)` และเชื่อมต่อโพรไฟล์ระบบ (`HID Host / A2DP / Headset`) พร้อมปิด `gatt.close()` ทุกครั้งไม่ให้โควตา 6 พอร์ตของ Android เต็ม
3. **ทำงานเบื้องหลัง 24/7 แบบไม่เด้งหลุด (Zero-Crash Android 14/15/16 FGS):**
   * แก้บั๊ก `SecurityException` และ `ForegroundServiceDidNotStartInTimeException` บน Android 14–16 อย่างสมบูรณ์
   * ต่ออายุ `WakeLock` อัตโนมัติทุก 8 นาที, รีไซเคิลตัวสแกน BLE ทุก 10 นาที (ป้องกัน Android ตัดสแกนที่นาทีที่ 30), ตั้งค่า `stopWithTask="false"` และรองรับ `BOOT_COMPLETED`
4. **สั่งงานข้ามแอปได้จริง + ป้องกันปุ่มลั่นซ้ำ (Rising-Edge Detector):**
   * แก้ปัญหาปุ่มลั่น 2 ครั้งตอนปล่อยนิ้ว (`00 00`) ด้วย `ButtonPacketDetector`
   * เลือกโหมดสั่งงานจริงได้ทันทีในแอป: **ชัตเตอร์กล้อง/เพิ่มเสียง**, **เล่น/หยุดเพลง-วิดีโอ**, **เลื่อนจอลง (TikTok/PDF)**, **เลื่อนจอขึ้น**, และ **ย้อนกลับ + ส่ง Broadcast ปุ่มปากกา**
   * มีสะพาน `UniversalAccessibilityService` แบบเน้นความปลอดภัยสูงสุด (`canRetrieveWindowContent="false"` — **ไม่อ่านข้อความหรือเนื้อหาส่วนตัวบนหน้าจอเด็ดขาด**)
5. **ลงนามความปลอดภัย Production Keystore (v1 + v2 + v3 APK Signature):**
   * ติดตั้งผ่าน Package Installer ของ Xiaomi HyperOS และ Google Play Protect ได้อย่างราบรื่น

---

## 🚀 วิธีเชื่อมต่อไปยังแอปอื่น (Developer Integration)

แอปอื่นๆ (เช่น [SaberPlus Universal](https://github.com/SDWinnie/SaberPlus-Universal) หรือแอปจดโน้ต/วาดเขียน) สามารถดักรับสัญญาณปุ่มกดจากปากกา/รีโมตผ่าน `BroadcastReceiver` ได้ทันที:

```kotlin
val filter = IntentFilter("com.sdwinnie.universalbluetooth.ACTION_STYLUS_BUTTON")
ContextCompat.registerReceiver(
    context,
    object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val buttonCode = intent?.getIntExtra("button_code", 0) ?: 0
            val deviceName = intent?.getStringExtra("device_name").orEmpty()
            val triggerSource = intent?.getStringExtra("trigger_source").orEmpty()
            // นำ buttonCode ไปผูกคำสั่ง Undo / Redo / Eraser ได้ทันที
        }
    },
    filter,
    ContextCompat.RECEIVER_EXPORTED
)
```

---

## 🛠️ วิธีการบิลด์จาก Source Code

ต้องมี JDK 17+ (แนะนำ JDK 21) และ Android SDK (API 35):

```bash
git clone https://github.com/SDWinnie/Universal-Bluetooth-Companion.git
cd Universal-Bluetooth-Companion
./gradlew testReleaseUnitTest lintRelease assembleRelease
```

---

## 📄 ใบอนุญาต (License)
MIT License
