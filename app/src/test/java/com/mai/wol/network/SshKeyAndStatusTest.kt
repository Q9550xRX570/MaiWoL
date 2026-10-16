package com.mai.wol.network

import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking

class SshKeyAndStatusTest {

    private fun privateKeyText(type: Int, passphrase: String? = null): String {
        val keySize = when (type) {
            KeyPair.ECDSA -> 256
            KeyPair.RSA -> 2048
            else -> 0
        }
        val keyPair = if (keySize > 0) KeyPair.genKeyPair(JSch(), type, keySize) else KeyPair.genKeyPair(JSch(), type)
        val out = ByteArrayOutputStream()
        if (passphrase == null) keyPair.writePrivateKey(out) else keyPair.writePrivateKey(out, passphrase.toByteArray())
        keyPair.dispose()
        return out.toString(Charsets.UTF_8.name())
    }

    @Test
    fun detectsRsaKeyFromContent() {
        val info = ShutdownManager.inspectPrivateKey(privateKeyText(KeyPair.RSA))

        assertTrue(info.valid)
        assertEquals("RSA", info.typeLabel)
        assertFalse(info.encrypted)
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("sshkeys/$name")).readText()

    @Test
    fun detectsEcdsaKey() {
        assertEquals("ECDSA", ShutdownManager.inspectPrivateKey(privateKeyText(KeyPair.ECDSA)).typeLabel)
    }

    @Test
    fun detectsOpenSshEd25519FileWithoutExtension() {
        val info = ShutdownManager.inspectPrivateKey(fixture("id_ed25519"))

        assertTrue(info.valid)
        assertEquals("Ed25519", info.typeLabel)
        assertFalse(info.encrypted)
    }

    @Test
    fun detectsPasswordProtectedEd25519File() {
        val info = ShutdownManager.inspectPrivateKey(fixture("id_ed25519_protected"))

        assertTrue(info.valid)
        assertTrue(info.encrypted)
    }

    @Test
    fun reportsPasswordProtectedKey() {
        val info = ShutdownManager.inspectPrivateKey(privateKeyText(KeyPair.RSA, passphrase = "secret"))

        assertTrue(info.valid)
        assertTrue(info.encrypted)
    }

    @Test
    fun acceptsKeyWithWindowsLineEndings() {
        val windowsText = privateKeyText(KeyPair.RSA).replace("\n", "\r\n")

        assertTrue(ShutdownManager.inspectPrivateKey(windowsText).valid)
    }

    @Test
    fun rejectsTextThatIsNotAKey() {
        assertFalse(ShutdownManager.inspectPrivateKey("merhaba dunya").valid)
        assertFalse(ShutdownManager.inspectPrivateKey("").valid)
    }

    @Test
    fun sshBannerLineIsRecognised() {
        assertTrue(DeviceStatusChecker.isSshBanner("SSH-2.0-OpenSSH_9.6"))
        assertFalse(DeviceStatusChecker.isSshBanner("HTTP/1.1 200 OK"))
    }

    @Test
    fun readsBannerOnlyFromRealSshServer() {
        ServerSocket(0).use { server ->
            thread {
                server.accept().use { client ->
                    client.getOutputStream().write("SSH-2.0-OpenSSH_9.6\r\n".toByteArray())
                    client.getOutputStream().flush()
                    Thread.sleep(200)
                }
            }
            assertTrue(DeviceStatusChecker.readSshBanner("127.0.0.1", server.localPort, 2000))
        }
    }

    @Test
    fun oneMissKeepsThePreviousStatus() {
        val online = StatusResult(DeviceStatus.ONLINE, StatusReason.TCP_PORT, "445")
        val offline = StatusResult(DeviceStatus.STANDBY, StatusReason.SAME_SUBNET_SILENT)
        val unreachable = StatusResult(DeviceStatus.UNREACHABLE, StatusReason.NO_ROUTE)

        val firstMiss = StatusResult.settle(online, offline, 0)
        assertEquals(online, firstMiss.result)
        assertEquals(1, firstMiss.pendingMisses)

        val confirmed = StatusResult.settle(firstMiss.result, offline, firstMiss.pendingMisses)
        assertEquals(offline, confirmed.result)
        assertEquals(0, confirmed.pendingMisses)

        val differentFailure = StatusResult.settle(offline, unreachable, 0)
        assertEquals(offline, differentFailure.result)
        assertEquals(unreachable, StatusResult.settle(offline, unreachable, 1).result)
    }

    @Test
    fun onlineResultShowsImmediatelyAndClearsMisses() {
        val udp = StatusResult(DeviceStatus.ONLINE, StatusReason.UDP_RESPONSE, "9")
        val tcp = StatusResult(DeviceStatus.ONLINE, StatusReason.TCP_PORT, "445")
        val offline = StatusResult(DeviceStatus.STANDBY, StatusReason.SAME_SUBNET_SILENT)

        val upgraded = StatusResult.settle(udp, tcp, 1)
        assertEquals(tcp, upgraded.result)
        assertEquals(0, upgraded.pendingMisses)

        val fromOffline = StatusResult.settle(offline, udp, 1)
        assertEquals(udp, fromOffline.result)
        assertEquals(offline, StatusResult.settle(null, offline, 4).result)
        assertEquals(offline, StatusResult.settle(StatusResult.CHECKING, offline, 4).result)
    }

    @Test
    fun tcpPortOpenOnlyWhenSomethingAccepts() = runBlocking {
        ServerSocket(0).use { server ->
            thread { runCatching { server.accept().close() } }
            assertTrue(DeviceStatusChecker.isTcpOpen("127.0.0.1", server.localPort, 1000))
        }
        ServerSocket(0).use { server ->
            val closed = server.localPort
            server.close()
            assertFalse(DeviceStatusChecker.isTcpOpen("127.0.0.1", closed, 500))
        }
    }

    @Test
    fun subnetFollowsThePhonePrefix() {
        assertTrue(sameIpv4Subnet("192.168.1.10", "192.168.1.20", 24))
        assertFalse(sameIpv4Subnet("192.168.1.10", "192.168.2.20", 24))
        assertTrue(sameIpv4Subnet("10.0.0.5", "10.0.1.5", 16))
        assertFalse(sameIpv4Subnet("10.0.0.5", "10.1.0.5", 16))
        assertTrue(sameIpv4Subnet("192.168.1.1", "192.168.1.14", 28))
        assertFalse(sameIpv4Subnet("192.168.1.1", "192.168.1.20", 28))
        assertTrue(sameIpv4Subnet("192.168.1.8", "192.168.1.40", prefixForLanCompare(32)))
    }

    @Test
    fun scanHostListStaysInsideThePrefix() {
        val hosts = ipv4HostsInPrefix("192.168.1.10", 28)
        assertEquals(14, hosts.size)
        assertTrue(hosts.contains("192.168.1.1"))
        assertTrue(hosts.contains("192.168.1.14"))
        assertFalse(hosts.contains("192.168.1.0"))
        assertFalse(hosts.contains("192.168.1.15"))
        assertEquals(254, ipv4HostsInPrefix("10.0.2.15", 24).size)
    }

    @Test
    fun openPortWithoutSshBannerIsNotTreatedAsSsh() {
        ServerSocket(0).use { server ->
            thread {
                server.accept().use { client ->
                    client.getOutputStream().write("HTTP/1.1 200 OK\r\n".toByteArray())
                    client.getOutputStream().flush()
                    Thread.sleep(200)
                }
            }
            assertFalse(DeviceStatusChecker.readSshBanner("127.0.0.1", server.localPort, 2000))
        }
    }
}
