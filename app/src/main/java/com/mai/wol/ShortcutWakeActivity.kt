package com.mai.wol

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.widget.Toast
import com.mai.wol.data.AppDatabase
import com.mai.wol.network.WolManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShortcutWakeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceId = intent.getLongExtra("device_id", -1L)
        if (deviceId == -1L) {
            finish()
            return
        }

        val prefs = getSharedPreferences("wol_settings", Context.MODE_PRIVATE)
        val isAppLockEnabled = prefs.getBoolean("app_lock_enabled", false)
        val isWidgetLockEnabled = prefs.getBoolean("widget_lock_enabled", false)

        if (isAppLockEnabled && isWidgetLockEnabled) {
            val unlockIntent = Intent(this, WidgetUnlockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("device_id", deviceId)
            }
            startActivity(unlockIntent)
            finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            var wakeLock: PowerManager.WakeLock? = null
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "MaiWoL:ShortcutWakeLock"
                )?.apply {
                    acquire(4000)
                }

                val db = AppDatabase.getDatabase(applicationContext)
                val device = db.deviceDao().getDeviceById(deviceId)

                if (device != null) {
                    val packetCount = prefs.getInt("packet_count", 3)

                    val result = WolManager.sendMagicPacket(
                        macAddress = device.macAddress,
                        ipAddress = device.ipAddress,
                        localIp = device.localIp,
                        port = device.port,
                        secureOnPassword = device.secureOnPassword,
                        packetCount = packetCount
                    )

                    withContext(Dispatchers.Main) {
                        result.fold(
                            onSuccess = {
                                val msg = getString(R.string.packet_sent_success, device.name, packetCount)
                                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                            },
                            onFailure = {
                                val msg = getString(R.string.packet_sent_error, it.localizedMessage ?: "")
                                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }
    }
}