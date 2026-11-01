package com.mai.wol.network

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Turns a pasted or picked SSH private key into the bytes JSch can load.
 * File pickers are optional: the same text that starts with -----BEGIN works on its own.
 */
object SshKeyText {

    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
        }
        if (looksLikeUtf16Le(bytes)) {
            return bytes.toString(Charsets.UTF_16LE)
        }
        return bytes.toString(Charsets.UTF_8)
    }

    fun readAtMost(input: InputStream, maxBytes: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    fun looksLikePublicKey(text: String): Boolean {
        val t = text.trim().removePrefix("\uFEFF").trim()
        if (t.contains("-----BEGIN ") && t.contains("PRIVATE KEY")) return false
        if (t.contains("PuTTY-User-Key-File-")) return false
        if (t.lineSequence().any { isOpenSshPublicKeyLine(it.trim()) }) return true
        return t.contains("-----BEGIN ") && t.contains("PUBLIC KEY")
    }

    private fun isOpenSshPublicKeyLine(line: String): Boolean {
        return line.startsWith("ssh-rsa ") ||
            line.startsWith("ssh-ed25519 ") ||
            line.startsWith("ssh-dss ") ||
            line.startsWith("ecdsa-sha2-") ||
            line.startsWith("sk-ssh-") ||
            line.startsWith("sk-ecdsa-")
    }

    /** True once the paste is a whole key, not a few characters the user is still typing. */
    fun looksComplete(raw: String): Boolean {
        val t = raw.trim()
        if (t.isEmpty()) return false
        if (looksLikePublicKey(t)) return true
        if (t.contains("-----END ")) return true
        if (t.contains("PuTTY-User-Key-File-")) return true
        return false
    }

    fun extract(raw: String): String {
        val text = raw
            .replace("\uFEFF", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .trim()

        val privateBegin = PRIVATE_MARKERS
            .map { text.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull()
        if (privateBegin != null) {
            return slicePem(text, privateBegin)
        }

        val begin = text.indexOf("-----BEGIN ")
        if (begin >= 0) {
            return slicePem(text, begin)
        }

        val putty = text.indexOf("PuTTY-User-Key-File-")
        if (putty >= 0) {
            return text.substring(putty).trim()
        }
        return text
    }

    private fun slicePem(text: String, begin: Int): String {
        val endLabel = text.indexOf("-----END ", begin + 1)
        if (endLabel >= 0) {
            val close = text.indexOf("-----", endLabel + "-----END ".length)
            if (close >= 0) {
                return text.substring(begin, close + 5).trim()
            }
        }
        return text.substring(begin).trim()
    }

    private val PRIVATE_MARKERS = listOf(
        "-----BEGIN OPENSSH PRIVATE KEY-----",
        "-----BEGIN RSA PRIVATE KEY-----",
        "-----BEGIN DSA PRIVATE KEY-----",
        "-----BEGIN EC PRIVATE KEY-----",
        "-----BEGIN ECDSA PRIVATE KEY-----",
        "-----BEGIN PRIVATE KEY-----",
        "-----BEGIN ENCRYPTED PRIVATE KEY-----"
    )

    private fun looksLikeUtf16Le(bytes: ByteArray): Boolean {
        if (bytes.size < 16 || bytes.size % 2 != 0) return false
        var zeros = 0
        var checked = 0
        var i = 1
        while (i < bytes.size && checked < 32) {
            if (bytes[i] == 0.toByte()) zeros++
            checked++
            i += 2
        }
        return checked > 0 && zeros * 2 >= checked
    }
}
