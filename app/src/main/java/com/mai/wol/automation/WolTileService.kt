package com.mai.wol.automation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.PowerManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.mai.wol.MainActivity
import com.mai.wol.R
import com.mai.wol.WidgetUnlockActivity
import com.mai.wol.data.AppDatabase
import com.mai.wol.network.WolManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.N)
class WolTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val prefs = getSharedPreferences("wol_settings", Context.MODE_PRIVATE)
        val targetDeviceId = prefs.getLong("tile_device_id", -1L)

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val targetDevice = if (targetDeviceId != -1L) {
                db.deviceDao().getDeviceById(targetDeviceId)
            } else {
                db.deviceDao().getAllDevices().firstOrNull()?.firstOrNull()
            }

            withContext(Dispatchers.Main) {
                tile.state = Tile.STATE_INACTIVE
                tile.label = targetDevice?.name ?: getString(R.string.app_name)
                tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_launcher_foreground)
                tile.updateTile()
            }
        }
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val prefs = getSharedPreferences("wol_settings", Context.MODE_PRIVATE)
            val targetDeviceId = prefs.getLong("tile_device_id", -1L)

            val devices = db.deviceDao().getAllDevices().firstOrNull() ?: emptyList()
            if (devices.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, getString(R.string.no_devices_yet), Toast.LENGTH_SHORT).show()
                    val intent = Intent(applicationContext, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivityAndCollapse(intent)
                }
                return@launch
            }

            val targetDevice = if (targetDeviceId != -1L) {
                db.deviceDao().getDeviceById(targetDeviceId) ?: devices.first()
            } else {
                devices.first()
            }

            val isAppLockEnabled = prefs.getBoolean("app_lock_enabled", false)
            val isTileLockEnabled = prefs.getBoolean("lock_tile_enabled", false)

            if (isAppLockEnabled && isTileLockEnabled) {
                withContext(Dispatchers.Main) {
                    val unlockIntent = Intent(applicationContext, WidgetUnlockActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("device_id", targetDevice.id)
                    }
                    startActivityAndCollapse(unlockIntent)
                }
                return@launch
            }

            var wakeLock: PowerManager.WakeLock? = null
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "MaiWoL:TileWakeLock"
                )?.apply {
                    acquire(4000)
                }

                val packetCount = prefs.getInt("packet_count", 3)
                val result = WolManager.sendMagicPacket(
                    macAddress = targetDevice.macAddress,
                    ipAddress = targetDevice.ipAddress,
                    localIp = targetDevice.localIp,
                    port = targetDevice.port,
                    secureOnPassword = targetDevice.secureOnPassword,
                    packetCount = packetCount
                )

                withContext(Dispatchers.Main) {
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = targetDevice.name
                    tile.updateTile()

                    result.fold(
                        onSuccess = {
                            val msg = getString(R.string.packet_sent_success, targetDevice.name, packetCount)
                            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                        },
                        onFailure = {
                            val msg = getString(R.string.packet_sent_error, it.localizedMessage ?: "")
                            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                delay(1500)

                withContext(Dispatchers.Main) {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = targetDevice.name
                    tile.updateTile()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            }
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    requestListeningState(context, ComponentName(context, WolTileService::class.java))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}