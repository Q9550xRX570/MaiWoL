package com.mai.wol.ui.device

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mai.wol.data.DeviceEntity
import com.mai.wol.network.DeviceStatusChecker
import com.mai.wol.network.NetworkScanner
import com.mai.wol.network.ScannedDevice
import com.mai.wol.network.ShutdownManager
import com.mai.wol.network.SshKeyInfo
import com.mai.wol.network.StatusResult
import com.mai.wol.ui.home.DeviceStatusBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.mai.wol.R

private const val MAX_KEY_FILE_BYTES = 64 * 1024

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddOrEditDeviceDialog(
    deviceToEdit: DeviceEntity?,
    useShizuku: Boolean,
    existingGroups: List<String> = emptyList(),
    defaultGroup: String = "",
    onDismiss: () -> Unit,
    onConfirm: (DeviceEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var name by remember { mutableStateOf(deviceToEdit?.name ?: "") }
    var mac by remember { mutableStateOf(deviceToEdit?.macAddress ?: "") }
    var ip by remember { mutableStateOf(deviceToEdit?.ipAddress ?: "") }
    var localIp by remember { mutableStateOf(deviceToEdit?.localIp ?: "") }
    var portText by remember { mutableStateOf(deviceToEdit?.port?.toString() ?: "9") }
    var secureOn by remember { mutableStateOf(deviceToEdit?.secureOnPassword ?: "") }
    var groupName by remember { mutableStateOf(deviceToEdit?.groupName ?: defaultGroup) }

    var shutdownType by remember { mutableStateOf(deviceToEdit?.shutdownType ?: "NONE") }
    var shutdownPortText by remember { mutableStateOf(deviceToEdit?.shutdownPort?.toString() ?: "22") }
    var shutdownUsername by remember { mutableStateOf(deviceToEdit?.shutdownUsername ?: "") }
    var shutdownPassword by remember { mutableStateOf(deviceToEdit?.shutdownPassword ?: "") }
    var shutdownCommand by remember { mutableStateOf(deviceToEdit?.shutdownCommand ?: "shutdown /s /f /t 0") }
    var shutdownHttpUrl by remember { mutableStateOf(deviceToEdit?.shutdownHttpUrl ?: "") }

    var sshAuthType by remember { mutableStateOf(deviceToEdit?.sshAuthType ?: "PASSWORD") }
    var sshPrivateKey by remember { mutableStateOf(deviceToEdit?.sshPrivateKey ?: "") }
    var sshKeyPassphrase by remember { mutableStateOf(deviceToEdit?.sshKeyPassphrase ?: "") }
    var sshKeyInfo by remember { mutableStateOf<SshKeyInfo?>(null) }
    var sshKeyError by remember { mutableStateOf<String?>(null) }

    // Şifre Göster / Gizle State'i
    var isPasswordVisible by remember { mutableStateOf(false) }

    var showScanSheet by remember { mutableStateOf(false) }

    var checkResult by remember { mutableStateOf<StatusResult?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var sshTestMessage by remember { mutableStateOf<String?>(null) }
    var sshTestOk by remember { mutableStateOf(false) }
    var isTestingSsh by remember { mutableStateOf(false) }

    LaunchedEffect(sshTestMessage) {
        if (sshTestMessage != null) scrollState.animateScrollTo(scrollState.maxValue)
    }

    LaunchedEffect(Unit) {
        val existingKey = deviceToEdit?.sshPrivateKey.orEmpty()
        if (existingKey.isNotBlank()) {
            sshKeyInfo = withContext(Dispatchers.IO) { ShutdownManager.inspectPrivateKey(existingKey) }
        }
    }

    val keyFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        if (bytes.size > MAX_KEY_FILE_BYTES) null else String(bytes, Charsets.UTF_8)
                    }
                }.getOrNull()
            }
            if (text == null) {
                sshKeyError = context.getString(R.string.ssh_key_read_error)
                return@launch
            }
            val info = withContext(Dispatchers.IO) { ShutdownManager.inspectPrivateKey(text) }
            if (info.valid) {
                sshPrivateKey = text
                sshKeyInfo = info
                sshKeyError = null
                sshTestMessage = null
            } else {
                sshKeyError = context.getString(R.string.ssh_key_invalid)
            }
        }
    }

    fun buildDevice(): DeviceEntity {
        val base = deviceToEdit ?: DeviceEntity(name = "", macAddress = "")
        return base.copy(
            name = name.trim(),
            macAddress = mac.trim(),
            ipAddress = ip.trim(),
            localIp = localIp.trim(),
            port = portText.toIntOrNull()?.takeIf { it > 0 } ?: 9,
            secureOnPassword = secureOn.trim().ifBlank { null },
            groupName = groupName.trim(),
            shutdownType = shutdownType,
            shutdownPort = shutdownPortText.toIntOrNull()?.takeIf { it > 0 } ?: 22,
            shutdownUsername = shutdownUsername.trim(),
            shutdownPassword = shutdownPassword,
            shutdownCommand = shutdownCommand,
            shutdownHttpUrl = shutdownHttpUrl.trim(),
            sshAuthType = sshAuthType,
            sshPrivateKey = sshPrivateKey,
            sshKeyPassphrase = sshKeyPassphrase
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (deviceToEdit == null) stringResource(R.string.add_device) else stringResource(R.string.edit_device)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.device_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = { Text(stringResource(R.string.mac_address)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text(stringResource(R.string.wan_ddns_address)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = localIp,
                    onValueChange = { localIp = it },
                    label = { Text(stringResource(R.string.local_ip_address)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text(stringResource(R.string.port_default)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = secureOn,
                    onValueChange = { secureOn = it },
                    label = { Text(stringResource(R.string.secureon_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text(stringResource(R.string.group_name_optional)) },
                    placeholder = { Text("Ev, Ofis, Sunucu...") },
                    leadingIcon = {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (existingGroups.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        existingGroups.forEach { grp ->
                            val isSel = groupName.equals(grp, ignoreCase = true)
                            FilterChip(
                                selected = isSel,
                                onClick = { groupName = if (isSel) "" else grp },
                                label = { Text(grp, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = stringResource(R.string.connection_check),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.connection_check_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = {
                        isChecking = true
                        scope.launch {
                            checkResult = DeviceStatusChecker.checkStatus(context, buildDevice())
                            isChecking = false
                        }
                    },
                    enabled = !isChecking && (ip.isNotBlank() || localIp.isNotBlank())
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.checking_now))
                    } else {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.check_now))
                    }
                }

                checkResult?.let { result ->
                    if (!isChecking) {
                        DeviceStatusBadge(result = result, onRefresh = {})
                    }
                }

                if (shutdownType != "SSH") {
                    Text(
                        text = stringResource(R.string.ssh_continue_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { shutdownType = "SSH" }) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.ssh_continue))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = stringResource(R.string.shutdown_method),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = shutdownType == "NONE",
                        onClick = { shutdownType = "NONE" },
                        label = { Text(stringResource(R.string.shutdown_method_none), style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = shutdownType == "SSH",
                        onClick = { shutdownType = "SSH" },
                        label = { Text(stringResource(R.string.shutdown_method_ssh), style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = shutdownType == "HTTP_GET",
                        onClick = { shutdownType = "HTTP_GET" },
                        label = { Text(stringResource(R.string.shutdown_method_http_get), style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = shutdownType == "HTTP_POST",
                        onClick = { shutdownType = "HTTP_POST" },
                        label = { Text(stringResource(R.string.shutdown_method_http_post), style = MaterialTheme.typography.labelSmall) }
                    )
                }

                if (shutdownType == "SSH") {
                    OutlinedTextField(
                        value = shutdownUsername,
                        onValueChange = { shutdownUsername = it },
                        label = { Text(stringResource(R.string.shutdown_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = stringResource(R.string.ssh_auth_method),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = sshAuthType == "PASSWORD",
                            onClick = { sshAuthType = "PASSWORD"; sshTestMessage = null },
                            label = { Text(stringResource(R.string.ssh_auth_password), style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = sshAuthType == "KEY",
                            onClick = { sshAuthType = "KEY"; sshTestMessage = null },
                            label = { Text(stringResource(R.string.ssh_auth_key), style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    if (sshAuthType == "KEY") {
                        OutlinedButton(
                            onClick = { keyFileLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (sshPrivateKey.isBlank()) stringResource(R.string.ssh_key_select)
                                else stringResource(R.string.ssh_key_change)
                            )
                        }

                        val info = sshKeyInfo
                        when {
                            sshKeyError != null -> Text(
                                text = sshKeyError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            info != null && info.valid -> Text(
                                text = if (info.encrypted) stringResource(R.string.ssh_key_loaded_encrypted, info.typeLabel)
                                else stringResource(R.string.ssh_key_loaded, info.typeLabel),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            else -> Text(
                                text = stringResource(R.string.ssh_key_formats),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = sshKeyPassphrase,
                            onValueChange = { sshKeyPassphrase = it },
                            label = { Text(stringResource(R.string.ssh_key_passphrase)) },
                            singleLine = true,
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val image = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = image,
                                        contentDescription = if (isPasswordVisible) "Şifreyi Gizle" else "Şifreyi Göster"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // ŞİFRE GÖSTER / GİZLE (GÖZ İKONLU)
                        OutlinedTextField(
                            value = shutdownPassword,
                            onValueChange = { shutdownPassword = it },
                            label = { Text(stringResource(R.string.shutdown_password)) },
                            singleLine = true,
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val image = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = image,
                                        contentDescription = if (isPasswordVisible) "Şifreyi Gizle" else "Şifreyi Göster"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = shutdownPortText,
                        onValueChange = { shutdownPortText = it },
                        label = { Text(stringResource(R.string.shutdown_port)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = shutdownCommand,
                        onValueChange = { shutdownCommand = it },
                        label = { Text(stringResource(R.string.shutdown_command)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SuggestionChip(
                            onClick = { shutdownCommand = "shutdown /s /f /t 0" },
                            label = { Text(stringResource(R.string.preset_windows), style = MaterialTheme.typography.labelSmall) }
                        )
                        SuggestionChip(
                            onClick = { shutdownCommand = "systemctl poweroff" },
                            label = { Text(stringResource(R.string.preset_linux), style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            isTestingSsh = true
                            sshTestMessage = null
                            scope.launch {
                                val result = ShutdownManager.testSshConnection(buildDevice())
                                sshTestOk = result.isSuccess
                                sshTestMessage = result.fold(
                                    onSuccess = { host -> context.getString(R.string.ssh_test_ok, host) },
                                    onFailure = { err -> context.getString(R.string.ssh_test_fail, err.localizedMessage ?: err.message ?: "") }
                                )
                                isTestingSsh = false
                            }
                        },
                        enabled = !isTestingSsh && shutdownUsername.isNotBlank() && (ip.isNotBlank() || localIp.isNotBlank()),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTestingSsh) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.ssh_test))
                    }

                    sshTestMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (sshTestOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                } else if (shutdownType == "HTTP_GET" || shutdownType == "HTTP_POST") {
                    OutlinedTextField(
                        value = shutdownHttpUrl,
                        onValueChange = { shutdownHttpUrl = it },
                        label = { Text(stringResource(R.string.shutdown_url)) },
                        placeholder = { Text("http://192.168.1.100:8080/shutdown") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = shutdownUsername,
                        onValueChange = { shutdownUsername = it },
                        label = { Text("${stringResource(R.string.shutdown_username)} (Opsiyonel)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = shutdownPassword,
                        onValueChange = { shutdownPassword = it },
                        label = { Text("${stringResource(R.string.shutdown_password)} (Opsiyonel)") },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = image,
                                    contentDescription = if (isPasswordVisible) "Şifreyi Gizle" else "Şifreyi Göster"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (deviceToEdit == null) {
                    TextButton(onClick = { showScanSheet = true }) {
                        Text(stringResource(R.string.auto_scan))
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(
                        onClick = {
                            if (name.isNotBlank() && mac.isNotBlank()) {
                                onConfirm(buildDevice())
                            }
                        }
                    ) {
                        Text(if (deviceToEdit == null) stringResource(R.string.add) else stringResource(R.string.save))
                    }
                }
            }
        },
        dismissButton = null
    )

    if (showScanSheet) {
        ScanNetworkBottomSheet(
            useShizuku = useShizuku,
            onDismiss = { showScanSheet = false },
            onDeviceSelected = { selectedDevice ->
                localIp = selectedDevice.ip
                if (selectedDevice.mac.isNotBlank()) mac = selectedDevice.mac
                if (name.isBlank()) name = selectedDevice.name
                showScanSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanNetworkBottomSheet(
    useShizuku: Boolean,
    onDismiss: () -> Unit,
    onDeviceSelected: (ScannedDevice) -> Unit
) {
    var isScanning by remember { mutableStateOf(true) }
    var devices by remember { mutableStateOf<List<ScannedDevice>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    fun startScan() {
        isScanning = true
        scope.launch {
            devices = NetworkScanner.scanLocalSubnet(context, useShizuku)
            isScanning = false
        }
    }

    LaunchedEffect(Unit) {
        startScan()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.local_network_devices),
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(
                    onClick = { startScan() },
                    enabled = !isScanning
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.scanning_network))
                    }
                }
            } else if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.no_devices_found))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(devices) { _, dev ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDeviceSelected(dev) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = dev.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "IP: ${dev.ip}" + if (dev.mac.isNotBlank()) " · MAC: ${dev.mac}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
