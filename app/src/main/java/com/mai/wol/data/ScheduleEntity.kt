package com.mai.wol.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "schedules",
    foreignKeys = [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["deviceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("deviceId")]
)
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceId: Long,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: String = "1,2,3,4,5,6,7", // 1=Pzt, 2=Sal ... 7=Paz
    val isEnabled: Boolean = true,
    val isOneTime: Boolean = false, // Tek seferlik tarih mi?
    val targetDateMillis: Long? = null // Belirli bir tarih seçildiğinde epoch ms
)