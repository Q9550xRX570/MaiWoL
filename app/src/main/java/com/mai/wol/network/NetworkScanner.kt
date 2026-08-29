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
    ONLINE,
    STANDBY,
    UNREACHABLE
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
        } catch (_: Exception) {}
        return arpMap
    }

    private fun isHostReachable(ip: String, ports: List<Int>): Boolean {
        try {
            val process = Runtime.getRuntime().exec("ping -c 1 -W 1 -w 1 $ip")
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
        val rawLocalIp = device.localIp.trim()
        val rawWanAddress = device.ipAddress.trim()
        val targetPort = device.port
        val shutdownPort = device.shutdownPort
        val shutdownType = device.shutdownType

        val effectiveLocalIp = when {
            rawLocalIp.isNotBlank() && !isBroadcastAddress(rawLocalIp) -> rawLocalIp
            rawWanAddress.isNotBlank() && isPrivateIp(rawWanAddress) && !isBroadcastAddress(rawWanAddress) -> rawWanAddress
            else -> ""
        }

        val effectiveWanAddress = when {
            rawWanAddress.isNotBlank() && !isPrivateIp(rawWanAddress) && !isBroadcastAddress(rawWanAddress) -> rawWanAddress
            else -> ""
        }

        val phoneLocalIp = NetworkScanner.getLocalIpAddress(context)
        val isPhoneOnLocalWifi = !phoneLocalIp.isNullOrBlank() && isPrivateIp(phoneLocalIp)

        val isPhoneOnSameSubnet = if (isPhoneOnLocalWifi && effectiveLocalIp.isNotBlank()) {
            val phoneSubnet = phoneLocalIp.substringBeforeLast(".")
            val deviceSubnet = effectiveLocalIp.substringBeforeLast(".")
            phoneSubnet == deviceSubnet
        } else {
            false
        }

        if (effectiveLocalIp.isNotBlank()) {
            val localOsServicePorts = mutableListOf(445, 135, 139, 3389, 5357)
            if (shutdownType.equals("SSH", ignoreCase = true) && shutdownPort in 1..65535) {
                localOsServicePorts.add(0, shutdownPort)
            } else if (shutdownPort !in listOf(7, 9, 80, 443) && shutdownPort in 1..65535) {
                localOsServicePorts.add(0, shutdownPort)
            }
            if (targetPort !in listOf(7, 9, 80, 443) && targetPort in 1..65535 && !localOsServicePorts.contains(targetPort)) {
                localOsServicePorts.add(0, targetPort)
            }

            if (pingHostAccurate(effectiveLocalIp) || isAnyPortOpen(effectiveLocalIp, localOsServicePorts, 250)) {
                return@withContext DeviceStatus.ONLINE
            }
        }

        if (!isPhoneOnSameSubnet && effectiveWanAddress.isNotBlank()) {
            val specificWanPorts = mutableListOf<Int>()
            if (shutdownType.equals("SSH", ignoreCase = true) && shutdownPort in 1..65535) {
                specificWanPorts.add(shutdownPort)
            }
            if (targetPort !in listOf(7, 9, 80, 443) && targetPort in 1..65535 && !specificWanPorts.contains(targetPort)) {
                specificWanPorts.add(targetPort)
            }

            if (specificWanPorts.isNotEmpty()) {
                if (isAnyPortOpen(effectiveWanAddress, specificWanPorts, 350)) {
                    return@withContext DeviceStatus.ONLINE
                }
            }
        }

        if (isPhoneOnSameSubnet) {
            return@withContext DeviceStatus.STANDBY
        }

        if (effectiveWanAddress.isNotBlank()) {
            try {
                val address = InetAddress.getByName(effectiveWanAddress)
                if (address != null && !address.isLoopbackAddress && !address.isAnyLocalAddress) {
                    return@withContext DeviceStatus.STANDBY
                }
            } catch (_: Exception) {}
        }

        return@withContext DeviceStatus.UNREACHABLE
    }

    private fun pingHostAccurate(host: String): Boolean {
        if (host.isBlank() || isBroadcastAddress(host)) return false
        return try {
            val process = Runtime.getRuntime().exec("ping -c 1 -W 1 -w 1 $host")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var hasTargetReply = false
            var hasPacketLoss = false
            val cleanHost = host.lowercase()

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line?.lowercase() ?: continue
                if (l.contains("unreachable") || l.contains("100% packet loss") || l.contains("100% loss") ||
                    l.contains("0 packets received") || l.contains("0 received") || l.contains("time to live exceeded") ||
                    l.contains("host down") || l.contains("network is unreachable")) {
                    hasPacketLoss = true
                    break
                }
                if (l.contains("bytes from") && l.contains(cleanHost) && l.contains("ttl=")) {
                    hasTargetReply = true
                }
            }
            val exitCode = process.waitFor()
            exitCode == 0 && hasTargetReply && !hasPacketLoss
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun isAnyPortOpen(host: String, ports: List<Int>, timeoutMs: Int = 250): Boolean = withContext(Dispatchers.IO) {
        if (host.isBlank() || isBroadcastAddress(host) || ports.isEmpty()) return@withContext false
        val validPorts = ports.filter { it in 1..65535 }.distinct()
        if (validPorts.isEmpty()) return@withContext false

        val jobs = validPorts.map { port ->
            async {
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(host, port), timeoutMs)
                        true
                    }
                } catch (_: Exception) {
                    false
                }
            }
        }
        jobs.awaitAll().any { it }
    }

    private fun isPrivateIp(ip: String): Boolean {
        val clean = ip.trim()
        if (clean.startsWith("192.168.") || clean.startsWith("10.") || clean.startsWith("127.")) return true
        if (clean.startsWith("172.")) {
            val parts = clean.split(".")
            if (parts.size >= 2) {
                val secondOctet = parts[1].toIntOrNull() ?: 0
                if (secondOctet in 16..31) return true
            }
        }
        return false
    }

    private fun isBroadcastAddress(ip: String): Boolean {
        val clean = ip.trim()
        return clean.endsWith(".255") || clean == "255.255.255.255"
    }
}