# ⚡ MaiWoL

<p align="center">
  <strong>The Ultimate Enterprise-Grade, Privacy-First, and 100% Free & Open-Source (FOSS) Wake-on-LAN & Remote Power Management Suite for Android.</strong>
</p>

<p align="center">
  <a href="https://maiwol.com"><img src="https://img.shields.io/badge/Official_Website-maiwol.com-0284C7?style=for-the-badge&logo=googlechrome&logoColor=white" alt="Website"></a>
  <a href="https://github.com/Q9550xRX570/MaiWoL/releases/tag/v2.2.0"><img src="https://img.shields.io/badge/Latest_Release-v2.2.0-38BDF8?style=for-the-badge&logo=github&logoColor=white" alt="Release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPLv3-2563EB?style=for-the-badge&logo=gnu&logoColor=white" alt="License"></a>
  <a href="https://f-droid.org"><img src="https://img.shields.io/badge/F--Droid-100%25_FOSS-34D399?style=for-the-badge&logo=fdroid&logoColor=white" alt="FOSS"></a>
  <a href="https://android.com"><img src="https://img.shields.io/badge/Platform-Android_7.0+-4ADE80?style=for-the-badge&logo=android&logoColor=white" alt="Android"></a>
  <a href="https://www.virustotal.com/gui/file/a70b98aafcf4b6dbf1f8d458dd9a5118e6532940e94fe20752d0757ac1b35022?nocache=1"><img src="https://img.shields.io/badge/VirusTotal-0%2F68_Clean-10B981?style=for-the-badge&logo=virustotal&logoColor=white" alt="VirusTotal"></a>
  <img src="https://img.shields.io/badge/Telemetry-0%25_Zero-EF4444?style=for-the-badge" alt="Zero Telemetry">
</p>

---

## 📖 Overview

**MaiWoL** is an advanced, lightweight (~1.8 MB), and non-intrusive Wake-on-LAN (WoL) and Remote Power Management suite crafted for sysadmins, DevOps engineers, homelab builders, and privacy advocates. Developed natively in **Kotlin** and **Jetpack Compose (Material 3)**, it unites raw UDP packet dispatch with native OpenSSH/Webhook remote shutdown, device grouping, batch triggers, home screen widgets, quick settings tiles, automated scheduling, and military-grade encryption—all backed by a strict **zero telemetry, zero tracking, and zero advertisement** guarantee.

---

## 🌟 Key Features & Architectural Deep-Dive

### ⏻ 1. Secure Remote Shutdown & Power Control *(New in v2.2)*
- **Native OpenSSH Engine:** Direct terminal command execution over SSH (Windows 10/11 OpenSSH, Linux systemd, macOS, TrueNAS, Proxmox).
- **HTTP Webhook Integration:** Trigger remote shutdowns, reboots, or smart plug automations via customizable `HTTP GET` or `HTTP POST` endpoints with optional Basic Auth headers.
- **Forced Instant Halting (`/f` Flag):** Integrated `/f` parameter ensuring guaranteed shutdown without hanging on unresponsive desktop applications or dialogs (`shutdown /s /f /t 0`).
- **Dual-Route LAN/WAN Auto-Switching:** Attempts low-latency local subnet execution when connected to home Wi-Fi and automatically falls back to WAN / DDNS forwarded ports when on mobile data.
- **Credential Visibility Toggle:** Integrated eye button for viewing and verifying masked SSH/HTTP passwords during configuration.
- **Accidental Trigger Protection:** Tactical confirmation prompts prevent unintended machine shutdowns.

### 📂 2. Device Grouping & Category Management *(v2.1)*
- **Interactive Reorderable Group Tabs:** Organize computers and servers into categories (e.g., *Home, Office, Lab, Racks*). Long-press and drag tabs horizontally to customize order.
- **Granular Group Customization:** Option to hide device count badges on tabs (e.g., show *Home* instead of *Home (3)*) for a cleaner interface.
- **Instant Group Reassignment:** Quickly move machines between groups directly from device action menus.

### ⚡ 3. One-Tap Batch Group Wake *(v2.1)*
- **Simultaneous Group Triggering:** Dedicated top bar action button to wake all computers within the selected group at once.
- **Paced Dispatch Concurrency:** Automatically sequences Magic Packets across all targets with an 80ms stepped delay to prevent network buffer congestion and packet loss.
- **Safety Confirmation:** Protective dialogs prevent accidental mass wake-ups.

### 🔍 4. Architecture Inspector & Recommendation Engine *(v2.1)*
- **Runtime ABI Detection:** Identifies the precise binary architecture running on the device (*ARM64 v8A, Universal, ARMeabi v7A, x86_64*).
- **Optimization Advisory:** Automatically suggests downloading the architecture-specific APK for optimal performance and smaller memory footprint when running a Universal build on 64-bit silicon.

### 🚀 5. Precision WoL Packet Engine
- **Multi-Route Transmission:** Dispatches UDP Magic Packets across standard local subnet broadcast (`255.255.255.255` / Subnet Directed) or remote unicast endpoints (WAN / Static IP / DDNS).
- **Enterprise SecureOn Injection:** Supports motherboard/NIC BIOS-level 6-byte password hex injection for hardened infrastructure.
- **Paced Multi-Burst Dispatch:** Configurable packet repetition (1–20 bursts with 50ms interval pacing) ensuring packet delivery over noisy switches and aggressive router drop queues.

### 📱 6. Interactive Home Screen Widgets
- **2x1 Device Card Widget:** Displays device name, MAC address, and a dedicated tactile "Wake" trigger button directly on the Android launcher.
- **1x1 Quick Wake Icon Widget:** Ultra-compact, single-tap launcher widget functioning like an app icon for instantaneous one-touch boot.
- **Isolated Widget Security:** Option to enforce PIN/Biometric authentication specifically for widget interactions before Magic Packets are dispatched.

### ⚡ 7. Quick Settings Tile & Launcher Shortcuts
- **Notification Shade Tile (`WolTileService`):** Pull down the Android quick settings drawer and trigger your primary workstation with a single tap without ever opening the app.
- **Dynamic App Shortcuts:** Long-press the MaiWoL launcher icon to access instant wake shortcuts for your most frequently used machines.

### 📦 8. Military-Grade Encrypted Backup & Restore (`.maiwol`)
- **AES-256-GCM Cryptography:** Protects network infrastructure topologies (MAC addresses, WAN endpoints, port configurations, custom groups, shutdown profiles, and timers) with **PBKDF2-HMAC-SHA256** key derivation (10,000 iterations, 16-byte random salt, 12-byte IV).
- **Dual-Mode Export:** Export as plain JSON or PIN-encrypted `.maiwol` backup packages.
- **System File Association:** Directly tap any `.maiwol` file from Telegram, WhatsApp, or file managers to initiate an automated, verified import.

### 🟢 9. Multi-Tier Intelligent Status Polling
- **False-Positive Elimination:** Proprietary ICMP parser that filters out deceptive gateway echo replies (e.g., "Destination Host Unreachable" or 100% loss drops).
- **Core OS Daemon Polling:** Interrogates standard OS services (**SMB `445`, RPC `135`, RDP `3389`, SSH `22`, LLMNR/WSD**) to detect running machines when ICMP echo is blocked by firewalls.
- **Tri-State Diagnostics:** Immediate visual status indicators for **ONLINE** (OS Active), **STANDBY** (Powered off, but route verified for WoL), or **UNREACHABLE**.

### 🔍 10. Rootless ARP Subnet Discovery (Shizuku Binder IPC)
- Overcomes Android 10+ restrictions that block user-space access to `/proc/net/arp`.
- Interfaces with the **Shizuku API** via elevated IPC binder transactions to execute privileged `ip neigh` and `sysfs` queries, discovering real physical MAC addresses across the LAN without rooting.

### ⏰ 11. Resilient Background Scheduling Engine
- **Hardware WakeAlarms:** Backed by `AlarmManager.RTC_WAKEUP` with exact alarm scheduling.
- **WakeLock Concurrency:** Automatic `PowerManager.PARTIAL_WAKE_LOCK` acquisition preventing CPU sleep during packet construction and socket dispatch.
- **Reboot Resilience:** Integrated `RECEIVE_BOOT_COMPLETED` broadcast receiver automatically reconstituting all Room Database scheduling queues on phone restart.
- **Calendar & Recurring Timers:** Configure single-run calendar wake-ups or weekly recurring day schedules with interactive drag-and-drop reordering.

### ⚡ 12. Headless External Automation (Tasker / MacroDroid / ADB)
- Dedicated background intent receivers capable of processing headless triggers via saved device name, ID, or raw MAC/IP/Port configurations.

### 🛡️ 13. Bank-Grade Security & Ergonomic Settings Navigation
- **Cryptographic PIN Storage:** Stored using irreversible **SHA-256** hash digests.
- **Hardware-Backed Biometrics:** Native Android `BiometricPrompt` (Fingerprint & Face Recognition).
- **Window Anti-Leak Protection:** Enforced `FLAG_SECURE` window flags preventing screen capture and masking previews in the Android Recent Apps switcher.
- **Card Data Masking:** Granular privacy customization to Show, Mask (`*****`), or completely Hide MAC, IP, and Port parameters on screen.
- **Adaptive Scrollable Settings Drawer:** Completely responsive navigation drawer layout ensuring touch targets and options remain perfectly accessible across all screen dimensions and font scales.

### 🛠️ 14. Built-in Network Diagnostics Suite
- **DNS Resolver:** Native hostname-to-IP and reverse DNS resolution utility.
- **Ping & Latency Tester:** Live round-trip time (RTT) and packet loss diagnostic terminal.

---

## 📥 Verified Standalone Downloads & Checksums (v2.2.0)

Every standalone APK is built in an isolated environment, optimized via R8, and verified through VirusTotal:

| Architecture | Target Device | File | SHA-256 Checksum | VirusTotal Report |
| :--- | :--- | :--- | :--- | :--- |
| **ARM64 v8A** | Modern 64-bit Phones | [Download](https://github.com/Q9550xRX570/MaiWoL/releases/download/v2.2.0/MaiWoL-v2.2.0-arm64-v8a.apk) | `44572d0deb6fc9c64d0d2b9111c3dfb32be779df5a9e0eddfa77f0503fb3efde` | [🛡️ 0/68 Clean](https://www.virustotal.com/gui/file/44572d0deb6fc9c64d0d2b9111c3dfb32be779df5a9e0eddfa77f0503fb3efde?nocache=1) |
| **Universal** | All Android Devices | [Download](https://github.com/Q9550xRX570/MaiWoL/releases/download/v2.2.0/MaiWoL-v2.2.0-universal.apk) | `a70b98aafcf4b6dbf1f8d458dd9a5118e6532940e94fe20752d0757ac1b35022` | [🛡️ 0/68 Clean](https://www.virustotal.com/gui/file/a70b98aafcf4b6dbf1f8d458dd9a5118e6532940e94fe20752d0757ac1b35022?nocache=1) |
| **ARMeabi v7A** | Legacy 32-bit Phones | [Download](https://github.com/Q9550xRX570/MaiWoL/releases/download/v2.2.0/MaiWoL-v2.2.0-armeabi-v7a.apk) | `dc60f5204ae5e70e3b3821b81dab86d8048e7dc90ed56931a41704ecebc3f1f2` | [🛡️ 0/68 Clean](https://www.virustotal.com/gui/file/dc60f5204ae5e70e3b3821b81dab86d8048e7dc90ed56931a41704ecebc3f1f2?nocache=1) |
| **x86_64** | Emulators & Android PCs | [Download](https://github.com/Q9550xRX570/MaiWoL/releases/download/v2.2.0/MaiWoL-v2.2.0-x86_64.apk) | `b02449cb61a129f9c9cc5f1d13ad677a3a43fbe1377eb8fd325fa911b5420e53` | [🛡️ 0/68 Clean](https://www.virustotal.com/gui/file/b02449cb61a129f9c9cc5f1d13ad677a3a43fbe1377eb8fd325fa911b5420e53?nocache=1) |

---

## 🧪 Google Play Closed Beta Program

We are actively maintaining our **Google Play Closed Testing** channel. To install and test **MaiWoL** directly through the Play Store:

1. **Join the Testers Group:** [Google Group for Testers](https://groups.google.com/g/maiwol-testers)
2. **Opt-in via Web:** [Google Play Testing Opt-in](https://play.google.com/apps/testing/com.mai.wol)
3. **Download on Play Store:** [MaiWoL on Google Play](https://play.google.com/store/apps/details?id=com.mai.wol)

*For questions or support, reach us at [contact@maiwol.com](mailto:contact@maiwol.com).*

---

## 📲 Headless External Automation (Tasker / MacroDroid / ADB)

Trigger background wake-ups and power actions without launching the graphical interface:

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
Database: Room DB (v6) with Coroutine Flows
Remote Execution: OpenSSH (JSch) & Native HTTP Engine
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