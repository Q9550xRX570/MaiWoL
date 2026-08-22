# ⚡ MaiWoL

An enterprise-grade, ultra-modern, privacy-hardened, and 100% Free and Open Source (FOSS) Wake-on-LAN power management suite for Android.

[![Version](https://img.shields.io/badge/Version-v2.0.0-blue?style=flat&logo=android&logoColor=white)](https://github.com/Q9550xRX570/MaiWoL/releases)
[![Website](https://img.shields.io/badge/Website-maiwol.com-blue?style=flat&logo=googlechrome&logoColor=white)](https://maiwol.com)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![FOSS](https://img.shields.io/badge/FOSS-100%25-success.svg)](https://f-droid.org)
[![Platform](https://img.shields.io/badge/Platform-Android_7.0+-green.svg)](https://android.com)
[![VirusTotal](https://img.shields.io/badge/VirusTotal-0%2F68_Clean-brightgreen.svg)](https://www.virustotal.com/gui/file/df831f1d144b17f24e5d713a34661213cfe3f9da4eed922824d3b552ca290b8f?nocache=1)
[![Trackers](https://img.shields.io/badge/Trackers-0-brightgreen.svg)]()
[![Ads](https://img.shields.io/badge/Ads-0-brightgreen.svg)]()

---

## 🌟 Why MaiWoL v2.0.0?

Most Wake-on-LAN applications on Android are bloated with ads, closed-source trackers, outdated XML layouts, or basic UDP-only packet dispatchers. 

**MaiWoL v2.0.0** redefines Android network power management: Built from the ground up with **100% Kotlin & Jetpack Compose**, it unites **instant home screen access**, **military-grade AES-256 backup encryption**, **tri-state live status diagnostics**, **rootless Shizuku ARP discovery**, and **bank-grade biometric security** in an ultra-lightweight (~5 MB) native footprint with **zero trackers and zero telemetry**.

---

## 🚀 Key Highlights & Architectural Pillars

### ⚡ 1. Next-Gen Widget & Quick-Access Ecosystem
Never open the app to wake your computers again:
- **📱 1x1 App Icon Widget:** Sits on your home screen identically to a native app icon, displaying your target PC's name and waking it with a single tap.
- **🎴 2x1 Compact Card Widget:** Sleek horizontal card displaying device status, MAC/IP details, and an instant power button.
- **🔥 One UI & Android Dynamic App Shortcuts:** Long-press the MaiWoL icon on your launcher (Samsung One UI, Pixel Launcher, etc.) to view registered devices, wake them instantly, or drag them out to create dedicated 1x1 desktop shortcuts.
- **⚡ Dynamic Quick Settings Tile (Notification Shade):** Pull down your quick settings panel from anywhere in Android to wake your selected PC. Configure target devices directly inside the app; the notification tile dynamically updates its label to match your PC's name!

---

### 💾 2. Native `.maiwol` Backup & Restore Engine (AES-256 GCM)
- **Custom `.maiwol` File Architecture:** Native file-association support. Tapping a `.maiwol` file from WhatsApp, Telegram, Google Drive, or your File Manager automatically launches MaiWoL and prompts for instant one-tap restoration.
- **Optional Military-Grade Encryption:** Protect exported backups using irreversible **AES-256 GCM** encryption derived via PBKDF2 with custom 2, 4, 6, or 8-digit PINs. Your network topologies, IP ranges, MACs, and SecureOn passwords remain fully cryptographically protected.
- **Plain / Encrypted Fallback:** Choose between human-readable open JSON backups or hardened encrypted payloads.

---

### 🛡️ 3. Multi-Tier Security & Privacy Shield
- **Cryptographic PIN & Biometrics:** Irreversible **SHA-256** hash protection supporting 2, 4, 6, or 8-digit PINs alongside hardware-backed `BiometricPrompt` (Fingerprint & Face Recognition).
- **🔒 Dedicated Widget & Tile Lock Gating:** Optionally require PIN or biometric authentication *before* widgets, shortcuts, or notification shade tiles are allowed to transmit Magic Packets. Displays a dedicated contextual unlock prompt: *"Wake up [Device Name]"* / *"[Cihaz Adı] cihazını uyandır"*.
- **🛡️ Anti-Leak Window Shield (`FLAG_SECURE`):** Prevents unauthorized screen recordings, screenshots, and obscures app thumbnails in the Android recent task switcher.
- **🎭 Card Data Masking:** Dynamic visibility controls to Show, Mask (`*****`), or completely Hide sensitive MAC, IP, and Port parameters on screen.

---

### 🚀 4. High-Performance WoL Dispatch Engine
- **Multi-Route Transmission:** Local Subnet Broadcast (`255.255.255.255` / Subnet Directed) or WAN / DDNS routing.
- **Enterprise SecureOn:** Full support for BIOS/NIC-level 6-byte password hex injection.
- **Resilient Multi-Burst Dispatch:** Configurable packet burst counter (1–20 packets with precision 50ms pacing) preventing packet drop across complex routers and managed switches.

---

### 🟢 5. Tri-State Intelligent Status Diagnostics
- **False-Positive Filtering:** Raw ICMP ping parser rejecting deceptive router echo drops (host unreachable / 100% loss).
- **Deep OS Service Polling:** Fallback TCP polling on active OS daemon ports (**SMB 445, RPC 135, RDP 3389, SSH 22, LLMNR 5357**) when ICMP ping is blocked by firewalls.
- **Instant Tri-State Diagnostics:**
  - 🟢 **ONLINE:** OS is active and answering requests.
  - 🟡 **OFFLINE / STANDBY:** Device powered down, but local Wi-Fi / WAN route verified and ready for Magic Packet reception.
  - 🔴 **UNREACHABLE:** Target unreachable or network path disconnected.

---

### 🔍 6. Rootless ARP & MAC Discovery via Shizuku
- Overcomes Android 10+ privacy restrictions blocking `/proc/net/arp`.
- Direct IPC interaction via **Shizuku API** executing privileged `ip neigh` and `sysfs` queries to dynamically discover true physical MAC addresses across the LAN without root permissions.

---

### ⏰ 7. Dual Automation Engine
- **Internal Scheduled Tasks:** Backed by `AlarmManager.RTC_WAKEUP` with exact alarm scheduling, recurring weekly calendars, and automatic `PowerManager.PARTIAL_WAKE_LOCK` acquisition preventing CPU suspension during dispatch.
- **Reboot Resilience:** `BootReceiver` reconstructs all Room Database task queues upon device restart.
- **External Intent Receiver (Tasker / MacroDroid / ADB):** Headless background intent receiver (`com.mai.wol.ACTION_WAKE_DEVICE`) accepting parameters by device name or MAC address without opening the GUI.

---

### 🛠️ 8. Built-in Network Diagnostics Suite
- **DNS Lookup Tool:** Query A/AAAA records and perform reverse DNS lookups in real-time.
- **Ping & Latency Tester:** Live round-trip time (RTT) and packet loss diagnostic terminal with localized metrics.
- **Local Network Auditor:** Direct physical network interface inspector displaying active IP and physical MAC addresses.

---

## 📲 External Automation Guide (Tasker / MacroDroid / ADB)

Trigger headless wake-ups from third-party automation tools using standard broadcast intents:

- **Action:** `com.mai.wol.ACTION_WAKE_DEVICE`
- **Package:** `com.mai.wol`

### Intent Extra Parameters:
| Extra Key | Type | Description | Example |
| :--- | :--- | :--- | :--- |
| `device_name` | String | Saved device name | `"Gaming PC"` |
| `mac_address` | String | Target physical MAC address | `"AA:BB:CC:DD:EE:FF"` |
| `ip_address` | String | Optional WAN / DDNS host | `"home.ddns.net"` |
| `local_ip` | String | Optional Local Subnet IP | `"192.168.1.100"` |
| `port` | Int / String | Target UDP Port (Default: 9) | `9` |
| `packet_count` | Int / String | Packet burst count (1–20) | `5` |

### ADB Command Example:
```bash
adb shell am broadcast -a com.mai.wol.ACTION_WAKE_DEVICE -p com.mai.wol --es device_name "Gaming PC"
🔒 Privacy & Freedom Pledge
0% Telemetry / Analytics: Zero Google Analytics, Firebase, Sentry, or tracking SDKs.
0% Ads: Completely free of advertisements forever.
Point-to-Point Networking: All socket connections are strictly point-to-point between your phone and your configured network targets.
No Cloud Required: All database storage, PIN hashes, and .maiwol backup files remain 100% on your local device.
📥 Downloads & VirusTotal Verifications
Architecture	Target	SHA-256 Checksum	VirusTotal Report
ARM64-v8a	Modern Phones	569732b44ccdf59874cbe7251de7fbfa5937c033ce651ded328ee05a635469b7	0/68 Clean
Universal	All Devices	df831f1d144b17f24e5d713a34661213cfe3f9da4eed922824d3b552ca290b8f	0/68 Clean
ARMeabi-v7a	32-bit Phones	aa35cdb1dc69124df7f4a5c48ed0474904a95ab9b603202baec6afdc54edd658	0/68 Clean
x86_64	Emulators / PCs	b7d84b3426a6d7564952cf3f47d32333e4de52f5c70502965d43cf05baf0251e	0/68 Clean
🧪 Help Test on Google Play (Closed Beta)
We are currently running the Google Play Closed Testing phase. If you want to install and test MaiWoL directly through the Google Play Store and help us publish it publicly, we would love your support!
👉 How to join:
Send a quick email to gursoymustafaerdem@gmail.com with the subject MaiWoL Beta Tester (mentioning your Google Play account email), and we will immediately add you to the Google Play tester list!
🛠️ Build from Source
code
Bash
git clone https://github.com/Q9550xRX570/MaiWoL.git
cd MaiWoL
./gradlew assembleRelease
The optimized binaries will be generated in app/build/outputs/apk/release/.
📜 License
This project is licensed under the GNU General Public License v3.0 (GPL-3.0). See the LICENSE file for full details.