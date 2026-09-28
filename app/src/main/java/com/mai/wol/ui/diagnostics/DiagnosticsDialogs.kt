package com.mai.wol.ui.diagnostics

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.NetworkInterface

import com.mai.wol.R

@Composable
fun DnsQueryDialog(onDismiss: () -> Unit) {
    var domainInput by remember { mutableStateOf("") }
    var isQuerying by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<String>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun performDnsQuery() {
        val domain = domainInput.trim()
        if (domain.isBlank()) return
        isQuerying = true
        errorMessage = null
        results = null

        scope.launch(Dispatchers.IO) {
            try {
                val addresses = InetAddress.getAllByName(domain)
                val resolvedList = addresses.map { addr ->
                    "${addr.hostAddress} (${addr.hostName})"
                }
                withContext(Dispatchers.Main) {
                    results = resolvedList
                    isQuerying = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = e.localizedMessage ?: "Sunucu bulunamadı"
                    isQuerying = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.dns_query_tool))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = domainInput,
                    onValueChange = { domainInput = it },
                    label = { Text(stringResource(R.string.enter_domain_or_ip)) },
                    placeholder = { Text("cloudflare.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { performDnsQuery() })
                )

                Button(
                    onClick = { performDnsQuery() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = domainInput.isNotBlank() && !isQuerying
                ) {
                    if (isQuerying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.querying))
                    } else {
                        Text(stringResource(R.string.query))
                    }
                }

                if (results != null) {
                    Text(
                        text = stringResource(R.string.query_results),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            results?.forEach { item ->
                                Text(
                                    text = "• $item",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: stringResource(R.string.host_not_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

@Composable
fun PingToolDialog(onDismiss: () -> Unit) {
    var hostInput by remember { mutableStateOf("") }
    var pingCountText by remember { mutableStateOf("4") }
    var isPinging by remember { mutableStateOf(false) }
    var pingOutput by remember { mutableStateOf<List<String>>(emptyList()) }

    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentLocale = context.resources.configuration.locales[0]
    val isTurkish = currentLocale.language == "tr"

    fun translatePingLine(line: String): String {
        if (!isTurkish) return line
        var tLine = line
        if (tLine.contains("ping statistics")) {
            tLine = tLine.replace("ping statistics", "Ping İstatistikleri")
        }
        if (tLine.contains("packets transmitted")) {
            tLine = tLine
                .replace("packets transmitted", "paket gönderildi")
                .replace("received", "alındı")
                .replace("packet loss", "paket kaybı")
                .replace("time", "toplam süre:")
        }
        if (tLine.contains("rtt min/avg/max/mdev") || tLine.contains("round-trip min/avg/max")) {
            tLine = tLine
                .replace("rtt", "Gecikme (RTT)")
                .replace("round-trip", "Gecikme")
                .replace("min/avg/max/mdev", "min/ort/maks/sapma")
                .replace("min/avg/max", "min/ort/maks")
        }
        return tLine
    }

    LaunchedEffect(pingOutput.size) {
        if (pingOutput.isNotEmpty()) {
            lazyListState.animateScrollToItem(pingOutput.size - 1)
        }
    }

    fun performPing() {
        val host = hostInput.trim()
        if (host.isBlank()) return
        val count = pingCountText.toIntOrNull()?.coerceAtLeast(1) ?: 4
        isPinging = true
        pingOutput = emptyList()

        scope.launch(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec("ping -c $count $host")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String? = reader.readLine()
                while (line != null) {
                    if (line.isNotBlank()) {
                        val formattedLine = translatePingLine(line)
                        withContext(Dispatchers.Main) {
                            pingOutput = pingOutput + formattedLine
                        }
                    }
                    line = reader.readLine()
                }
                process.waitFor()
            } catch (e: Exception) {
                val err = "Hata: ${e.localizedMessage}"
                withContext(Dispatchers.Main) {
                    pingOutput = pingOutput + err
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isPinging = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.ping_tool))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = hostInput,
                    onValueChange = { hostInput = it },
                    label = { Text(stringResource(R.string.enter_ping_host)) },
                    placeholder = { Text("1.1.1.1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                OutlinedTextField(
                    value = pingCountText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) {
                            pingCountText = input
                        }
                    },
                    label = { Text(stringResource(R.string.ping_count_label)) },
                    placeholder = { Text("4") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { performPing() })
                )

                Button(
                    onClick = { performPing() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hostInput.isNotBlank() && pingCountText.isNotBlank() && !isPinging
                ) {
                    if (isPinging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.pinging))
                    } else {
                        Text(stringResource(R.string.ping))
                    }
                }

                if (pingOutput.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            itemsIndexed(pingOutput) { _, line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

@Composable
fun NetworkInfoCard(isShizukuEnabled: Boolean) {
    var wifiIp by remember { mutableStateOf("...") }
    var macAddress by remember { mutableStateOf("...") }

    val restrictedText = stringResource(R.string.privacy_restricted)

    LaunchedEffect(isShizukuEnabled) {
        withContext(Dispatchers.IO) {
            try {
                var foundIp: String? = null
                var foundMac: String? = null

                if (isShizukuEnabled) {
                    val shizukuInfo = getNetworkInfoViaShizuku()
                    foundIp = shizukuInfo.first
                    foundMac = shizukuInfo.second
                }

                if (foundIp.isNullOrBlank()) {
                    foundIp = getPhysicalLocalIpAddress() ?: "Bilinmiyor"
                }

                if (foundMac.isNullOrBlank()) {
                    foundMac = getPhysicalMacAddress() ?: restrictedText
                }

                wifiIp = foundIp
                macAddress = foundMac
            } catch (_: Exception) {
                wifiIp = "Bilinmiyor"
                macAddress = restrictedText
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.local_network_info),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.local_ip_label), style = MaterialTheme.typography.bodyMedium)
                Text(wifiIp, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.mac_address_label), style = MaterialTheme.typography.bodyMedium)
                Text(macAddress, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun getNetworkInfoViaShizuku(): Pair<String?, String?> {
    return try {
        if (!Shizuku.pingBinder()) return Pair(null, null)
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return Pair(null, null)

        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true

        var detectedIp: String? = null
        var detectedMac: String? = null

        val ipCmd = arrayOf("ip", "-4", "addr")
        val ipProcess = method.invoke(null, ipCmd, null, null) as? Process
        if (ipProcess != null) {
            val reader = BufferedReader(InputStreamReader(ipProcess.inputStream))
            var line: String?
            var currentIface = ""
            val ifaceIps = mutableMapOf<String, String>()

            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim() ?: continue
                val ifaceMatch = Regex("""^\d+:\s+([a-zA-Z0-9_-]+):""").find(l)
                if (ifaceMatch != null) {
                    currentIface = ifaceMatch.groupValues[1]
                }
                val inetMatch = Regex("""^inet\s+(\d+\.\d+\.\d+\.\d+)""").find(l)
                if (inetMatch != null && currentIface.isNotBlank()) {
                    val ip = inetMatch.groupValues[1]
                    if (ip != "127.0.0.1") {
                        ifaceIps[currentIface] = ip
                    }
                }
            }
            ipProcess.waitFor()

            detectedIp = ifaceIps["wlan0"]
                ?: ifaceIps["wlan1"]
                        ?: ifaceIps["eth0"]
                        ?: ifaceIps.entries.firstOrNull { (name, _) ->
                    !name.startsWith("tun") && !name.startsWith("dummy") && !name.startsWith("lo") && !name.startsWith("p2p")
                }?.value
        }

        val linkCmd = arrayOf("ip", "link")
        val linkProcess = method.invoke(null, linkCmd, null, null) as? Process
        if (linkProcess != null) {
            val reader = BufferedReader(InputStreamReader(linkProcess.inputStream))
            var line: String?
            var currentIface = ""
            val ifaceMacs = mutableMapOf<String, String>()

            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim() ?: continue
                val ifaceMatch = Regex("""^\d+:\s+([a-zA-Z0-9_-]+):""").find(l)
                if (ifaceMatch != null) {
                    currentIface = ifaceMatch.groupValues[1]
                }
                val macMatch = Regex("""link/ether\s+([0-9a-fA-F:]{17})""").find(l)
                if (macMatch != null && currentIface.isNotBlank()) {
                    val mac = macMatch.groupValues[1].uppercase()
                    if (mac != "00:00:00:00:00:00" && mac != "02:00:00:00:00:00") {
                        ifaceMacs[currentIface] = mac
                    }
                }
            }
            linkProcess.waitFor()

            detectedMac = ifaceMacs["wlan0"]
                ?: ifaceMacs["wlan1"]
                        ?: ifaceMacs["eth0"]
                        ?: ifaceMacs.entries.firstOrNull { (name, _) ->
                    !name.startsWith("tun") && !name.startsWith("dummy") && !name.startsWith("lo") && !name.startsWith("p2p")
                }?.value
        }

        if (detectedMac.isNullOrBlank()) {
            val sysfsCmd = arrayOf("sh", "-c", "cat /sys/class/net/wlan0/address 2>/dev/null || cat /sys/class/net/wlan1/address 2>/dev/null || cat /sys/class/net/eth0/address 2>/dev/null")
            val sysfsProcess = method.invoke(null, sysfsCmd, null, null) as? Process
            if (sysfsProcess != null) {
                val reader = BufferedReader(InputStreamReader(sysfsProcess.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val mac = line?.trim()?.uppercase() ?: continue
                    if (mac.matches(Regex("""^([0-9A-FA-F]{2}:){5}[0-9A-FA-F]{2}$""")) &&
                        mac != "00:00:00:00:00:00" && mac != "02:00:00:00:00:00") {
                        detectedMac = mac
                        break
                    }
                }
                sysfsProcess.waitFor()
            }
        }

        Pair(detectedIp, detectedMac)
    } catch (_: Exception) {
        Pair(null, null)
    }
}

private fun getPhysicalLocalIpAddress(): String? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces().toList()
        val sortedInterfaces = interfaces.sortedWith { o1, o2 ->
            val o1Prio = if (o1.name.startsWith("wlan") || o1.name.startsWith("eth")) 0 else if (o1.name.startsWith("tun")) 2 else 1
            val o2Prio = if (o2.name.startsWith("wlan") || o2.name.startsWith("eth")) 0 else if (o2.name.startsWith("tun")) 2 else 1
            o1Prio.compareTo(o2Prio)
        }

        for (element in sortedInterfaces) {
            val name = element.name.lowercase()
            if (name.startsWith("dummy") || name.startsWith("lo") || name.startsWith("p2p")) continue

            val addresses = element.inetAddresses
            while (addresses.hasMoreElements()) {
                val addr = addresses.nextElement()
                if (!addr.isLoopbackAddress && addr is InetAddress && addr.hostAddress?.contains(":") == false) {
                    val ip = addr.hostAddress
                    if (!ip.isNullOrBlank()) {
                        return ip
                    }
                }
            }
        }
    } catch (_: Exception) {}
    return null
}

private fun getPhysicalMacAddress(): String? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces().toList()
        for (element in interfaces) {
            val name = element.name.lowercase()
            if (name.startsWith("wlan") || name.startsWith("eth")) {
                val hardwareAddress = element.hardwareAddress
                if (hardwareAddress != null && hardwareAddress.isNotEmpty()) {
                    val rawMac = hardwareAddress.joinToString(":") { "%02X".format(it) }
                    if (rawMac != "02:00:00:00:00:00" && rawMac != "00:00:00:00:00:00") {
                        return rawMac
                    }
                }
            }
        }
    } catch (_: Exception) {}
    return null
}
