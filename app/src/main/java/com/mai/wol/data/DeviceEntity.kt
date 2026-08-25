package com.mai.wol.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val macAddress: String,
    val ipAddress: String = "",
    val localIp: String = "",
    val port: Int = 9,
    val secureOnPassword: String? = null,
    val groupName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    // Kapatma Parametreleri (Varsayılan Force Kapatma: shutdown /s /f /t 0)
    val shutdownType: String = "NONE",
    val shutdownPort: Int = 22,
    val shutdownUsername: String = "",
    val shutdownPassword: String = "",
    val shutdownCommand: String = "shutdown /s /f /t 0",
    val shutdownHttpUrl: String = ""
)