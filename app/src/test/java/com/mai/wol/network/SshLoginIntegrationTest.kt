package com.mai.wol.network

import com.jcraft.jsch.JSch
import com.mai.wol.data.DeviceEntity
import kotlinx.coroutines.runBlocking
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.security.PublicKey
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class SshLoginIntegrationTest {

    companion object {
        private const val USER = "erdem"
        private const val PASSWORD = "doğru-şifre"
        private const val HANGING_COMMAND = "shutdown /s /f /t 0"
        private val executedCommands = CopyOnWriteArrayList<String>()
        private lateinit var server: SshServer
        private val hostKeyFile = Files.createTempFile("maiwol-host", ".ser").also { Files.delete(it) }
        private val savedJschConfig = mutableMapOf<String, String>()

        private fun resource(name: String): String =
            requireNotNull(SshLoginIntegrationTest::class.java.classLoader?.getResource("sshkeys/$name")).readText()

        private fun publicKey(name: String): PublicKey =
            AuthorizedKeyEntry.parseAuthorizedKeyEntry(resource(name).trim())
                .resolvePublicKey(null, PublicKeyEntryResolver.FAILING)

        @BeforeClass
        @JvmStatic
        fun startServer() {
            // Android'de JSch Ed25519 ve curve25519 için Bouncy Castle sarmalayıcılarını kullanır; testte de aynısı
            mapOf(
                "ssh-ed25519" to "com.jcraft.jsch.bc.SignatureEd25519",
                "keypairgen.eddsa" to "com.jcraft.jsch.bc.KeyPairGenEdDSA",
                "xdh" to "com.jcraft.jsch.bc.XDH"
            ).forEach { (key, value) ->
                savedJschConfig[key] = JSch.getConfig(key)
                JSch.setConfig(key, value)
            }

            val allowedKeys = listOf(publicKey("id_ed25519.pub"), publicKey("id_ed25519_protected.pub"))

            server = SshServer.setUpDefaultServer().apply {
                host = "127.0.0.1"
                port = 0
                keyPairProvider = SimpleGeneratorHostKeyProvider(hostKeyFile)
                setPasswordAuthenticator { user, password, _ -> user == USER && password == PASSWORD }
                setPublickeyAuthenticator { user, key, _ -> user == USER && allowedKeys.any { KeyUtils.compareKeys(it, key) } }
                setCommandFactory { _, command -> RecordingCommand(command) }
                start()
            }
        }

        @AfterClass
        @JvmStatic
        fun stopServer() {
            server.stop(true)
            Files.deleteIfExists(hostKeyFile)
            savedJschConfig.forEach { (key, value) -> JSch.setConfig(key, value) }
        }
    }

    private class RecordingCommand(private val command: String) : Command {
        private var out: OutputStream? = null
        private var exitCallback: ExitCallback? = null

        override fun setInputStream(input: InputStream) {}
        override fun setOutputStream(out: OutputStream) { this.out = out }
        override fun setErrorStream(err: OutputStream) {}
        override fun setExitCallback(callback: ExitCallback) { exitCallback = callback }
        override fun destroy(channel: ChannelSession) {}

        override fun start(channel: ChannelSession, env: Environment) {
            executedCommands.add(command)
            if (command == HANGING_COMMAND) return
            thread {
                out?.apply { write("ok\n".toByteArray()); flush() }
                exitCallback?.onExit(0)
            }
        }
    }

    @Before
    fun clearCommands() = executedCommands.clear()

    private fun device(
        authType: String = "PASSWORD",
        password: String = PASSWORD,
        key: String = "",
        passphrase: String = ""
    ) = DeviceEntity(
        name = "Test PC",
        macAddress = "AA:BB:CC:DD:EE:FF",
        localIp = "127.0.0.1",
        shutdownType = "SSH",
        shutdownPort = server.port,
        shutdownUsername = USER,
        shutdownPassword = password,
        shutdownCommand = "systemctl poweroff",
        sshAuthType = authType,
        sshPrivateKey = key,
        sshKeyPassphrase = passphrase
    )

    @Test
    fun passwordLoginWorksWithoutRunningAnyCommand() = runBlocking {
        val result = ShutdownManager.testSshConnection(device())

        assertEquals("127.0.0.1", result.getOrThrow())
        assertTrue(executedCommands.isEmpty())
    }

    @Test
    fun wrongPasswordIsReportedAsWrongPassword() = runBlocking {
        val result = ShutdownManager.testSshConnection(device(password = "yanlış"))

        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message, message.contains("Şifre hatalı"))
    }

    @Test
    fun ed25519KeyFileWithoutExtensionSignsIn() = runBlocking {
        val result = ShutdownManager.testSshConnection(device(authType = "KEY", key = resource("id_ed25519")))

        assertEquals("127.0.0.1", result.getOrThrow())
    }

    @Test
    fun protectedKeySignsInWithItsPassphrase() = runBlocking {
        val result = ShutdownManager.testSshConnection(
            device(authType = "KEY", key = resource("id_ed25519_protected"), passphrase = "secret")
        )

        assertEquals("127.0.0.1", result.getOrThrow())
    }

    @Test
    fun wrongKeyPassphraseIsReportedBeforeConnecting() = runBlocking {
        val result = ShutdownManager.testSshConnection(
            device(authType = "KEY", key = resource("id_ed25519_protected"), passphrase = "yanlış")
        )

        assertTrue(result.exceptionOrNull()!!.message!!.contains("parolası hatalı"))
    }

    @Test
    fun shutdownRunsTheConfiguredCommandOverKeyAuth() = runBlocking {
        val result = ShutdownManager.executeShutdown(device(authType = "KEY", key = resource("id_ed25519")))

        assertTrue(result.isSuccess)
        assertEquals(listOf("systemctl poweroff"), executedCommands.toList())
    }

    @Test(timeout = 15_000)
    fun shutdownReturnsEvenWhenTheMachineNeverClosesTheSession() = runBlocking {
        val result = ShutdownManager.executeShutdown(
            device(authType = "KEY", key = resource("id_ed25519")).copy(shutdownCommand = HANGING_COMMAND)
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf(HANGING_COMMAND), executedCommands.toList())
    }

    @Test
    fun statusBannerCheckRecognisesRealSshServer() {
        assertTrue(DeviceStatusChecker.readSshBanner("127.0.0.1", server.port, 2000))
    }
}
