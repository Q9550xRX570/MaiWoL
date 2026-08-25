package com.mai.wol.network

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.mai.wol.data.DeviceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Properties

object ShutdownManager {

    suspend fun executeShutdown(device: DeviceEntity): Result<String> = withContext(Dispatchers.IO) {
        when (device.shutdownType.uppercase()) {
            "SSH" -> executeSshShutdown(device)
            "HTTP_GET" -> executeHttpShutdown(device, "GET")
            "HTTP_POST" -> executeHttpShutdown(device, "POST")
            else -> Result.failure(Exception("Kapatma yöntemi yapılandırılmamış."))
        }
    }

    private fun executeSshShutdown(device: DeviceEntity): Result<String> {
        val username = device.shutdownUsername.trim()
        val password = device.shutdownPassword
        val port = if (device.shutdownPort > 0) device.shutdownPort else 22
        val command = device.shutdownCommand.takeIf { it.isNotBlank() } ?: "shutdown /s /f /t 0"

        if (username.isBlank()) {
            return Result.failure(Exception("SSH Kullanıcı adı boş bırakılamaz."))
        }

        val candidateHosts = listOfNotNull(
            device.localIp.trim().takeIf { it.isNotBlank() },
            device.ipAddress.trim().takeIf { it.isNotBlank() && it != device.localIp.trim() }
        ).distinct()

        if (candidateHosts.isEmpty()) {
            return Result.failure(Exception("Hedef IP veya Host adresi girilmedi."))
        }

        var lastErrorMsg = "Cihaza ulaşılamadı."

        for (host in candidateHosts) {
            var session: Session? = null
            try {
                // Wi-Fi gecikmelerine karşı 3 saniyelik port kontrolü
                if (!isTcpPortOpen(host, port, 3000)) {
                    lastErrorMsg = "$host:$port portu kapalı veya SSH servisi çalışmıyor."
                    continue
                }

                val jsch = JSch()
                session = jsch.getSession(username, host, port)
                session.setPassword(password)

                val config = Properties()
                config["StrictHostKeyChecking"] = "no"
                session.setConfig(config)
                session.timeout = 6000
                session.connect(6000)

                val channel = session.openChannel("exec") as ChannelExec
                channel.setCommand(command)
                channel.connect(5000)

                val reader = BufferedReader(InputStreamReader(channel.inputStream))
                val output = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }

                channel.disconnect()
                session.disconnect()

                return Result.success("Komut iletildi ($host): $command")
            } catch (e: Exception) {
                val err = e.localizedMessage ?: e.message ?: ""
                lastErrorMsg = if (err.contains("Auth fail", ignoreCase = true)) {
                    "Kullanıcı adı veya Şifre hatalı!"
                } else {
                    "SSH Hatası ($host): $err"
                }
                try { session?.disconnect() } catch (_: Exception) {}
            }
        }

        return Result.failure(Exception(lastErrorMsg))
    }

    private fun isTcpPortOpen(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun executeHttpShutdown(device: DeviceEntity, method: String): Result<String> {
        val urlStr = device.shutdownHttpUrl.trim()
        if (urlStr.isBlank()) {
            return Result.failure(Exception("HTTP Webhook URL adresi belirtilmedi."))
        }

        return try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 6000
            connection.readTimeout = 6000

            if (device.shutdownUsername.isNotBlank() && device.shutdownPassword.isNotBlank()) {
                val userCredentials = "${device.shutdownUsername}:${device.shutdownPassword}"
                val basicAuth = "Basic " + android.util.Base64.encodeToString(userCredentials.toByteArray(), android.util.Base64.NO_WRAP)
                connection.setRequestProperty("Authorization", basicAuth)
            }

            val responseCode = connection.responseCode
            connection.disconnect()

            if (responseCode in 200..299) {
                Result.success("HTTP İsteği Başarılı (Kod: $responseCode)")
            } else {
                Result.failure(Exception("HTTP Sunucu Hatası: $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("HTTP Hatası: ${e.localizedMessage ?: e.message}"))
        }
    }
}