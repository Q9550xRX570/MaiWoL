package com.mai.wol.network

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import com.mai.wol.data.DeviceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.cancellation.CancellationException

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

enum class StatusReason {
    NOT_CHECKED,
    SSH_BANNER,
    TCP_PORT,
    SERVICE_PORT,
    PING_REPLY,
    WAN_PORT,
    UDP_RESPONSE,
    SAME_SUBNET_SILENT,
    NO_ROUTE
}

data class StatusResult(
    val status: DeviceStatus,
    val reason: StatusReason = StatusReason.NOT_CHECKED,
    val detail: String = ""
) {
    companion object {
        val CHECKING = StatusResult(DeviceStatus.CHECKING)
        const val MISSES_BEFORE_STATUS_CHANGE = 2

        /**
         * One missed reply must not flip the badge. A real change sticks after
         * two matching polls. An online reply is shown on the first success.
         */
        fun settle(previous: StatusResult?, next: StatusResult, pendingMisses: Int): SettledStatus {
            if (previous == null || previous.status == DeviceStatus.CHECKING) {
                return SettledStatus(next, 0)
            }
            if (next.status == DeviceStatus.ONLINE) {
                return SettledStatus(next, 0)
            }
            if (previous.status == next.status && previous.reason == next.reason && previous.detail == next.detail) {
                return SettledStatus(next, 0)
            }
            val misses = pendingMisses + 1
            if (misses < MISSES_BEFORE_STATUS_CHANGE) {
                return SettledStatus(previous, misses)
            }
            return SettledStatus(next, 0)
        }
    }
}

data class SettledStatus(
    val result: StatusResult,
    val pendingMisses: Int
)

data class LocalIpv4(
    val address: String,
    val prefixLength: Int
)

internal fun ipv4ToInt(ip: String): Int? {
    val parts = ip.trim().split('.')
    if (parts.size != 4) return null
    var value = 0
    for (part in parts) {
        val octet = part.toIntOrNull() ?: return null
        if (octet !in 0..255) return null
        value = (value shl 8) or octet
    }
    return value
}

internal fun prefixForLanCompare(prefixLength: Int): Int {
    return if (prefixLength in 8..30) prefixLength else 24
}

internal fun sameIpv4Subnet(localIp: String, otherIp: String, prefixLength: Int): Boolean {
    if (prefixLength !in 0..32) return false
    val local = ipv4ToInt(localIp) ?: return false
    val other = ipv4ToInt(otherIp) ?: return false
    if (prefixLength == 0) return true
    val mask = -1 shl (32 - prefixLength)
    return (local and mask) == (other and mask)
}

internal fun ipv4HostsInPrefix(address: String, prefixLength: Int): List<String> {
    if (prefixLength !in 24..32) return emptyList()
    val base = ipv4ToInt(address) ?: return emptyList()
    val hostBits = 32 - prefixLength
    val mask = -1 shl hostBits
    val network = base and mask
    val count = 1 shl hostBits
    if (count <= 2) return emptyList()
    return (1 until count - 1).map { host ->
        val value = network or host
        listOf(
            (value ushr 24) and 0xFF,
            (value ushr 16) and 0xFF,
            (value ushr 8) and 0xFF,
            value and 0xFF
        ).joinToString(".")
    }
}

object NetworkScanner {

    suspend fun scanLocalSubnet(context: Context, useShizuku: Boolean = false): List<ScannedDevice> = withContext(Dispatchers.IO) {
        val local = getLocalIpv4(context) ?: return@withContext emptyList()
        val hosts = ipv4HostsInPrefix(local.address, local.prefixLength.coerceAtLeast(24))

        val commonPorts = listOf(80, 445, 139, 22, 8080, 9)
        val concurrencyLimit = Semaphore(30)

        val jobs = hosts.map { targetIp ->
            async {
                concurrencyLimit.withPermit {
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

            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
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
        for (port in ports) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 70)
                    return true
                }
            } catch (_: Exception) {}
        }

        return try {
            InetAddress.getByName(ip).isReachable(100)
        } catch (_: Exception) {
            false
        }
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

    fun getLocalIpAddress(context: Context): String? = getLocalIpv4(context)?.address

    fun getLocalIpv4(context: Context): LocalIpv4? {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork ?: return null
            val linkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return null

            for (linkAddress in linkProperties.linkAddresses) {
                val address = linkAddress.address
                if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                    val host = address.hostAddress ?: continue
                    return LocalIpv4(host, linkAddress.prefixLength.coerceIn(0, 32))
                }
            }
        } catch (_: Exception) {}
        return null
    }
}

object DeviceStatusChecker {

    suspend fun checkStatus(context: Context, device: DeviceEntity): StatusResult = withContext(Dispatchers.IO) {
        val rawLocalIp = device.localIp.trim()
        val rawWanAddress = device.ipAddress.trim()

        val effectiveLocalIp = when {
            rawLocalIp.isNotBlank() && !isBroadcastAddress(rawLocalIp) -> rawLocalIp
            rawWanAddress.isNotBlank() && isPrivateIp(rawWanAddress) && !isBroadcastAddress(rawWanAddress) -> rawWanAddress
            else -> ""
        }

        val effectiveWanAddress = when {
            rawWanAddress.isNotBlank() && !isPrivateIp(rawWanAddress) && !isBroadcastAddress(rawWanAddress) -> rawWanAddress
            else -> ""
        }

        val localNet = NetworkScanner.getLocalIpv4(context)
        val isPhoneOnSameSubnet = localNet != null &&
            isPrivateIp(localNet.address) &&
            effectiveLocalIp.isNotBlank() &&
            sameIpv4Subnet(localNet.address, effectiveLocalIp, prefixForLanCompare(localNet.prefixLength))

        val useTcp = device.tcpPort in 1..65535
        val hosts = buildList {
            if (isPhoneOnSameSubnet) {
                if (effectiveLocalIp.isNotBlank()) add(effectiveLocalIp)
                if (effectiveWanAddress.isNotBlank()) add(effectiveWanAddress)
            } else {
                if (effectiveWanAddress.isNotBlank()) add(effectiveWanAddress)
                if (effectiveLocalIp.isNotBlank()) add(effectiveLocalIp)
            }
        }.distinct()

        for (host in hosts) {
            ensureActive()
            val answered = if (useTcp) {
                isTcpOpen(host, device.tcpPort, 800)
            } else {
                anyOsPortOpen(host)
            }
            if (answered) {
                return@withContext StatusResult(DeviceStatus.ONLINE)
            }
        }

        if (isPhoneOnSameSubnet) {
            return@withContext StatusResult(DeviceStatus.STANDBY)
        }

        return@withContext StatusResult(DeviceStatus.UNREACHABLE)
    }

    internal fun readSshBanner(host: String, port: Int, timeoutMs: Int): Boolean {
        if (host.isBlank() || isBroadcastAddress(host)) return false
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                socket.soTimeout = timeoutMs
                val input = socket.getInputStream()
                val buffer = StringBuilder()
                while (buffer.length < 255) {
                    val b = input.read()
                    if (b == -1 || b == '\n'.code) break
                    buffer.append(b.toChar())
                }
                isSshBanner(buffer.toString())
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            false
        }
    }

    internal fun isSshBanner(line: String): Boolean = line.trimStart().startsWith("SSH-")

    private suspend fun anyOsPortOpen(host: String): Boolean = withContext(Dispatchers.IO) {
        val ports = listOf(445, 22, 3389, 135, 80)
        val jobs = ports.map { port ->
            async { isTcpOpen(host, port, 500) }
        }
        jobs.awaitAll().any { it }
    }

    internal suspend fun isTcpOpen(host: String, port: Int, timeoutMs: Int): Boolean {
        if (host.isBlank() || isBroadcastAddress(host) || port !in 1..65535) return false
        return withContext(Dispatchers.IO) {
            val socket = Socket()
            val closeOnCancel = coroutineContext.job.invokeOnCompletion { error ->
                if (error != null) runCatching { socket.close() }
            }
            try {
                socket.connect(InetSocketAddress(host, port), timeoutMs.coerceAtLeast(1))
                true
            } catch (e: Exception) {
                ensureActive()
                if (e is CancellationException) throw e
                false
            } finally {
                closeOnCancel.dispose()
                runCatching { socket.close() }
            }
        }
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