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
