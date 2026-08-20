# ⚡ MaiWoL

A modern, privacy-focused, and 100% Free and Open Source (FOSS) Wake-on-LAN Android application.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![FOSS](https://img.shields.io/badge/FOSS-100%25-success.svg)](https://f-droid.org)
[![Platform](https://img.shields.io/badge/Platform-Android_7.0+-green.svg)](https://android.com)
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
adb shell am broadcast -a com.mai.wol.ACTION_WAKE_DEVICE -p com.mai.wol --es device_name "My PC"
🔒 Privacy
0% Telemetry / Analytics (No Firebase, no Google Analytics, no SDKs).
0% Ads / Trackers.
The app only establishes network connections directly to the target IP addresses you configure.
🛠️ Build from Source
code
Bash
git clone https://github.com/YOUR_USERNAME/MaiWOL.git
cd MaiWOL
./gradlew assembleRelease
The APK will be generated under app/build/outputs/apk/release/.
📜 License
This project is licensed under the GNU General Public License v3.0 (GPL-3.0).