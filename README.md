# ⚡ MaiWoL

A modern, privacy-focused, and 100% Free and Open Source (FOSS) Wake-on-LAN Android application.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![FOSS](https://img.shields.io/badge/FOSS-100%25-success.svg)](https://f-droid.org)
[![Platform](https://img.shields.io/badge/Platform-Android_7.0+-green.svg)](https://android.com)
[![VirusTotal](https://img.shields.io/badge/VirusTotal-0%2F68_Clean-brightgreen.svg)](https://www.virustotal.com/gui/file/20f74a5779beedd99c6643d89c4f95f7661024cfdfeebad3aa708676932a43c0)
[![Trackers](https://img.shields.io/badge/Trackers-0-brightgreen.svg)]()

---

## ✨ Features

- 🚀 **Powerful WoL Engine:** Send Magic Packets over local subnet (Broadcast) or Internet (WAN / DDNS) with configurable packet count (1–20).
- 🔒 **SecureOn Support:** Optional 6-byte password protection for enterprise BIOS/NICs.
- ⏰ **Internal Scheduling:**
  - One-time automated wake-up by specific date & time.
  - Weekly recurring schedules (e.g., weekdays, weekends, custom days).
  - Drag-and-drop schedule card reordering.
- ⚡ **External Automation:** Wake devices remotely via Tasker, MacroDroid, or ADB broadcast intents.
- 🟢 **Live Status Polling:** Real-time online/offline monitoring via ICMP ping & OS service ports (SMB, RPC, RDP, SSH). Adjustable polling rate (1s–60s or disabled).
- 🕵️ **Card Privacy Customization:** Option to show, mask (`*****`), or completely hide sensitive MAC, IP, and Port details on device cards.
- 🛡️ **App Lock & Security:** SHA-256 PIN hashing & Biometric unlock (Fingerprint / Face). Blank window preview in task switcher (`FLAG_SECURE`).
- 🔍 **Shizuku / ADB Integration:** Read ARP cache without root to automatically discover MAC addresses on modern Android versions.
- 🛠️ **Network Diagnostics:** Built-in DNS Lookup and Ping & Latency test tools.
- 🎨 **Material You Design:** Clean Material 3 UI supporting Dynamic, Light, and Dark themes.
- 🌐 **Multilingual:** Full Turkish and English localizations.

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

### Extra Parameters:
| Extra Key | Description | Example |
| :--- | :--- | :--- |
| `device_name` | Saved device name | `"My PC"` |
| `mac_address` | Target MAC address | `"AA:BB:CC:DD:EE:FF"` |
| `ip_address` | Optional WAN / DDNS | `"home.ddns.net"` |
| `local_ip` | Optional Local IP | `"192.168.1.100"` |
| `port` | Optional port (Default: 9) | `9` |

### ADB Command Example:
```bash
adb shell am broadcast -a com.mai.wol.ACTION_WAKE_DEVICE -p com.mai.wol --es device_name "My PC"
🔒 Privacy
0% Telemetry / Analytics (No Firebase, no Google Analytics, no SDKs).
0% Ads / Trackers.
The app only establishes network connections directly to the target IP addresses you configure.
🛠️ Build from Source
code
Bash
git clone https://github.com/Q9550xRX570/MaiWoL.git
cd MaiWoL
./gradlew assembleRelease
The APKs will be generated under app/build/outputs/apk/release/.
📜 License
This project is licensed under the GNU General Public License v3.0 (GPL-3.0).