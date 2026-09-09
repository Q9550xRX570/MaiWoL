package com.mai.wol.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WolManager {

    private val HEX_CLEAN_REGEX = Regex("[^a-fA-F0-9]")

    suspend fun sendMagicPacket(
        macAddress: String,
        ipAddress: String = "",
        localIp: String = "",
        port: Int = 9,
        secureOnPassword: String? = null,
        packetCount: Int = 1
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanMac = macAddress.replace(HEX_CLEAN_REGEX, "")
        if (cleanMac.length != 12) {
            return@withContext Result.failure(IllegalArgumentException("Geçersiz MAC adresi. 12 haneli hex olmalıdır."))
        }

        val macBytes = ByteArray(6)
        for (i in 0 until 6) {
            val idx = i * 2
            macBytes[i] = cleanMac.substring(idx, idx + 2).toInt(16).toByte()
        }

        val secureOnBytes = secureOnPassword?.takeIf { it.isNotBlank() }?.let { pwd ->
            val cleanPwd = pwd.replace(HEX_CLEAN_REGEX, "")
            if (cleanPwd.length == 12) {
                ByteArray(6).apply {
                    for (i in 0 until 6) {
                        val idx = i * 2
                        this[i] = cleanPwd.substring(idx, idx + 2).toInt(16).toByte()
                    }
                }
            } else null
        }

        val packetSize = 6 + (16 * 6) + (secureOnBytes?.size ?: 0)
        val bytes = ByteArray(packetSize)

        for (i in 0..5) {
            bytes[i] = 0xFF.toByte()
        }

        for (i in 6 until 102 step 6) {
            System.arraycopy(macBytes, 0, bytes, i, 6)
        }

        secureOnBytes?.let {
            System.arraycopy(it, 0, bytes, 102, 6)
        }

        val targets = listOfNotNull(
            ipAddress.trim().takeIf { it.isNotBlank() },
            localIp.trim().takeIf { it.isNotBlank() && it != ipAddress.trim() }
        ).distinct()

        if (targets.isEmpty()) {
            return@withContext Result.failure(Exception("Gönderilecek geçerli hedef IP/Adres girilmedi."))
        }

        val countToSet = packetCount.coerceIn(1, 20)

        val results = coroutineScope {
            targets.map { targetHost ->
                async {
                    sendToHost(targetHost, bytes, port, countToSet)
                }
            }
        }.map { it.await() }

        val successfulHosts = results.filter { it.isSuccess }
        if (successfulHosts.isNotEmpty()) {
            Result.success(Unit)
        } else {
            val errorMsg = results.mapNotNull { it.exceptionOrNull()?.message }.joinToString("; ")
            Result.failure(Exception("Paket gönderilemedi: $errorMsg"))
        }
    }

    private suspend fun sendToHost(
        host: String,
        payload: ByteArray,
        port: Int,
        count: Int
    ): Result<Int> {
        return try {
            val address = InetAddress.getByName(host)
            val packet = DatagramPacket(payload, payload.size, address, port)

            DatagramSocket().use { socket ->
                runCatching { socket.broadcast = true }
                var sent = 0
                repeat(count) { i ->
                    try {
                        socket.send(packet)
                        sent++
                    } catch (_: Exception) {}

                    if (count > 1 && i < count - 1) {
                        delay(50)
                    }
                }
                if (sent > 0) Result.success(sent)
                else Result.failure(Exception("$host adresine paket iletilemedi"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("$host (${e.localizedMessage ?: e.message})"))
        }
    }
}