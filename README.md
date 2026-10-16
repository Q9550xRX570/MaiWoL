# MaiWoL

Wake a computer from your phone. The magic packet goes from your phone to your network. There is no account, no ad, and no analytics in the middle.

<p align="center">
  <a href="https://maiwol.com"><img src="https://img.shields.io/badge/Website-maiwol.com-0284C7?style=for-the-badge" alt="Website"></a>
  <a href="https://github.com/Q9550xRX570/MaiWoL/releases/tag/v2.2.5"><img src="https://img.shields.io/badge/Release-v2.2.5-38BDF8?style=for-the-badge" alt="Release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-2563EB?style=for-the-badge" alt="GPL-3.0"></a>
  <a href="https://www.virustotal.com/gui/file/3b6ee5501f9af73f6f1c9982842ff9cfdc672041191df1bd1436e6eda9737a3e"><img src="https://img.shields.io/badge/VirusTotal-0%2F67_Clean-10B981?style=for-the-badge" alt="VirusTotal"></a>
</p>

## What you can do

- Wake one computer, or every computer in a group.
- See whether it answered, and why the card says so.
- Shut it down over SSH or a webhook, only if you set that up.
- Put the wake button on the home screen, in Quick Settings, on a schedule, or behind a Tasker intent.

The app is a normal Android app: Kotlin, Jetpack Compose, Room. Release builds are about 2 MB.

## Wake

A magic packet is six `0xFF` bytes and the MAC address repeated sixteen times. MaiWoL sends it as many times as you choose, to the local network broadcast. If a WAN or DDNS address is saved, it sends that same number there too. SecureOn, the 6-byte NIC password, is included when you set one.

## Is it on?

The card says on, off, or unreachable. Nothing else.

Enter a TCP port and that port is the check. Leave it empty and MaiWoL looks for a normal reply from the computer. One miss does not flip the card. A second miss does. Tapping the badge checks once and shows that result immediately.

After a wake, MaiWoL asks again with the same check. An answer turns the card on. Two misses leave it off or unreachable.

## Shut down, if you want it

Remote shutdown is off until you turn it on.

- SSH runs the command you write. The default is `shutdown /s /f /t 0`. Password or a key file both work. OpenSSH, PEM, and PuTTY `.ppk` are recognized by their contents, whatever the file extension is. You can test the sign-in without powering the machine off.
- HTTP GET and HTTP POST call a URL you provide, for a script or a smart plug.

On Wi-Fi at home the app uses the local address. On mobile data it uses the WAN or DDNS address.

## Close to hand

- Groups you can reorder. Wake the whole group from the top bar.
- A 2×1 card widget and a 1×1 wake icon. Quick Settings tile. Long-press shortcuts on the launcher icon.
- A one-shot date or a weekly alarm. Alarms are restored after the phone reboots.
- A background intent for Tasker, MacroDroid, or `adb`.
- DNS lookup and a ping tool when you want to look at the network yourself.
- Optional Shizuku scan, so a LAN sweep can read MAC addresses without root.

## Private by default

Nothing is sent to MaiWoL. There is no tracker SDK and no ad SDK. Sockets open only to addresses you typed.

A PIN or fingerprint can lock the app and the widgets. The PIN is stored as a SHA-256 hash. Card fields can be shown, masked, or hidden. Android backup and device transfer skip the device database and the PIN. Your own export is separate: plain JSON, or a `.maiwol` file encrypted with AES-256-GCM.

## Downloads

Source version is 2.2.5. The files below are the last published binaries, v2.2.4, with their real checksums. Attach the new APKs to the v2.2.5 release before replacing this table.

| Phone | File | SHA-256 | VirusTotal |
| :--- | :--- | :--- | :--- |
| Most phones (arm64) | [MaiWoL-v2.2.4-arm64-v8a.apk](https://github.com/Q9550xRX570/MaiWoL/releases/download/v2.2.4/MaiWoL-v2.2.4-arm64-v8a.apk) | `5ce9740ae8bb4a15a5c69a0c61a96957c7679980199076d0766cfd6a70612fa7` | [0/68](https://www.virustotal.com/gui/file/5ce9740ae8bb4a15a5c69a0c61a96957c7679980199076d0766cfd6a70612fa7) |
| Any phone (universal) | [MaiWoL-v2.2.4-universal.apk](https://github.com/Q9550xRX570/MaiWoL/releases/download/v2.2.4/MaiWoL-v2.2.4-universal.apk) | `3b6ee5501f9af73f6f1c9982842ff9cfdc672041191df1bd1436e6eda9737a3e` | [0/67](https://www.virustotal.com/gui/file/3b6ee5501f9af73f6f1c9982842ff9cfdc672041191df1bd1436e6eda9737a3e) |
| Older 32-bit phones | [MaiWoL-v2.2.4-armeabi-v7a.apk](https://github.com/Q9550xRX570/MaiWoL/releases/download/v2.2.4/MaiWoL-v2.2.4-armeabi-v7a.apk) | `16a1d6f948c7246ffbbc27963bff16b5c1f9380aed3c224b70c43a4a867e360c` | [0/68](https://www.virustotal.com/gui/file/16a1d6f948c7246ffbbc27963bff16b5c1f9380aed3c224b70c43a4a867e360c) |
| Emulator and Android PC | [MaiWoL-v2.2.4-x86_64.apk](https://github.com/Q9550xRX570/MaiWoL/releases/download/v2.2.4/MaiWoL-v2.2.4-x86_64.apk) | `65ce39e01c46b5257b327bb19b70763deb8b395074f4caeda4b50f4744ec3f4f` | [0/68](https://www.virustotal.com/gui/file/65ce39e01c46b5257b327bb19b70763deb8b395074f4caeda4b50f4744ec3f4f) |

F-Droid metadata is in the repo. The app is not in the F-Droid catalog yet. Play is in closed testing: [join the testers](https://groups.google.com/g/maiwol-testers), [opt in](https://play.google.com/apps/testing/com.mai.wol), then [open the store page](https://play.google.com/store/apps/details?id=com.mai.wol).

Questions: [contact@maiwol.com](mailto:contact@maiwol.com)

## Wake from another app

Action `com.mai.wol.ACTION_WAKE_DEVICE`, package `com.mai.wol`.

| Extra | Type | Meaning | Example |
| :--- | :--- | :--- | :--- |
| `device_name` | String | Saved name | `Workstation` |
| `device_id` | Long, Int, or String | Saved id | `1` |
| `mac_address` | String | MAC, if the device is not saved | `AA:BB:CC:DD:EE:FF` |
| `ip_address` | String | WAN or DDNS | `home.ddns.net` |
| `local_ip` | String | LAN address | `192.168.1.100` |
| `port` | Int or String | UDP port, default 9 | `9` |
| `packet_count` | Int or String | Burst, 1–20 | `5` |
| `secure_on` | String | 6-byte hex SecureOn | `123456789ABC` |

```bash
adb shell am broadcast -a com.mai.wol.ACTION_WAKE_DEVICE -p com.mai.wol --es device_name "Workstation"
```

## Build

```bash
git clone https://github.com/Q9550xRX570/MaiWoL.git
cd MaiWoL
./gradlew assembleRelease
```

On Windows, run `gradlew.bat assembleRelease`. APKs land in `app/build/outputs/apk/release/`.

## License

[GPL-3.0](LICENSE). You can read it, change it, and share it under the same terms.
