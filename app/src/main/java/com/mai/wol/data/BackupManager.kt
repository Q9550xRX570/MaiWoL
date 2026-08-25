package com.mai.wol.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupData(
    val devices: List<DeviceEntity>,
    val schedules: List<ScheduleEntity>
)

object BackupManager {

    private const val ITERATION_COUNT = 10000
    private const val KEY_LENGTH = 256
    private const val GCM_TAG_LENGTH = 128

    fun exportBackupToJson(
        devices: List<DeviceEntity>,
        schedules: List<ScheduleEntity>,
        pin: String? = null
    ): String {
        val plainRoot = JSONObject()
        plainRoot.put("app", "MaiWoL")
        plainRoot.put("version", 3)
        plainRoot.put("timestamp", System.currentTimeMillis())

        val devArray = JSONArray()
        for (dev in devices) {
            val dObj = JSONObject()
            dObj.put("id", dev.id)
            dObj.put("name", dev.name)
            dObj.put("macAddress", dev.macAddress)
            dObj.put("ipAddress", dev.ipAddress)
            dObj.put("localIp", dev.localIp)
            dObj.put("port", dev.port)
            dObj.put("secureOnPassword", dev.secureOnPassword ?: "")
            dObj.put("groupName", dev.groupName)
            dObj.put("shutdownType", dev.shutdownType)
            dObj.put("shutdownPort", dev.shutdownPort)
            dObj.put("shutdownUsername", dev.shutdownUsername)
            dObj.put("shutdownPassword", dev.shutdownPassword)
            dObj.put("shutdownCommand", dev.shutdownCommand)
            dObj.put("shutdownHttpUrl", dev.shutdownHttpUrl)
            devArray.put(dObj)
        }
        plainRoot.put("devices", devArray)

        val schArray = JSONArray()
        for (sch in schedules) {
            val sObj = JSONObject()
            sObj.put("deviceId", sch.deviceId)
            sObj.put("hour", sch.hour)
            sObj.put("minute", sch.minute)
            sObj.put("daysOfWeek", sch.daysOfWeek)
            sObj.put("isEnabled", sch.isEnabled)
            sObj.put("isOneTime", sch.isOneTime)
            sObj.put("targetDateMillis", sch.targetDateMillis ?: -1L)
            schArray.put(sObj)
        }
        plainRoot.put("schedules", schArray)

        val rawJsonString = plainRoot.toString()

        if (pin.isNullOrBlank()) {
            plainRoot.put("encrypted", false)
            return plainRoot.toString(2)
        }

        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
        val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(rawJsonString.toByteArray(Charsets.UTF_8))

        val encryptedRoot = JSONObject()
        encryptedRoot.put("app", "MaiWoL")
        encryptedRoot.put("version", 3)
        encryptedRoot.put("timestamp", System.currentTimeMillis())
        encryptedRoot.put("encrypted", true)
        encryptedRoot.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
        encryptedRoot.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
        encryptedRoot.put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))

        return encryptedRoot.toString(2)
    }

    fun isFileEncrypted(jsonString: String): Boolean {
        return try {
            val root = JSONObject(jsonString)
            root.optBoolean("encrypted", false)
        } catch (e: Exception) {
            false
        }
    }

    fun decryptBackupJson(jsonString: String, pin: String): BackupData? {
        return try {
            val root = JSONObject(jsonString)
            val salt = Base64.decode(root.getString("salt"), Base64.NO_WRAP)
            val iv = Base64.decode(root.getString("iv"), Base64.NO_WRAP)
            val ciphertext = Base64.decode(root.getString("ciphertext"), Base64.NO_WRAP)

            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
            val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val decryptedBytes = cipher.doFinal(ciphertext)
            val decryptedJson = String(decryptedBytes, Charsets.UTF_8)

            parseBackupJson(decryptedJson)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun readStringFromUri(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.readText()
            reader.close()
            jsonString
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun parseBackupJson(jsonString: String): BackupData? {
        return try {
            val root = JSONObject(jsonString)
            if (root.optBoolean("encrypted", false)) {
                return null
            }
            if (!root.has("devices")) return null

            val devicesList = mutableListOf<DeviceEntity>()
            val devArray = root.getJSONArray("devices")
            for (i in 0 until devArray.length()) {
                val dObj = devArray.getJSONObject(i)
                val dev = DeviceEntity(
                    id = dObj.optLong("id", 0L),
                    name = dObj.getString("name"),
                    macAddress = dObj.getString("macAddress"),
                    ipAddress = dObj.optString("ipAddress", ""),
                    localIp = dObj.optString("localIp", ""),
                    port = dObj.optInt("port", 9),
                    secureOnPassword = dObj.optString("secureOnPassword", "").takeIf { it.isNotBlank() },
                    groupName = dObj.optString("groupName", ""),
                    shutdownType = dObj.optString("shutdownType", "NONE"),
                    shutdownPort = dObj.optInt("shutdownPort", 22),
                    shutdownUsername = dObj.optString("shutdownUsername", ""),
                    shutdownPassword = dObj.optString("shutdownPassword", ""),
                    shutdownCommand = dObj.optString("shutdownCommand", "shutdown /s /t 0"),
                    shutdownHttpUrl = dObj.optString("shutdownHttpUrl", "")
                )
                devicesList.add(dev)
            }

            val schedulesList = mutableListOf<ScheduleEntity>()
            if (root.has("schedules")) {
                val schArray = root.getJSONArray("schedules")
                for (i in 0 until schArray.length()) {
                    val sObj = schArray.getJSONObject(i)
                    val targetMillis = sObj.optLong("targetDateMillis", -1L)
                    val sch = ScheduleEntity(
                        id = 0L,
                        deviceId = sObj.getLong("deviceId"),
                        hour = sObj.getInt("hour"),
                        minute = sObj.getInt("minute"),
                        daysOfWeek = sObj.optString("daysOfWeek", "1,2,3,4,5"),
                        isEnabled = sObj.optBoolean("isEnabled", true),
                        isOneTime = sObj.optBoolean("isOneTime", false),
                        targetDateMillis = if (targetMillis != -1L) targetMillis else null
                    )
                    schedulesList.add(sch)
                }
            }

            BackupData(devicesList, schedulesList)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun writeStringToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            val outputStream = context.contentResolver.openOutputStream(uri, "wt") ?: return false
            val writer = OutputStreamWriter(outputStream)
            writer.write(content)
            writer.flush()
            writer.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun generateBackupFileName(): String {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        return "MaiWoL_Backup_$dateStr.maiwol"
    }
}