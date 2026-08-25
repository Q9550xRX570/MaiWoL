package com.mai.wol

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mai.wol.automation.AlarmScheduler
import com.mai.wol.automation.ShortcutHelper
import com.mai.wol.automation.WolTileService
import com.mai.wol.data.BackupData
import com.mai.wol.data.BackupManager
import com.mai.wol.data.DeviceDao
import com.mai.wol.data.DeviceEntity
import com.mai.wol.data.ScheduleDao
import com.mai.wol.data.ScheduleEntity
import com.mai.wol.network.DeviceStatus
import com.mai.wol.network.DeviceStatusChecker
import com.mai.wol.network.ShutdownManager
import com.mai.wol.network.WolManager
import com.mai.wol.widget.DeviceIconWidgetProvider
import com.mai.wol.widget.DeviceWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val deviceDao: DeviceDao,
    private val scheduleDao: ScheduleDao,
    private val sharedPreferences: SharedPreferences,
    private val appContext: Context
) : ViewModel() {

    val devices: StateFlow<List<DeviceEntity>> = deviceDao.getAllDevices()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allSchedules: StateFlow<List<ScheduleEntity>> = scheduleDao.getAllSchedules()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _hideGroupCounts = MutableStateFlow(sharedPreferences.getBoolean("hide_group_counts", false))
    val hideGroupCounts: StateFlow<Boolean> = _hideGroupCounts.asStateFlow()

    private val _showBatchWakeButton = MutableStateFlow(sharedPreferences.getBoolean("show_batch_wake_button", true))
    val showBatchWakeButton: StateFlow<Boolean> = _showBatchWakeButton.asStateFlow()

    private fun loadSavedGroups(): List<String> {
        val savedStr = sharedPreferences.getString("custom_groups_ordered", null)
        if (savedStr != null) {
            return savedStr.split("|").map { it.trim() }.filter { it.isNotBlank() }
        }
        val set = sharedPreferences.getStringSet("custom_groups", emptySet()) ?: emptySet()
        return set.toList().sorted()
    }

    private val _customGroups = MutableStateFlow(loadSavedGroups())
    val customGroups: StateFlow<List<String>> = _customGroups.asStateFlow()

    init {
        viewModelScope.launch {
            devices.collect { devList ->
                ShortcutHelper.updateShortcuts(appContext, devList)
                WolTileService.requestUpdate(appContext)
            }
        }
    }

    private val _packetCount = MutableStateFlow(sharedPreferences.getInt("packet_count", 3))
    val packetCount: StateFlow<Int> = _packetCount.asStateFlow()

    private val _statusCheckInterval = MutableStateFlow(sharedPreferences.getInt("status_check_interval", 5000))
    val statusCheckInterval: StateFlow<Int> = _statusCheckInterval.asStateFlow()

    private val _tileDeviceId = MutableStateFlow(sharedPreferences.getLong("tile_device_id", -1L))
    val tileDeviceId: StateFlow<Long> = _tileDeviceId.asStateFlow()

    private val _cardMacDisplay = MutableStateFlow(sharedPreferences.getString("card_mac_display", "show") ?: "show")
    val cardMacDisplay: StateFlow<String> = _cardMacDisplay.asStateFlow()

    private val _cardLocalIpDisplay = MutableStateFlow(sharedPreferences.getString("card_local_ip_display", "show") ?: "show")
    val cardLocalIpDisplay: StateFlow<String> = _cardLocalIpDisplay.asStateFlow()

    private val _cardWanIpDisplay = MutableStateFlow(sharedPreferences.getString("card_wan_ip_display", "show") ?: "show")
    val cardWanIpDisplay: StateFlow<String> = _cardWanIpDisplay.asStateFlow()

    private val _cardPortDisplay = MutableStateFlow(sharedPreferences.getString("card_port_display", "show") ?: "show")
    val cardPortDisplay: StateFlow<String> = _cardPortDisplay.asStateFlow()

    private val _totalWakeUps = MutableStateFlow(sharedPreferences.getInt("total_wake_ups", 0))
    val totalWakeUps: StateFlow<Int> = _totalWakeUps.asStateFlow()

    private val _totalPacketsSent = MutableStateFlow(sharedPreferences.getInt("total_packets_sent", 0))
    val totalPacketsSent: StateFlow<Int> = _totalPacketsSent.asStateFlow()

    private val _isShizukuEnabled = MutableStateFlow(sharedPreferences.getBoolean("use_shizuku", false))
    val isShizukuEnabled: StateFlow<Boolean> = _isShizukuEnabled.asStateFlow()

    private val _deviceStatuses = MutableStateFlow<Map<Long, DeviceStatus>>(emptyMap())
    val deviceStatuses: StateFlow<Map<Long, DeviceStatus>> = _deviceStatuses.asStateFlow()

    fun updateHideGroupCounts(hide: Boolean) {
        _hideGroupCounts.value = hide
        sharedPreferences.edit().putBoolean("hide_group_counts", hide).apply()
    }

    fun updateShowBatchWakeButton(show: Boolean) {
        _showBatchWakeButton.value = show
        sharedPreferences.edit().putBoolean("show_batch_wake_button", show).apply()
    }

    fun updateGroupsOrder(newOrder: List<String>) {
        val cleanList = newOrder.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        _customGroups.value = cleanList
        sharedPreferences.edit()
            .putString("custom_groups_ordered", cleanList.joinToString("|"))
            .putStringSet("custom_groups", cleanList.toSet())
            .apply()
    }

    fun addGroup(groupName: String) {
        val trimmed = groupName.trim()
        if (trimmed.isBlank()) return
        val current = _customGroups.value.toMutableList()
        if (!current.contains(trimmed)) {
            current.add(trimmed)
            updateGroupsOrder(current)
        }
    }

    fun renameGroup(oldName: String, newName: String) {
        viewModelScope.launch {
            val trimmedOld = oldName.trim()
            val trimmedNew = newName.trim()
            if (trimmedOld.isBlank() || trimmedNew.isBlank() || trimmedOld == trimmedNew) return@launch

            val current = _customGroups.value.toMutableList()
            val idx = current.indexOf(trimmedOld)
            if (idx != -1) {
                current[idx] = trimmedNew
            } else {
                current.add(trimmedNew)
            }
            updateGroupsOrder(current.distinct())

            val currentDevices = deviceDao.getAllDevices().firstOrNull() ?: emptyList()
            currentDevices.filter { it.groupName.equals(trimmedOld, ignoreCase = true) }.forEach { dev ->
                deviceDao.updateDevice(dev.copy(groupName = trimmedNew))
            }
        }
    }

    fun deleteGroup(groupName: String) {
        viewModelScope.launch {
            val trimmed = groupName.trim()
            if (trimmed.isBlank()) return@launch

            val current = _customGroups.value.toMutableList()
            current.remove(trimmed)
            updateGroupsOrder(current)

            val currentDevices = deviceDao.getAllDevices().firstOrNull() ?: emptyList()
            currentDevices.filter { it.groupName.equals(trimmed, ignoreCase = true) }.forEach { dev ->
                deviceDao.updateDevice(dev.copy(groupName = ""))
            }
        }
    }

    fun updateDeviceGroup(deviceId: Long, newGroupName: String) {
        viewModelScope.launch {
            val dev = deviceDao.getDeviceById(deviceId) ?: return@launch
            val trimmed = newGroupName.trim()
            deviceDao.updateDevice(dev.copy(groupName = trimmed))
            if (trimmed.isNotBlank()) {
                addGroup(trimmed)
            }
        }
    }

    fun updateTileDeviceId(deviceId: Long) {
        _tileDeviceId.value = deviceId
        sharedPreferences.edit().putLong("tile_device_id", deviceId).apply()
        WolTileService.requestUpdate(appContext)
    }

    fun updatePacketCount(count: Int) {
        val validCount = count.coerceIn(1, 20)
        _packetCount.value = validCount
        sharedPreferences.edit().putInt("packet_count", validCount).apply()
    }

    fun updateStatusCheckInterval(intervalMs: Int) {
        val validInterval = if (intervalMs <= 0) 0 else intervalMs.coerceIn(1000, 60000)
        _statusCheckInterval.value = validInterval
        sharedPreferences.edit().putInt("status_check_interval", validInterval).apply()
    }

    fun updateCardCustomization(mac: String, localIp: String, wanIp: String, port: String) {
        _cardMacDisplay.value = mac
        _cardLocalIpDisplay.value = localIp
        _cardWanIpDisplay.value = wanIp
        _cardPortDisplay.value = port

        sharedPreferences.edit()
            .putString("card_mac_display", mac)
            .putString("card_local_ip_display", localIp)
            .putString("card_wan_ip_display", wanIp)
            .putString("card_port_display", port)
            .apply()
    }

    fun setShizukuEnabled(enabled: Boolean) {
        _isShizukuEnabled.value = enabled
        sharedPreferences.edit().putBoolean("use_shizuku", enabled).apply()
    }

    fun addDevice(
        name: String,
        macAddress: String,
        ipAddress: String,
        localIp: String,
        port: Int,
        secureOn: String?,
        groupName: String = "",
        shutdownType: String = "NONE",
        shutdownPort: Int = 22,
        shutdownUsername: String = "",
        shutdownPassword: String = "",
        shutdownCommand: String = "shutdown /s /t 0",
        shutdownHttpUrl: String = ""
    ) {
        viewModelScope.launch {
            val trimmedGroup = groupName.trim()
            val entity = DeviceEntity(
                name = name,
                macAddress = macAddress,
                ipAddress = ipAddress.trim(),
                localIp = localIp.trim(),
                port = if (port <= 0) 9 else port,
                secureOnPassword = secureOn?.takeIf { it.isNotBlank() },
                groupName = trimmedGroup,
                shutdownType = shutdownType,
                shutdownPort = shutdownPort,
                shutdownUsername = shutdownUsername,
                shutdownPassword = shutdownPassword,
                shutdownCommand = shutdownCommand,
                shutdownHttpUrl = shutdownHttpUrl
            )
            deviceDao.insertDevice(entity)
            if (trimmedGroup.isNotBlank()) {
                addGroup(trimmedGroup)
            }
            DeviceWidgetProvider.updateAllWidgets(appContext)
            DeviceIconWidgetProvider.updateAllWidgets(appContext)
            WolTileService.requestUpdate(appContext)
        }
    }

    fun updateDevice(device: DeviceEntity) {
        viewModelScope.launch {
            deviceDao.updateDevice(device)
            if (device.groupName.isNotBlank()) {
                addGroup(device.groupName)
            }
            DeviceWidgetProvider.updateAllWidgets(appContext)
            DeviceIconWidgetProvider.updateAllWidgets(appContext)
            WolTileService.requestUpdate(appContext)
        }
    }

    fun deleteDevice(device: DeviceEntity) {
        viewModelScope.launch {
            deviceDao.deleteDevice(device)
            DeviceWidgetProvider.updateAllWidgets(appContext)
            DeviceIconWidgetProvider.updateAllWidgets(appContext)
            WolTileService.requestUpdate(appContext)
        }
    }

    fun refreshDeviceStatus(context: Context, device: DeviceEntity) {
        viewModelScope.launch {
            _deviceStatuses.value = _deviceStatuses.value + (device.id to DeviceStatus.CHECKING)
            val status = DeviceStatusChecker.checkStatus(context, device)
            _deviceStatuses.value = _deviceStatuses.value + (device.id to status)
        }
    }

    fun checkAllDevicesStatus(context: Context, deviceList: List<DeviceEntity>) {
        viewModelScope.launch {
            deviceList.forEach { dev ->
                launch {
                    val status = DeviceStatusChecker.checkStatus(context, dev)
                    _deviceStatuses.value = _deviceStatuses.value + (dev.id to status)
                }
            }
        }
    }

    fun getSchedulesForDevice(deviceId: Long): Flow<List<ScheduleEntity>> {
        return scheduleDao.getSchedulesForDevice(deviceId)
    }

    fun saveSchedule(context: Context, schedule: ScheduleEntity) {
        viewModelScope.launch {
            val id = scheduleDao.insertSchedule(schedule)
            val updatedSchedule = schedule.copy(id = if (schedule.id == 0L) id else schedule.id)
            if (updatedSchedule.isEnabled) {
                AlarmScheduler.scheduleAlarm(context, updatedSchedule)
            } else {
                AlarmScheduler.cancelAlarm(context, updatedSchedule.id)
            }
        }
    }

    fun deleteSchedule(context: Context, schedule: ScheduleEntity) {
        viewModelScope.launch {
            AlarmScheduler.cancelAlarm(context, schedule.id)
            scheduleDao.deleteSchedule(schedule)
        }
    }

    fun toggleSchedule(context: Context, schedule: ScheduleEntity, isEnabled: Boolean) {
        viewModelScope.launch {
            val updated = schedule.copy(isEnabled = isEnabled)
            scheduleDao.updateSchedule(updated)
            if (isEnabled) {
                AlarmScheduler.scheduleAlarm(context, updated)
            } else {
                AlarmScheduler.cancelAlarm(context, schedule.id)
            }
        }
    }

    fun sendWol(device: DeviceEntity, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val currentPacketCount = _packetCount.value
            val result = WolManager.sendMagicPacket(
                macAddress = device.macAddress,
                ipAddress = device.ipAddress,
                localIp = device.localIp,
                port = device.port,
                secureOnPassword = device.secureOnPassword,
                packetCount = currentPacketCount
            )
            result.fold(
                onSuccess = {
                    val newWakeUps = _totalWakeUps.value + 1
                    val newPackets = _totalPacketsSent.value + currentPacketCount
                    _totalWakeUps.value = newWakeUps
                    _totalPacketsSent.value = newPackets
                    sharedPreferences.edit()
                        .putInt("total_wake_ups", newWakeUps)
                        .putInt("total_packets_sent", newPackets)
                        .apply()
                    onResult(true, null)
                },
                onFailure = { onResult(false, it.message) }
            )
        }
    }

    fun sendShutdown(device: DeviceEntity, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = ShutdownManager.executeShutdown(device)
            result.fold(
                onSuccess = { msg -> onResult(true, msg) },
                onFailure = { err -> onResult(false, err.localizedMessage ?: err.message) }
            )
        }
    }

    fun wakeAllDevices(deviceList: List<DeviceEntity>, onComplete: (Int, Int) -> Unit) {
        viewModelScope.launch {
            val currentPacketCount = _packetCount.value
            var successCount = 0
            var failCount = 0

            deviceList.forEachIndexed { index, device ->
                val result = WolManager.sendMagicPacket(
                    macAddress = device.macAddress,
                    ipAddress = device.ipAddress,
                    localIp = device.localIp,
                    port = device.port,
                    secureOnPassword = device.secureOnPassword,
                    packetCount = currentPacketCount
                )
                result.fold(
                    onSuccess = { successCount++ },
                    onFailure = { failCount++ }
                )
                if (index < deviceList.size - 1) {
                    delay(80)
                }
            }

            if (successCount > 0) {
                val newWakeUps = _totalWakeUps.value + successCount
                val newPackets = _totalPacketsSent.value + (successCount * currentPacketCount)
                _totalWakeUps.value = newWakeUps
                _totalPacketsSent.value = newPackets
                sharedPreferences.edit()
                    .putInt("total_wake_ups", newWakeUps)
                    .putInt("total_packets_sent", newPackets)
                    .apply()
            }

            onComplete(successCount, failCount)
        }
    }

    fun exportBackup(
        context: Context,
        uri: Uri,
        pin: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val currentDevices = deviceDao.getAllDevices().firstOrNull() ?: emptyList()
                val currentSchedules = scheduleDao.getAllSchedules().firstOrNull() ?: emptyList()
                val jsonContent = BackupManager.exportBackupToJson(currentDevices, currentSchedules, pin)
                val success = BackupManager.writeStringToUri(context, uri, jsonContent)
                if (success) {
                    onSuccess()
                } else {
                    onError("Dosya kaydedilemedi")
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Hata oluştu")
            }
        }
    }

    fun importBackup(
        context: Context,
        backupData: BackupData,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                var importedCount = 0
                val oldToNewIdMap = mutableMapOf<Long, Long>()

                for (dev in backupData.devices) {
                    val newDev = dev.copy(id = 0L)
                    val newId = deviceDao.insertDevice(newDev)
                    oldToNewIdMap[dev.id] = newId
                    importedCount++
                    if (newDev.groupName.isNotBlank()) {
                        addGroup(newDev.groupName)
                    }
                }

                for (sch in backupData.schedules) {
                    val targetDeviceId = oldToNewIdMap[sch.deviceId] ?: sch.deviceId
                    val newSch = sch.copy(id = 0L, deviceId = targetDeviceId)
                    val schId = scheduleDao.insertSchedule(newSch)
                    if (newSch.isEnabled) {
                        AlarmScheduler.scheduleAlarm(context, newSch.copy(id = schId))
                    }
                }

                DeviceWidgetProvider.updateAllWidgets(appContext)
                DeviceIconWidgetProvider.updateAllWidgets(appContext)
                WolTileService.requestUpdate(appContext)
                onSuccess(importedCount)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Hata oluştu")
            }
        }
    }
}

class MainViewModelFactory(
    private val deviceDao: DeviceDao,
    private val scheduleDao: ScheduleDao,
    private val sharedPreferences: SharedPreferences,
    private val appContext: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(deviceDao, scheduleDao, sharedPreferences, appContext) as T
        }
        throw IllegalArgumentException("Bilinmeyen ViewModel sınıfı")
    }
}