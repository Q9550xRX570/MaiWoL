package com.mai.wol.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.widget.RemoteViews
import android.widget.Toast
import com.mai.wol.MainActivity
import com.mai.wol.R
import com.mai.wol.WidgetUnlockActivity
import com.mai.wol.data.AppDatabase
import com.mai.wol.network.WolManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (appWidgetId in appWidgetIds) {
            editor.remove("widget_device_$appWidgetId")
        }
        editor.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_WIDGET_WAKE) {
            val deviceId = intent.getLongExtra(EXTRA_DEVICE_ID, -1L)
            if (deviceId == -1L) return

            val prefs = context.getSharedPreferences("wol_settings", Context.MODE_PRIVATE)
            val isAppLockEnabled = prefs.getBoolean("app_lock_enabled", false)
            val isWidgetLockEnabled = prefs.getBoolean("widget_lock_enabled", false)

            if (isAppLockEnabled && isWidgetLockEnabled) {
                val unlockIntent = Intent(context, WidgetUnlockActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("device_id", deviceId)
                }
                context.startActivity(unlockIntent)
                return
            }

            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                var wakeLock: PowerManager.WakeLock? = null
                try {
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    wakeLock = powerManager?.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "MaiWoL:WidgetCardWakeLock"
                    )?.apply {
                        acquire(4000)
                    }

                    val db = AppDatabase.getDatabase(context)
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
                                    val msg = context.getString(R.string.packet_sent_success, device.name, packetCount)
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                onFailure = {
                                    val msg = context.getString(R.string.packet_sent_error, it.localizedMessage ?: "")
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.widget_device_not_found), Toast.LENGTH_SHORT).show()
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
    }

    companion object {
        const val ACTION_WIDGET_WAKE = "com.mai.wol.ACTION_WIDGET_WAKE"
        const val EXTRA_DEVICE_ID = "extra_device_id"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val deviceId = prefs.getLong("widget_device_$appWidgetId", -1L)

            val views = RemoteViews(context.packageName, R.layout.widget_device_wake)

            val mainIntent = Intent(context, MainActivity::class.java)
            val mainPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, mainPendingIntent)
            views.setTextViewText(R.id.widget_btn_text, context.getString(R.string.wake_up))

            if (deviceId == -1L) {
                views.setTextViewText(R.id.widget_device_name, context.getString(R.string.widget_card_name))
                views.setTextViewText(R.id.widget_device_mac, context.getString(R.string.select_device_for_widget))
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(context)
                val device = db.deviceDao().getDeviceById(deviceId)

                if (device != null) {
                    views.setTextViewText(R.id.widget_device_name, device.name)
                    views.setTextViewText(R.id.widget_device_mac, device.macAddress)

                    val wakeIntent = Intent(context, DeviceWidgetProvider::class.java).apply {
                        action = ACTION_WIDGET_WAKE
                        putExtra(EXTRA_DEVICE_ID, device.id)
                    }

                    val wakePendingIntent = PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        wakeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    views.setOnClickPendingIntent(R.id.widget_wake_button, wakePendingIntent)
                } else {
                    views.setTextViewText(R.id.widget_device_name, context.getString(R.string.widget_device_not_found))
                    views.setTextViewText(R.id.widget_device_mac, "")
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, DeviceWidgetProvider::class.java)
            )
            for (id in ids) {
                updateAppWidget(context, appWidgetManager, id)
            }
        }
    }
}