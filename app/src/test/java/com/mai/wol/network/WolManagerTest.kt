package com.mai.wol.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WolManagerTest {

    @Test
    fun magicPacketRepeatsMacSixteenTimesAfterSyncStream() {
        val packet = WolManager.buildMagicPacket("AA:BB:CC:DD:EE:FF", null)
        val mac = byteArrayOf(
            0xAA.toByte(),
            0xBB.toByte(),
            0xCC.toByte(),
            0xDD.toByte(),
            0xEE.toByte(),
            0xFF.toByte()
        )

        assertEquals(102, packet.size)
        assertArrayEquals(ByteArray(6) { 0xFF.toByte() }, packet.copyOfRange(0, 6))
        for (copy in 0 until 16) {
            val start = 6 + (copy * 6)
            assertArrayEquals(mac, packet.copyOfRange(start, start + 6))
        }
    }

    @Test
    fun magicPacketAppendsSecureOnPassword() {
        val packet = WolManager.buildMagicPacket("aabbccddeeff", "11-22-33-44-55-66")
        val secureOn = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66)

        assertEquals(108, packet.size)
        assertArrayEquals(secureOn, packet.copyOfRange(102, 108))
    }

    @Test
    fun invalidMacIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            WolManager.buildMagicPacket("AA:BB", null)
        }
    }

    @Test
    fun packetCountGoesOnceOnLanAndOnceOnWan() {
        val targets = WolManager.resolveWakeTargets(
            ipAddress = "home.example",
            localIp = "192.168.1.20",
            broadcasts = listOf("255.255.255.255", "192.168.1.255", "10.0.0.255")
        )

        assertEquals(listOf("192.168.1.255", "home.example"), targets)
    }

    @Test
    fun wanFieldIsSentEvenWhenBothFieldsMatch() {
        val targets = WolManager.resolveWakeTargets(
            ipAddress = "88.1.2.3",
            localIp = "88.1.2.3",
            broadcasts = listOf("255.255.255.255")
        )

        assertEquals(listOf("255.255.255.255", "88.1.2.3"), targets)
    }

    @Test
    fun lanOnlyWakeDoesNotAddWan() {
        val targets = WolManager.resolveWakeTargets(
            ipAddress = "",
            localIp = "192.168.1.20",
            broadcasts = listOf("255.255.255.255", "192.168.1.255")
        )

        assertEquals(listOf("192.168.1.255"), targets)
    }

    @Test
    fun macOnlyWakeStillHasBroadcastTarget() {
        val targets = WolManager.resolveWakeTargets("", "  ", listOf("255.255.255.255"))

        assertEquals(listOf("255.255.255.255"), targets)
    }
}
