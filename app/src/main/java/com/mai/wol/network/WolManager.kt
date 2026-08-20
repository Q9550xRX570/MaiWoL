package com.mai.wol.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WolManager {

    suspend fun sendMagicPacket(
        macAddress: String,
        ipAddress: String = "",
        localIp: String = "",
        port: Int = 9,
        secureOnPassword: String? = null,
        packetCount: Int = 1
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanMac = macAddress.replace(Regex("[^a-fA-F0-9]"), "")
        if (cleanMac.length != 12) {
            return@withContext Result.failure(IllegalArgumentException("Geçersiz MAC adresi. 12 haneli hex olmalıdır."))
        }

        val macBytes = cleanMac.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        val secureOnBytes = secureOnPassword?.takeIf { it.isNotBlank() }?.let { pwd ->
            val cleanPwd = pwd.replace(Regex("[^a-fA-F0-9]"), "")
            if (cleanPwd.length == 12) {
                cleanPwd.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
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

        var sentCount = 0
        val errors = mutableListOf<String>()
        val countToSet = packetCount.coerceIn(1, 20)

        for (targetHost in targets) {
            try {
                val address = InetAddress.getByName(targetHost)
                val isBroadcastTarget = targetHost.endsWith(".255") || targetHost == "255.255.255.255"

                var targetSent = 0
                repeat(countToSet) {
                    var success = sendPacket(bytes, address, port, enableBroadcast = isBroadcastTarget)
                    if (!success) {
                        success = sendPacket(bytes, address, port, enableBroadcast = !isBroadcastTarget)
                    }
                    if (success) targetSent++
                    if (countToSet > 1) {
                        Thread.sleep(50)
                    }
                }

                if (targetSent > 0) {
                    sentCount += targetSent
                } else {
                    errors.add(targetHost)
                }
            } catch (e: Exception) {
                errors.add("$targetHost (${e.localizedMessage ?: e.message})")
            }
        }

        if (sentCount > 0) {
            Result.success(Unit)
        } else {
            val errorMsg = "Paket gönderilemedi: ${errors.joinToString("; ")}"
            Result.failure(Exception(errorMsg))
        }
    }

    private fun sendPacket(
        bytes: ByteArray,
        address: InetAddress,
        port: Int,
        enableBroadcast: Boolean
    ): Boolean {
        return try {
            DatagramSocket().use { socket ->
                if (enableBroadcast) {
                    runCatching { socket.broadcast = true }
                }
                val packet = DatagramPacket(bytes, bytes.size, address, port)
                socket.send(packet)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}