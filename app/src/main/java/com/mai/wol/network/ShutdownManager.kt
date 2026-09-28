package com.mai.wol.network

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.Session
import com.mai.wol.data.DeviceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Properties

data class SshKeyInfo(
    val valid: Boolean,
    val typeLabel: String,
    val encrypted: Boolean
)

object ShutdownManager {

    private const val COMMAND_WAIT_MS = 5000L

    suspend fun executeShutdown(device: DeviceEntity): Result<String> = withContext(Dispatchers.IO) {
        when (device.shutdownType.uppercase()) {
            "SSH" -> executeSshShutdown(device)
            "HTTP_GET" -> executeHttpShutdown(device, "GET")
            "HTTP_POST" -> executeHttpShutdown(device, "POST")
            else -> Result.failure(Exception("Kapatma yöntemi yapılandırılmamış."))
        }
    }

    suspend fun testSshConnection(device: DeviceEntity): Result<String> = withContext(Dispatchers.IO) {
        runSsh(device, command = null)
    }

    fun inspectPrivateKey(keyText: String): SshKeyInfo {
        if (keyText.isBlank()) return SshKeyInfo(false, "", false)
        return try {
            val keyPair = KeyPair.load(JSch(), keyText.toByteArray(Charsets.UTF_8), null)
            val label = when (keyPair.keyType) {
                KeyPair.RSA -> "RSA"
                KeyPair.DSA -> "DSA"
                KeyPair.ECDSA -> "ECDSA"
                KeyPair.ED25519 -> "Ed25519"
                KeyPair.ED448 -> "Ed448"
                else -> keyPair.keyTypeString.ifBlank { "SSH" }
            }
            val encrypted = keyPair.isEncrypted
            keyPair.dispose()
            SshKeyInfo(true, label, encrypted)
        } catch (_: Exception) {
            SshKeyInfo(false, "", false)
        }
    }

    private fun executeSshShutdown(device: DeviceEntity): Result<String> {
        val command = device.shutdownCommand.takeIf { it.isNotBlank() } ?: "shutdown /s /f /t 0"
        return runSsh(device, command)
    }

    private fun runSsh(device: DeviceEntity, command: String?): Result<String> {
        val username = device.shutdownUsername.trim()
        val port = if (device.shutdownPort > 0) device.shutdownPort else 22
        val useKey = device.sshAuthType.equals("KEY", ignoreCase = true)

        if (username.isBlank()) {
            return Result.failure(Exception("SSH Kullanıcı adı boş bırakılamaz."))
        }
        if (useKey) {
            if (device.sshPrivateKey.isBlank()) {
                return Result.failure(Exception("SSH anahtar dosyası yüklenmedi."))
            }
            val keyPair = try {
                KeyPair.load(JSch(), device.sshPrivateKey.toByteArray(Charsets.UTF_8), null)
            } catch (_: Exception) {
                return Result.failure(Exception("SSH anahtarı okunamadı."))
            }
            val unlocked = !keyPair.isEncrypted || keyPair.decrypt(device.sshKeyPassphrase)
            keyPair.dispose()
            if (!unlocked) {
                return Result.failure(Exception("SSH anahtar parolası hatalı veya eksik."))
            }
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
                if (!isTcpPortOpen(host, port, 3000)) {
                    lastErrorMsg = "$host:$port portu kapalı veya SSH servisi çalışmıyor."
                    continue
                }

                session = openSession(device, username, host, port, useKey)
                session.connect(6000)

                if (command == null) {
                    session.disconnect()
                    return Result.success(host)
                }

                val channel = session.openChannel("exec") as ChannelExec
                channel.setCommand(command)
                channel.connect(5000)

                val deadline = System.currentTimeMillis() + COMMAND_WAIT_MS
                while (!channel.isClosed && System.currentTimeMillis() < deadline) {
                    Thread.sleep(100)
                }

                channel.disconnect()
                session.disconnect()

                return Result.success("Komut iletildi ($host): $command")
            } catch (e: Exception) {
                val err = e.localizedMessage ?: e.message ?: ""
                lastErrorMsg = when {
                    err.contains("Auth fail", ignoreCase = true) || err.contains("Auth cancel", ignoreCase = true) ->
                        if (useKey) "SSH anahtarı reddedildi!" else "Kullanıcı adı veya Şifre hatalı!"
                    err.contains("invalid privatekey", ignoreCase = true) ->
                        "SSH anahtarı okunamadı veya parola hatalı."
                    else -> "SSH Hatası ($host): $err"
                }
                try { session?.disconnect() } catch (_: Exception) {}
            }
        }

        return Result.failure(Exception(lastErrorMsg))
    }

    private fun openSession(
        device: DeviceEntity,
        username: String,
        host: String,
        port: Int,
        useKey: Boolean
    ): Session {
        val jsch = JSch()
        if (useKey) {
            val passphrase = device.sshKeyPassphrase.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
            jsch.addIdentity("maiwol", device.sshPrivateKey.toByteArray(Charsets.UTF_8), null, passphrase)
        }

        val session = jsch.getSession(username, host, port)
        if (!useKey) {
            session.setPassword(device.shutdownPassword)
        }

        val config = Properties()
        config["StrictHostKeyChecking"] = "no"
        config["PreferredAuthentications"] = if (useKey) "publickey" else "password,keyboard-interactive"
        session.setConfig(config)
        session.timeout = 6000
        return session
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

        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 6000
            connection.readTimeout = 6000

            if (device.shutdownUsername.isNotBlank() && device.shutdownPassword.isNotBlank()) {
                val userCredentials = "${device.shutdownUsername}:${device.shutdownPassword}"
                val basicAuth = "Basic " + android.util.Base64.encodeToString(userCredentials.toByteArray(), android.util.Base64.NO_WRAP)
                connection.setRequestProperty("Authorization", basicAuth)
            }

            val responseCode = connection.responseCode

            runCatching { connection.inputStream?.close() }
            runCatching { connection.errorStream?.close() }

            if (responseCode in 200..299) {
                Result.success("HTTP İsteği Başarılı (Kod: $responseCode)")
            } else {
                Result.failure(Exception("HTTP Sunucu Hatası: $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("HTTP Hatası: ${e.localizedMessage ?: e.message}"))
        } finally {
            connection?.disconnect()
        }
    }
}
