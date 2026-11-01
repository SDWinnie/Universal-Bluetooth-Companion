# 📡 Universal Bluetooth Companion v2.1 (Play Protect Safe Edition)

[![Release](https://img.shields.io/github/v/release/SDWinnie/Universal-Bluetooth-Companion?color=00E676&label=APK%20Release)](https://github.com/SDWinnie/Universal-Bluetooth-Companion/releases/latest)
[![Security](https://img.shields.io/badge/Security-Google%20Play%20Protect%20Safe-00E676)](https://developers.google.com/android/play-protect)
[![Platform](https://img.shields.io/badge/Platform-Android%207.0%20to%2016-1E88E5)](https://developer.android.com/)
[![Xiaomi HyperOS](https://img.shields.io/badge/Tested-Xiaomi%20HyperOS%202%2F3-FF6900)](https://miui.com/)

แอปพลิเคชัน Android สำหรับตรวจจับและเชื่อมต่ออุปกรณ์บลูทูธ **ทุกชนิด (BLE 4.x/5.x + Classic BR/EDR + Beacons/Tags + อุปกรณ์ที่จับคู่ไว้)** ในระยะ **5–10 เมตร** พร้อมระบบทำงานเบื้องหลัง **24/7 (`CompanionDeviceManager` + `ForegroundService`)** ที่ออกแบบตามมาตรฐานความปลอดภัยของ **Google Play Protect** และ **ไม่ติดปัญหา "การตั้งค่าที่จำกัด (Restricted Settings)"** บน Android 13 / 14 / 15 / 16

---

## 📥 ดาวน์โหลดไฟล์ติดตั้ง (.apk)

| เวอร์ชัน | มาตรฐานความปลอดภัย | สถาปัตยกรรม | ลิงก์ดาวน์โหลด |
| :--- | :--- | :--- | :--- |
| **v2.1.0 (ล่าสุด — Play Protect Safe)** | Zero Accessibility • Zero Internet • Official Android CDM | All Android Devices | [👉 **ดาวน์โหลด UniversalBT-Companion-v2.1.0.apk**](https://github.com/SDWinnie/Universal-Bluetooth-Companion/releases/download/v2.1.0/UniversalBT-Companion-v2.1.0.apk) |

---

## 🛡️ สถาปัตยกรรมความปลอดภัยระดับสูงสุดใน v2.1.0 (Play Protect & Restricted Settings Safe)

1. **ไม่ใช้ `AccessibilityService` 100% (ไม่ติด "การตั้งค่าที่จำกัด" บน Android 13–16):**
   * ตัด `BIND_ACCESSIBILITY_SERVICE` ออกทั้งจาก `AndroidManifest.xml` และจากไบต์โค้ด `classes.dex` อย่างสมบูรณ์ จึงไม่มีการอ่านข้อมูลบนหน้าจอ ไม่มีความเสี่ยงด้านข้อมูลส่วนตัว และติดตั้งใช้งานได้ทันทีโดยไม่ต้องปลดล็อกการตั้งค่าที่จำกัด
2. **ไม่มีสิทธิ์อินเทอร์เน็ต (`Zero INTERNET Permission — Offline 100%`):**
   * แอปทำงานแบบออฟไลน์ภายในเครื่อง 100% จึงเป็นไปไม่ได้ในทางเทคนิคที่ข้อมูลบลูทูธหรือพิกัดของคุณจะถูกส่งออกนอกเครื่อง
3. **ใช้ระบบ `CompanionDeviceManager` + `CompanionDeviceService` มาตรฐานศูนย์ Google Android:**
   * แทนที่การใช้ `BOOT_COMPLETED` หรือ `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (ซึ่งมักถูก Play Protect เพ่งเล็งในแอปที่ติดตั้งนอกสโตร์) ด้วยสถาปัตยกรรม **Android Companion Device Association** อย่างเป็นทางการ ให้ระบบปฏิบัติการ Android ปลุกการเชื่อมต่ออัตโนมัติเมื่ออุปกรณ์บลูทูธเข้ามาในระยะ 5–10 เมตรอย่างถูกกฎหมาย
4. **ปราศจาก Hidden Reflection และบังคับเข้ารหัสลิงก์ (`Encrypted GATT & Secure RFCOMM`):**
   * ใช้เฉพาะ Public Android SDK API 100%, ปิด `android:allowBackup="false"`, และมีหน้าต่างชี้แจงเหตุผลการใช้สิทธิ์อย่างโปร่งใสก่อนขอสิทธิ์ทุกครั้ง
5. **เรดาร์ Dual-Engine 5–10 เมตร + Multi-Protocol Connection Engine:**
   * สแกนครบทั้ง BLE 4.x/5.x และ Classic Bluetooth พร้อม EMA RSSI Smoothing อัปเดตระยะสด
   * เชื่อมต่อตรงด้วย Sequential GATT Queue, Automatic Encrypted HID Bonding (`0x1812`) และ Encrypted RFCOMM (`0x1101`) พร้อมสั่งงานมีเดีย/เสียง/Broadcast ปุ่มปากกาในพื้นหลัง

---

## 🚀 วิธีเชื่อมต่อไปยังแอปอื่น (Developer Integration)

```kotlin
val filter = IntentFilter("com.sdwinnie.universalbluetooth.ACTION_STYLUS_BUTTON")
ContextCompat.registerReceiver(
    context,
    object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val buttonCode = intent?.getIntExtra("button_code", 0) ?: 0
            val deviceName = intent?.getStringExtra("device_name").orEmpty()
            // นำ buttonCode ไปสั่งงาน Undo / Redo / Eraser ได้ทันที
        }
    },
    filter,
    ContextCompat.RECEIVER_EXPORTED
)
```

---

## 🛠️ วิธีการบิลด์จาก Source Code

```bash
git clone https://github.com/SDWinnie/Universal-Bluetooth-Companion.git
cd Universal-Bluetooth-Companion
./gradlew clean testReleaseUnitTest lintRelease assembleRelease
```

---

## 📄 ใบอนุญาต (License)
MIT License
