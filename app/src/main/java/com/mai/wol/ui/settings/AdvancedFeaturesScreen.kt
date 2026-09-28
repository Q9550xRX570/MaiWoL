package com.mai.wol.ui.settings

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mai.wol.automation.WolTileService

import com.mai.wol.R
import com.mai.wol.MainViewModel
import com.mai.wol.ui.automation.AutomationGuideDialog
import com.mai.wol.ui.backup.BackupAndRestoreDialog
import com.mai.wol.ui.diagnostics.DnsQueryDialog
import com.mai.wol.ui.diagnostics.NetworkInfoCard
import com.mai.wol.ui.diagnostics.PingToolDialog
import com.mai.wol.ui.lock.AppLockSettingsDialog
import com.mai.wol.ui.schedule.AllSchedulesOverviewDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedFeaturesScreen(
    viewModel: MainViewModel,
    isShizukuEnabled: Boolean,
    onRequestShizukuPermission: () -> Unit,
    onSetShizukuEnabled: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val devices by viewModel.devices.collectAsState()
    val tileDeviceId by viewModel.tileDeviceId.collectAsState()

    var showDnsDialog by remember { mutableStateOf(false) }
    var showPingDialog by remember { mutableStateOf(false) }
    var showInternalAutomationDialog by remember { mutableStateOf(false) }
    var showAutomationGuideDialog by remember { mutableStateOf(false) }
    var showShizukuDialog by remember { mutableStateOf(false) }
    var showAppLockDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showTileManagementDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("wol_settings", Context.MODE_PRIVATE) }
    var isAppLockActive by remember { mutableStateOf(prefs.getBoolean("app_lock_enabled", false)) }
    val pinLength = remember(isAppLockActive) { prefs.getInt("security_pin_length", 4) }

    val currentTileDeviceName = remember(devices, tileDeviceId) {
        if (tileDeviceId != -1L) {
            devices.firstOrNull { it.id == tileDeviceId }?.name ?: devices.firstOrNull()?.name ?: ""
        } else {
            devices.firstOrNull()?.name ?: ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.advanced_features)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. YEDEKLEME VE GERİ YÜKLEME (.maiwol)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showBackupDialog = true },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.backup_and_restore),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.backup_restore_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. HIZLI AYARLAR KARTI
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTileManagementDialog = true },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.quick_settings_tile),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.quick_settings_tile_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (currentTileDeviceName.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Hedef: $currentTileDeviceName",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // 3. DNS SORGU ARACI
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDnsDialog = true },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.dns_query_tool),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.dns_query_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 4. PING & GECİKME TESTİ
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPingDialog = true },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.ping_tool),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.ping_tool_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 5. DAHİLİ OTOMASYON
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showInternalAutomationDialog = true },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.internal_automation),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.internal_automation_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 6. HARİCİ OTOMASYON
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAutomationGuideDialog = true },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.external_automation),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.external_automation_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 7. SHIZUKU ENTEGRASYONU
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showShizukuDialog = true },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_shizuku),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.shizuku_integration),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isShizukuEnabled) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isShizukuEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.shizuku_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 8. UYGULAMA KİLİDİ
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAppLockDialog = true },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.app_lock),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isAppLockActive) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isAppLockActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isAppLockActive) "$pinLength " + stringResource(R.string.digits) + " · " + stringResource(R.string.app_lock_description)
                                else stringResource(R.string.app_lock_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                NetworkInfoCard(isShizukuEnabled = isShizukuEnabled)
            }
        }
    }

    if (showTileManagementDialog) {
        var isTileLockEnabledInDialog by remember {
            mutableStateOf(prefs.getBoolean("lock_tile_enabled", false))
        }

        AlertDialog(
            onDismissRequest = { showTileManagementDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.quick_settings_tile))
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.tile_target_device),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (devices.isEmpty()) {
                        Text(stringResource(R.string.no_devices_yet), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                devices.forEach { dev ->
                                    val isSelected = (tileDeviceId == dev.id) || (tileDeviceId == -1L && dev == devices.first())
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = isSelected,
                                                onClick = { viewModel.updateTileDeviceId(dev.id) }
                                            )
                                            .padding(vertical = 8.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { viewModel.updateTileDeviceId(dev.id) }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = dev.name, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.lock_tile), style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = stringResource(R.string.lock_tile_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!isAppLockActive) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.lock_tile_app_lock_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Switch(
                            checked = isTileLockEnabledInDialog,
                            onCheckedChange = { newValue ->
                                isTileLockEnabledInDialog = newValue
                                prefs.edit().putBoolean("lock_tile_enabled", newValue).apply()
                            }
                        )
                    }

                    HorizontalDivider()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Button(
                            onClick = {
                                try {
                                    val statusBarManager = context.getSystemService(StatusBarManager::class.java)
                                    statusBarManager?.requestAddTileService(
                                        ComponentName(context, WolTileService::class.java),
                                        context.getString(R.string.app_name),
                                        Icon.createWithResource(context, R.drawable.ic_launcher_foreground),
                                        { },
                                        { }
                                    )
                                    Toast.makeText(context, context.getString(R.string.tile_request_sent), Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.add_tile_to_shade))
                        }
                    }

                    Text(
                        text = stringResource(R.string.tile_manual_add_guide),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTileManagementDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    if (showBackupDialog) {
        BackupAndRestoreDialog(
            viewModel = viewModel,
            onDismiss = { showBackupDialog = false }
        )
    }

    if (showInternalAutomationDialog) {
        AllSchedulesOverviewDialog(
            viewModel = viewModel,
            onDismiss = { showInternalAutomationDialog = false }
        )
    }

    if (showDnsDialog) {
        DnsQueryDialog(onDismiss = { showDnsDialog = false })
    }

    if (showPingDialog) {
        PingToolDialog(onDismiss = { showPingDialog = false })
    }

    if (showAutomationGuideDialog) {
        AutomationGuideDialog(onDismiss = { showAutomationGuideDialog = false })
    }

    if (showShizukuDialog) {
        ShizukuDialog(
            isEnabled = isShizukuEnabled,
            onDismiss = { showShizukuDialog = false },
            onConfirm = { enabled ->
                if (enabled) {
                    onRequestShizukuPermission()
                } else {
                    onSetShizukuEnabled(false)
                }
            }
        )
    }

    if (showAppLockDialog) {
        AppLockSettingsDialog(
            onDismiss = {
                showAppLockDialog = false
                isAppLockActive = prefs.getBoolean("app_lock_enabled", false)
            }
        )
    }
}
