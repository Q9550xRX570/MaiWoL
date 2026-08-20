package com.mai.wol.network

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import com.mai.wol.data.DeviceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class ScannedDevice(
    val name: String,
    val ip: String,
    val mac: String = ""
)

enum class DeviceStatus {
    CHECKING,
    ONLINE,      // Bilgisayar açık (Ping veya TCP servis portu yanıt veriyor)
    STANDBY,     // Bilgisayar kapalı AMA ağ yolu açık, Magic Packet kesinlikle ulaşabilir
    UNREACHABLE  // Bağlantı yok (farklı ağda / DNS çözülemiyor / modem kapalı)
}

object NetworkScanner {

    suspend fun scanLocalSubnet(context: Context, useShizuku: Boolean = false): List<ScannedDevice> = withContext(Dispatchers.IO) {
        val localIp = getLocalIpAddress(context) ?: return@withContext emptyList()
        val subnetBase = localIp.substringBeforeLast(".") + "."

        val commonPorts = listOf(80, 445, 139, 22, 8080, 9)

        val jobs = (1..254).map { hostSuffix ->
            async {
                val targetIp = "$subnetBase$hostSuffix"
                if (isHostReachable(targetIp, commonPorts)) {
                    val hostName = resolveHostName(targetIp)
                    val displayName = if (hostName != targetIp && hostName.isNotBlank()) {
                        hostName
                    } else {
                        "Cihaz ($targetIp)"
                    }
                    ScannedDevice(name = displayName, ip = targetIp)
                } else {
                    null
                }
            }
        }

        val foundDevices = jobs.awaitAll().filterNotNull()

        if (useShizuku) {
            val arpMap = getArpTableWithShizuku()
            return@withContext foundDevices.map { dev ->
                val mac = arpMap[dev.ip] ?: ""
                dev.copy(mac = mac)
            }
        }

        return@withContext foundDevices
    }

    private fun getArpTableWithShizuku(): Map<String, String> {
        val arpMap = mutableMapOf<String, String>()
        try {
            if (!Shizuku.pingBinder()) return emptyMap()
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return emptyMap()

            val cmd = arrayOf("ip", "neigh")

            val process = try {
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                method.invoke(null, cmd, null, null) as? Process
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } ?: return emptyMap()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            val regex = Regex("""^(\d+\.\d+\.\d+\.\d+)\s+.*lladdr\s+([0-9a-fA-F:]+)""")
            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    val match = regex.find(it)
                    if (match != null) {
                        val ip = match.groupValues[1]
                        val mac = match.groupValues[2].uppercase()
                        arpMap[ip] = mac
                    }
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return arpMap
    }

    private fun isHostReachable(ip: String, ports: List<Int>): Boolean {
        try {
            val process = Runtime.getRuntime().exec("ping -c 1 -w 1 $ip")
            val exitCode = process.waitFor()
            if (exitCode == 0) return true
        } catch (_: Exception) {}

        try {
            if (InetAddress.getByName(ip).isReachable(200)) return true
        } catch (_: Exception) {}

        for (port in ports) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 80)
                    return true
                }
            } catch (_: Exception) {}
        }

        return false
    }

    private fun resolveHostName(ip: String): String {
        return try {
            val inetAddress = InetAddress.getByName(ip)
            val canonical = inetAddress.canonicalHostName
            if (canonical != ip) canonical else inetAddress.hostName
        } catch (_: Exception) {
            ip
        }
    }

    fun getLocalIpAddress(context: Context): String? {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork ?: return null
            val linkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return null

            for (linkAddress in linkProperties.linkAddresses) {
                val address = linkAddress.address
                if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                    return address.hostAddress
                }
            }
        } catch (_: Exception) {}
        return null
    }
}

object DeviceStatusChecker {

    suspend fun checkStatus(context: Context, device: DeviceEntity): DeviceStatus = withContext(Dispatchers.IO) {
        val localIp = device.localIp.trim()
        val wanAddress = device.ipAddress.trim()
        val targetPort = device.port

        // İşletim sisteminin açık olduğunu gösteren standart TCP servis portları
        // 80 (modem/router web arayüzü), 9 ve 7 (WoL UDP) gibi portlar elenir
        val osServicePorts = mutableListOf(445, 135, 139, 5357, 3389, 22)
        if (targetPort !in listOf(7, 9, 80, 443) && targetPort in 1..65535) {
            osServicePorts.add(0, targetPort)
        }

        // 1. AÇIK MI? (Cihazın yerel IP'sine ICMP ping veya OS servis port kontrolü)
        if (localIp.isNotBlank()) {
            if (pingHostAccurate(localIp) || isAnyPortOpen(localIp, osServicePorts)) {
                return@withContext DeviceStatus.ONLINE
            }
        }

        // Kullanıcı yerel ağda değilse ve WAN üzerinden cihaz için özel yönlendirilmiş bir port varsa (RDP/SSH vs.)
        if (wanAddress.isNotBlank() && targetPort !in listOf(7, 9, 80, 443) && targetPort in 1..65535) {
            if (isAnyPortOpen(wanAddress, listOf(targetPort, 3389, 22))) {
                return@withContext DeviceStatus.ONLINE
            }
        }

        // 2. KAPALI MI? (İşletim sistemi kapalı AMA Magic Packet alabilmesi için ağ yolu hazır)
        val phoneLocalIp = NetworkScanner.getLocalIpAddress(context)
        if (!phoneLocalIp.isNullOrBlank() && localIp.isNotBlank()) {
            val phoneSubnet = phoneLocalIp.substringBeforeLast(".")
            val deviceSubnet = localIp.substringBeforeLast(".")
            if (phoneSubnet == deviceSubnet) {
                // Telefon ve bilgisayar aynı yerel Wi-Fi alt ağında -> Cihaz kapalı ama WoL hazır
                return@withContext DeviceStatus.STANDBY
            }
        }

        if (wanAddress.isNotBlank()) {
            try {
                val address = InetAddress.getByName(wanAddress)
                if (address != null && !address.isLoopbackAddress) {
                    // WAN/DDNS adresi çözümlenebiliyor -> İnternet üzerinden WoL hazır
                    return@withContext DeviceStatus.STANDBY
                }
            } catch (_: Exception) {}
        }

        // 3. ULAŞILAMADI (Farklı ağda / DNS çözülemiyor / bağlantı yok)
        return@withContext DeviceStatus.UNREACHABLE
    }

    private fun pingHostAccurate(host: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("ping -c 1 -w 1 $host")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var hasValidReply = false
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val lower = line?.lowercase() ?: ""
                // Router'ın "Destination Host Unreachable" veya %100 paket kaybı sahte yanıtlarını ele
                if (lower.contains("unreachable") || lower.contains("100% packet loss") || lower.contains("100% loss")) {
                    hasValidReply = false
                    break
                }
                if (lower.contains("ttl=") || lower.contains("1 received") || lower.contains("1 packets received") || lower.contains("bytes from $host")) {
                    hasValidReply = true
                }
            }
            val exitCode = process.waitFor()
            exitCode == 0 && hasValidReply
        } catch (_: Exception) {
            false
        }
    }

    private fun isAnyPortOpen(host: String, ports: List<Int>): Boolean {
        for (port in ports) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 200)
                    return true
                }
            } catch (_: Exception) {}
        }
        return false
    }
}