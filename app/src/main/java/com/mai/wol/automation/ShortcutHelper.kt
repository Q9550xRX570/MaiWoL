package com.mai.wol.automation

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import com.mai.wol.R
import com.mai.wol.ShortcutWakeActivity
import com.mai.wol.data.DeviceEntity

object ShortcutHelper {

    fun updateShortcuts(context: Context, devices: List<DeviceEntity>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            try {
                val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
                val maxShortcuts = shortcutManager.maxShortcutCountPerActivity.coerceAtMost(4)
                val topDevices = devices.take(maxShortcuts)

                val shortcuts = topDevices.map { device ->
                    val intent = Intent(context, ShortcutWakeActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        putExtra("device_id", device.id)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }

                    ShortcutInfo.Builder(context, "shortcut_device_${device.id}")
                        .setShortLabel(device.name)
                        .setLongLabel(device.name)
                        .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                        .setIntent(intent)
                        .build()
                }

                shortcutManager.dynamicShortcuts = shortcuts
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}