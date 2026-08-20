package com.mai.wol.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.mai.wol.R
import com.mai.wol.data.AppDatabase
import com.mai.wol.network.WolManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class WolAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getLongExtra("schedule_id", -1L)
        val deviceId = intent.getLongExtra("device_id", -1L)
        if (deviceId == -1L) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            var wakeLock: PowerManager.WakeLock? = null
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "MaiWoL:ScheduledWolWakeLock"
                )?.apply {
                    acquire(5000)
                }

                val db = AppDatabase.getDatabase(context)
                val device = db.deviceDao().getDeviceById(deviceId)
                if (device != null) {
                    val prefs = context.getSharedPreferences("wol_settings", Context.MODE_PRIVATE)
                    val packetCount = prefs.getInt("packet_count", 3)

                    WolManager.sendMagicPacket(
                        macAddress = device.macAddress,
                        ipAddress = device.ipAddress,
                        localIp = device.localIp,
                        port = device.port,
                        secureOnPassword = device.secureOnPassword,
                        packetCount = packetCount
                    )

                    showNotification(context, device.name)

                    if (scheduleId != -1L) {
                        val schedule = db.scheduleDao().getScheduleById(scheduleId)
                        if (schedule != null) {
                            if (schedule.isOneTime) {
                                db.scheduleDao().updateSchedule(schedule.copy(isEnabled = false))
                            } else if (schedule.isEnabled) {
                                AlarmScheduler.scheduleAlarm(context, schedule)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, deviceName: String) {
        val channelId = "wol_scheduled_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.scheduled_notification_title),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.scheduled_notification_title))
            .setContentText(context.getString(R.string.scheduled_notification_text, deviceName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val activeSchedules = db.scheduleDao().getAllActiveSchedules()
                    for (schedule in activeSchedules) {
                        AlarmScheduler.scheduleAlarm(context, schedule)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

class WolAutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.mai.wol.ACTION_WAKE_DEVICE") {
            val pendingResult = goAsync()

            // Parametreleri hem String hem Sayı olarak esnekçe oku
            val rawDeviceId = intent.getStringExtra("device_id")?.toLongOrNull()
                ?: intent.getLongExtra("device_id", -1L).takeIf { it != -1L }
                ?: intent.getIntExtra("device_id", -1).takeIf { it != -1 }?.toLong()
                ?: -1L

            val rawDeviceName = intent.getStringExtra("device_name")
                ?: intent.getStringExtra("name")

            val rawMac = intent.getStringExtra("mac_address")
                ?: intent.getStringExtra("mac")

            val rawIp = intent.getStringExtra("ip_address")
                ?: intent.getStringExtra("ip")
                ?: ""

            val rawLocalIp = intent.getStringExtra("local_ip") ?: ""

            val rawPort = intent.getStringExtra("port")?.toIntOrNull()
                ?: intent.getIntExtra("port", 9)

            val rawSecureOn = intent.getStringExtra("secure_on")
                ?: intent.getStringExtra("password")

            CoroutineScope(Dispatchers.IO).launch {
                var wakeLock: PowerManager.WakeLock? = null
                try {
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    wakeLock = powerManager?.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "MaiWoL:ExternalAutomationWakeLock"
                    )?.apply {
                        acquire(5000)
                    }

                    val db = AppDatabase.getDatabase(context)
                    val prefs = context.getSharedPreferences("wol_settings", Context.MODE_PRIVATE)
                    val packetCount = intent.getStringExtra("packet_count")?.toIntOrNull()
                        ?: intent.getIntExtra("packet_count", prefs.getInt("packet_count", 3))

                    fun clean(mac: String) = mac.replace(Regex("[^a-fA-F0-9]"), "")

                    val allDevices = db.deviceDao().getAllDevices().firstOrNull() ?: emptyList()

                    val targetDevice = when {
                        rawDeviceId != -1L -> allDevices.firstOrNull { it.id == rawDeviceId }
                        !rawDeviceName.isNullOrBlank() -> allDevices.firstOrNull { it.name.equals(rawDeviceName.trim(), ignoreCase = true) }
                        !rawMac.isNullOrBlank() -> allDevices.firstOrNull { clean(it.macAddress).equals(clean(rawMac), ignoreCase = true) }
                        else -> null
                    }

                    val sentDeviceName: String

                    if (targetDevice != null) {
                        sentDeviceName = targetDevice.name
                        WolManager.sendMagicPacket(
                            macAddress = targetDevice.macAddress,
                            ipAddress = targetDevice.ipAddress,
                            localIp = targetDevice.localIp,
                            port = targetDevice.port,
                            secureOnPassword = targetDevice.secureOnPassword,
                            packetCount = packetCount
                        )
                    } else if (!rawMac.isNullOrBlank()) {
                        sentDeviceName = rawMac
                        WolManager.sendMagicPacket(
                            macAddress = rawMac,
                            ipAddress = rawIp,
                            localIp = rawLocalIp,
                            port = rawPort,
                            secureOnPassword = rawSecureOn,
                            packetCount = packetCount
                        )
                    } else {
                        return@launch
                    }

                    // Harici tetikleme başarılı olduğunda bildirim ver
                    showExternalNotification(context, sentDeviceName)

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                    pendingResult.finish()
                }
            }
        }
    }

    private fun showExternalNotification(context: Context, deviceName: String) {
        val channelId = "wol_external_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.external_automation),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.external_automation))
            .setContentText(context.getString(R.string.scheduled_notification_text, deviceName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}