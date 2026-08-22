# ⚡ MaiWoL

<p align="center">
  <strong>The Ultimate Enterprise-Grade, Privacy-First, and 100% Free & Open-Source (FOSS) Wake-on-LAN Suite for Android.</strong>
</p>

<p align="center">
  <a href="https://maiwol.com"><img src="https://img.shields.io/badge/Official_Website-maiwol.com-0284C7?style=for-the-badge&logo=googlechrome&logoColor=white" alt="Website"></a>
  <a href="https://github.com/Q9550xRX570/MaiWoL/releases/tag/v2.1.0"><img src="https://img.shields.io/badge/Latest_Release-v2.1.0-38BDF8?style=for-the-badge&logo=github&logoColor=white" alt="Release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPLv3-2563EB?style=for-the-badge&logo=gnu&logoColor=white" alt="License"></a>
  <a href="https://f-droid.org"><img src="https://img.shields.io/badge/F--Droid-100%25_FOSS-34D399?style=for-the-badge&logo=fdroid&logoColor=white" alt="FOSS"></a>
  <a href="https://android.com"><img src="https://img.shields.io/badge/Platform-Android_7.0+-4ADE80?style=for-the-badge&logo=android&logoColor=white" alt="Android"></a>
  <a href="https://www.virustotal.com"><img src="https://img.shields.io/badge/VirusTotal-0%2F68_Clean-10B981?style=for-the-badge&logo=virustotal&logoColor=white" alt="VirusTotal"></a>
  <img src="https://img.shields.io/badge/Telemetry-0%25_Zero-EF4444?style=for-the-badge" alt="Zero Telemetry">
</p>

---

## 📖 Overview

**MaiWoL** is an advanced, lightweight (~1.8 MB), and non-intrusive Wake-on-LAN (WoL) management suite crafted for sysadmins, DevOps engineers, homelab builders, and privacy advocates. Developed natively in **Kotlin** and **Jetpack Compose (Material 3)**, it unites raw UDP packet dispatch with device grouping, batch triggers, home screen widgets, quick settings tiles, automated scheduling, and military-grade encryption—all backed by a strict **zero telemetry, zero tracking, and zero advertisement** guarantee.

---

## 🌟 Key Features & Architectural Deep-Dive

### 📂 1. Device Grouping & Category Management *(New in v2.1)*
- **Interactive Reorderable Group Tabs:** Organize computers and servers into categories (e.g., *Home, Office, Lab, Racks*). Long-press and drag tabs horizontally to customize order.
- **Granular Group Customization:** Option to hide device count badges on tabs (e.g., show *Home* instead of *Home (3)*) for a cleaner interface.
- **Instant Group Reassignment:** Quickly move machines between groups directly from device action menus.

### ⚡ 2. One-Tap Batch Group Wake *(New in v2.1)*
- **Simultaneous Group Triggering:** Dedicated top bar action button to wake all computers within the selected group at once.
- **Paced Dispatch Concurrency:** Automatically sequences Magic Packets across all targets with an 80ms stepped delay to prevent network buffer congestion and packet loss.
- **Safety Confirmation:** Protective dialogs prevent accidental mass wake-ups.

### 🔍 3. Architecture Inspector & Recommendation Engine *(New in v2.1)*
- **Runtime ABI Detection:** Identifies the precise binary architecture running on the device (*ARM64 v8A, Universal, ARMeabi v7A, x86_64*).
- **Optimization Advisory:** Automatically suggests downloading the architecture-specific APK for optimal performance and smaller memory footprint when running a Universal build on 64-bit silicon.

### 🚀 4. Precision WoL Packet Engine
- **Multi-Route Transmission:** Dispatches UDP Magic Packets across standard local subnet broadcast (`255.255.255.255` / Subnet Directed) or remote unicast endpoints (WAN / Static IP / DDNS).
- **Enterprise SecureOn Injection:** Supports motherboard/NIC BIOS-level 6-byte password hex injection for hardened infrastructure.
- **Paced Multi-Burst Dispatch:** Configurable packet repetition (1–20 bursts with 50ms interval pacing) ensuring packet delivery over noisy switches and aggressive router drop queues.

### 📱 5. Interactive Home Screen Widgets
- **2x1 Device Card Widget:** Displays device name, MAC address, and a dedicated tactile "Wake" trigger button directly on the Android launcher.
- **1x1 Quick Wake Icon Widget:** Ultra-compact, single-tap launcher widget functioning like an app icon for instantaneous one-touch boot.
- **Isolated Widget Security:** Option to enforce PIN/Biometric authentication specifically for widget interactions before Magic Packets are dispatched.

### ⚡ 6. Quick Settings Tile & Launcher Shortcuts
- **Notification Shade Tile (`WolTileService`):** Pull down the Android quick settings drawer and trigger your primary workstation with a single tap without ever opening the app.
- **Dynamic App Shortcuts:** Long-press the MaiWoL launcher icon to access instant wake shortcuts for your most frequently used machines.

### 📦 7. Military-Grade Encrypted Backup & Restore (`.maiwol`)
- **AES-256-GCM Cryptography:** Protects network infrastructure topologies (MAC addresses, WAN endpoints, port configurations, custom groups, and timers) with **PBKDF2-HMAC-SHA256** key derivation (10,000 iterations, 16-byte random salt, 12-byte IV).
- **Dual-Mode Export:** Export as plain JSON or PIN-encrypted `.maiwol` backup packages.
- **System File Association:** Directly tap any `.maiwol` file from Telegram, WhatsApp, or file managers to initiate an automated, verified import.

### 🟢 8. Multi-Tier Intelligent Status Polling
- **False-Positive Elimination:** Proprietary ICMP parser that filters out deceptive gateway echo replies (e.g., "Destination Host Unreachable" or 100% loss drops).
- **Core OS Daemon Polling:** Interrogates standard OS services (**SMB `445`, RPC `135`, RDP `3389`, SSH `22`, LLMNR/WSD**) to detect running machines when ICMP echo is blocked by firewalls.
- **Tri-State Diagnostics:** Immediate visual status indicators for **ONLINE** (OS Active), **STANDBY** (Powered off, but route verified for WoL), or **UNREACHABLE**.

### 🔍 9. Rootless ARP Subnet Discovery (Shizuku Binder IPC)
- Overcomes Android 10+ restrictions that block user-space access to `/proc/net/arp`.
- Interfaces with the **Shizuku API** via elevated IPC binder transactions to execute privileged `ip neigh` and `sysfs` queries, discovering real physical MAC addresses across the LAN without rooting.

### ⏰ 10. Resilient Background Scheduling Engine
- **Hardware WakeAlarms:** Backed by `AlarmManager.RTC_WAKEUP` with exact alarm scheduling.
- **WakeLock Concurrency:** Automatic `PowerManager.PARTIAL_WAKE_LOCK` acquisition preventing CPU sleep during packet construction and socket dispatch.
- **Reboot Resilience:** Integrated `RECEIVE_BOOT_COMPLETED` broadcast receiver automatically reconstituting all Room Database scheduling queues on phone restart.
- **Calendar & Recurring Timers:** Configure single-run calendar wake-ups or weekly recurring day schedules with interactive drag-and-drop reordering.

### ⚡ 11. Headless External Automation (Tasker / MacroDroid / ADB)
- Dedicated background intent receiver (`com.mai.wol.ACTION_WAKE_DEVICE`) capable of processing headless triggers via saved device name, ID, or raw MAC/IP/Port configurations.

### 🛡️ 12. Bank-Grade Security & Privacy Protection
- **Cryptographic PIN Storage:** Stored using irreversible **SHA-256** hash digests.
- **Hardware-Backed Biometrics:** Native Android `BiometricPrompt` (Fingerprint & Face Recognition).
- **Window Anti-Leak Protection:** Enforced `FLAG_SECURE` window flags preventing screen capture and masking previews in the Android Recent Apps switcher.
- **Card Data Masking:** Granular privacy customization to Show, Mask (`*****`), or completely Hide MAC, IP, and Port parameters on screen.

### 🛠️ 13. Built-in Network Diagnostics Suite
- **DNS Resolver:** Native hostname-to-IP and reverse DNS resolution utility.
- **Ping & Latency Tester:** Live round-trip time (RTT) and packet loss diagnostic terminal.

---

## 📥 Verified Standalone Downloads & Checksums (v2.1.0)

Every standalone APK is built in an isolated environment, optimized via R8, and verified through VirusTotal:

| Architecture | Target Device | File | SHA-256 Checksum | VirusTotal Report |
| :--- | :--- | :--- | :--- | :--- |
| **ARM64 v8A** | Modern 64-bit Phones | [Download](https://github.com/Q9550xRX570/MaiWoL/releases/download/v2.1.0/MaiWoL-v2.1.0-arm64-v8a.apk) | `cadc877df223efb657edce153cd0cc3b15d71430a6d89d8e8d74b4f64828f00c` | [🛡️ 0/68 Clean](https://www.virustotal.com/gui/file/cadc877df223efb657edce153cd0cc3b15d71430a6d89d8e8d74b4f64828f00c/detection) |
| **Universal** | All Android Devices | [Download](https://github.com/Q9550xRX570/MaiWoL/releases/download/v2.1.0/MaiWoL-v2.1.0-universal.apk) | `7be75d1b9c361d7907eba417d1401fc05725e40429113f8c4cfe34ac3b1430e2` | [🛡️ 0/68 Clean](https://www.virustotal.com/gui/file/7be75d1b9c361d7907eba417d1401fc05725e40429113f8c4cfe34ac3b1430e2?nocache=1) |
| **ARMeabi v7A** | Legacy 32-bit Phones | [Download](https://github.com/Q9550xRX570/MaiWoL/releases/download/v2.1.0/MaiWoL-v2.1.0-armeabi-v7a.apk) | `bfdbf508adefcf744bc8401ca39d5a73e74ee240ca341f3db2924a7fea5254d5` | [🛡️ 0/68 Clean](https://www.virustotal.com/gui/file/bfdbf508adefcf744bc8401ca39d5a73e74ee240ca341f3db2924a7fea5254d5?nocache=1) |
| **x86_64** | Emulators & Android PCs | [Download](https://github.com/Q9550xRX570/MaiWoL/releases/download/v2.1.0/MaiWoL-v2.1.0-x86_64.apk) | `6de860c9b7a4ad0def03d4f7480522485fe6b007dd35ff8bcac71a2cf1f528cf` | [🛡️ 0/68 Clean](https://www.virustotal.com/gui/file/6de860c9b7a4ad0def03d4f7480522485fe6b007dd35ff8bcac71a2cf1f528cf?nocache=1) |

---

## 🧪 Google Play Closed Beta Program

We are actively maintaining our **Google Play Closed Testing** channel. To install and test **MaiWoL** directly through the Play Store:

1. **Join the Testers Group:** [Google Group for Testers](https://groups.google.com/g/maiwol-testers)
2. **Opt-in via Web:** [Google Play Testing Opt-in](https://play.google.com/apps/testing/com.mai.wol)
3. **Download on Play Store:** [MaiWoL on Google Play](https://play.google.com/store/apps/details?id=com.mai.wol)

*For questions or support, reach us at [contact@maiwol.com](mailto:contact@maiwol.com).*

---

## 📲 Headless External Automation (Tasker / MacroDroid / ADB)

Trigger background wake-ups without launching the graphical interface:

- **Action:** `com.mai.wol.ACTION_WAKE_DEVICE`
- **Package:** `com.mai.wol`

### Intent Extra Parameters:
| Extra Key | Type | Description | Example |
| :--- | :--- | :--- | :--- |
| `device_name` | String | Target saved device name | `"Workstation"` |
| `device_id` | Long / Int / String | Target saved device ID | `1` |
| `mac_address` | String | Direct hardware MAC address | `"AA:BB:CC:DD:EE:FF"` |
| `ip_address` | String | Optional WAN / DDNS target | `"home.ddns.net"` |
| `local_ip` | String | Optional Local subnet IP | `"192.168.1.100"` |
| `port` | Int / String | Target UDP Port *(Default: 9)* | `9` |
| `packet_count` | Int / String | Packet burst count *(1–20)* | `5` |
| `secure_on` | String | Optional 6-byte hex SecureOn password | `"123456789ABC"` |

### Terminal / ADB Command Example:
```bash
adb shell am broadcast -a com.mai.wol.ACTION_WAKE_DEVICE -p com.mai.wol --es device_name "Workstation"
```
🔒 Privacy & Freedom Guarantee
0% Telemetry & Analytics: No Firebase, no Google Analytics, no third-party SDK trackers.
0% Advertisements: Zero ad banners, native ads, or tracking cookies.
Direct Point-to-Point Networking: All socket connections are strictly negotiated between your Android device and the target network addresses you specify.
🛠️ Tech Stack & Build from Source
Language: Kotlin 2.0+
UI Framework: Jetpack Compose & Material 3
Database: Room DB (v5) with Coroutine Flows
Privileged IPC: Shizuku API
Cryptography: AES-256-GCM / PBKDF2-HMAC-SHA256 / SHA-256
Building locally:
code
Bash
# Clone the repository
git clone https://github.com/Q9550xRX570/MaiWoL.git
cd MaiWoL

# Compile optimized release binaries
./gradlew assembleRelease
Compiled APK artifacts will be output to app/build/outputs/apk/release/.
📜 License
MaiWoL is released under the GNU General Public License v3.0 (GPL-3.0). You are free to inspect, modify, and redistribute this software in accordance with the GNU GPL-3.0 terms.