# ⚡ MaiWoL

A modern, enterprise-grade, lightweight, and 100% Free and Open Source (FOSS) Wake-on-LAN suite for Android.

[![Website](https://img.shields.io/badge/Website-maiwol.com-blue?style=flat&logo=googlechrome&logoColor=white)](https://maiwol.com)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![FOSS](https://img.shields.io/badge/FOSS-100%25-success.svg)](https://f-droid.org)
[![Platform](https://img.shields.io/badge/Platform-Android_7.0+-green.svg)](https://android.com)
[![VirusTotal](https://img.shields.io/badge/VirusTotal-0%2F68_Clean-brightgreen.svg)](https://www.virustotal.com/gui/file/20f74a5779beedd99c6643d89c4f95f7661024cfdfeebad3aa708676932a43c0)
[![Trackers](https://img.shields.io/badge/Trackers-0-brightgreen.svg)]()

---

## 🌟 Architectural & Feature Highlights

### 🚀 High-Performance WoL Dispatch Engine
- **Flexible Network Routing:** Direct UDP Magic Packet transmission via Local Subnet Broadcast (`255.255.255.255` / Subnet Directed) or WAN / DDNS endpoints.
- **Enterprise SecureOn:** Full support for BIOS-level 6-byte password hex injection.
- **Resilient Multi-Burst Transmission:** Configurable packet burst counter (1–20 packets with precision 50ms pacing) to bypass packet loss across complex switches and routers.

### 🟢 Multi-Tier Intelligent Status Polling
- **False-Positive Resistant:** Raw ICMP ping parser filtering out deceptive router echo replies (e.g., host unreachable, 100% packet loss drops).
- **Core OS Service Verification:** Deep TCP polling for OS daemon ports (**SMB 445, RPC 135, RDP 3389, SSH 22, LLMNR**) when ICMP is blocked by firewalls.
- **Tri-State Device Diagnostics:** Immediate identification of **ONLINE** (OS Active), **STANDBY** (Device powered off but network path verified for WoL), or **UNREACHABLE** states.

### 🔍 Rootless ARP Discovery via Shizuku (Binder IPC)
- Overcomes Android 10+ privacy restrictions blocking `/proc/net/arp`.
- Direct IPC interaction via **Shizuku API** executing privileged `ip neigh` and `sysfs` queries to dynamically discover true physical MAC addresses across the LAN without rooting.

### ⏰ Resilient Internal Automation & Scheduling
- **Precision Alarms:** Backed by `AlarmManager.RTC_WAKEUP` with exact alarm scheduling.
- **WakeLock Concurrency:** Automatic `PowerManager.PARTIAL_WAKE_LOCK` acquisition preventing CPU suspension during packet dispatch.
- **Reboot Persistence:** `RECEIVE_BOOT_COMPLETED` receiver reconstructing all Room Database task queues upon device restart.
- **Interactive UI:** Smooth drag-and-drop schedule card reordering with real-time feedback.

### ⚡ Headless External Automation (Tasker / MacroDroid / ADB)
- Dedicated background intent receiver (`com.mai.wol.ACTION_WAKE_DEVICE`) accepting dynamic parameters by device name or MAC address without launching the GUI.

### 🛡️ Hardened App Security & Privacy
- **Cryptographic PIN Storage:** Irreversible **SHA-256** hash protection.
- **Hardware-Backed Biometrics:** Integrated with Android `BiometricPrompt` (Fingerprint & Face Recognition).
- **Window Anti-Leak Protection:** Enforced `FLAG_SECURE` preventing screen captures and obscuring recent app snapshots in the Android task switcher.
- **Card Data Masking:** Dynamic visibility controls to Show, Mask (`*****`), or completely Hide MAC, IP, and Port parameters on screen.

### 🛠️ Built-in Network Diagnostics Suite
- **DNS Resolver:** Native hostname-to-IP and reverse DNS resolution utility.
- **Ping & Latency Tester:** Live round-trip latency (RTT) and packet loss diagnostic terminal.

### 🎨 Pure Jetpack Compose Architecture
- 100% Kotlin & Material 3 implementation with dynamic Material You palette.
- **Ultra-Lightweight Footprint (~5 MB):** Optimized with R8 minification, resource shrinking, ABI splits, and zero third-party telemetry dependencies.

---

## 🧪 Help Test on Google Play (Closed Beta)

We are currently running the **Google Play Closed Testing** phase. If you want to install and test **MaiWoL** directly through the Google Play Store and help us publish it publicly, we would love your support!

👉 **How to join:**
Send a quick email to **[gursoymustafaerdem@gmail.com](mailto:gursoymustafaerdem@gmail.com)** with the subject `MaiWoL Beta Tester` (mentioning your Google Play account email), and we will immediately add you to the Google Play tester list!

---

## 📥 Downloads & VirusTotal Verifications

| Architecture | File | SHA-256 Checksum | VirusTotal |
| :--- | :--- | :--- | :--- |
| **ARM64-v8a** (Most phones) | [Download](https://github.com/Q9550xRX570/MaiWoL/releases/latest) | `20f74a5779beedd99c6643d89c4f95f7661024cfdfeebad3aa708676932a43c0` | [0/68 Clean](https://www.virustotal.com/gui/file/20f74a5779beedd99c6643d89c4f95f7661024cfdfeebad3aa708676932a43c0) |
| **Universal** (All devices) | [Download](https://github.com/Q9550xRX570/MaiWoL/releases/latest) | `043c7d38d64465fab8180a0b951d3bd126db6c5cb535b3f0258efb3633fc4d17` | [0/68 Clean](https://www.virustotal.com/gui/file/043c7d38d64465fab8180a0b951d3bd126db6c5cb535b3f0258efb3633fc4d17) |
| **ARMeabi-v7a** (32-bit) | [Download](https://github.com/Q9550xRX570/MaiWoL/releases/latest) | `2d5352eb9bb86e69a711fedf694a2b17d13b442d22685cbe61d5e1b7f902be89` | [0/68 Clean](https://www.virustotal.com/gui/file/2d5352eb9bb86e69a711fedf694a2b17d13b442d22685cbe61d5e1b7f902be89) |
| **x86_64** (Emulators) | [Download](https://github.com/Q9550xRX570/MaiWoL/releases/latest) | `2afc22712be958bd9a9e668a759dbf90699f409062bd36147b8ba033ea7783e5` | [0/68 Clean](https://www.virustotal.com/gui/file/2afc22712be958bd9a9e668a759dbf90699f409062bd36147b8ba033ea7783e5) |

---

## 📲 External Automation (Tasker / MacroDroid / ADB)

Trigger wake-ups from third-party tools using standard broadcast intents:

- **Action:** `com.mai.wol.ACTION_WAKE_DEVICE`
- **Package:** `com.mai.wol`

### Intent Parameters:
| Extra Key | Type | Description | Example |
| :--- | :--- | :--- | :--- |
| `device_name` | String | Saved device name | `"My PC"` |
| `mac_address` | String | Target MAC address | `"AA:BB:CC:DD:EE:FF"` |
| `ip_address` | String | Optional WAN / DDNS host | `"home.ddns.net"` |
| `local_ip` | String | Optional Local IP | `"192.168.1.100"` |
| `port` | Int / String | Target UDP Port (Default: 9) | `9` |
| `packet_count` | Int / String | Packet burst count (1–20) | `5` |

### ADB Command Example:
```bash
adb shell am broadcast -a com.mai.wol.ACTION_WAKE_DEVICE -p com.mai.wol --es device_name "My PC"
🔒 Privacy & Freedom
0% Telemetry / Analytics: Zero Google Analytics, Firebase, or tracking SDKs.
0% Ads: Completely free of advertisements.
Direct Networking: All socket connections are strictly point-to-point between your device and the configured network targets.
🛠️ Build from Source
code
Bash
git clone https://github.com/Q9550xRX570/MaiWoL.git
cd MaiWoL
./gradlew assembleRelease
The optimized binaries will be generated in app/build/outputs/apk/release/.
📜 License
This project is licensed under the GNU General Public License v3.0 (GPL-3.0). See the LICENSE file for full details.