package com.mai.wol.ui.backup

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mai.wol.data.BackupData
import com.mai.wol.data.BackupManager
import kotlinx.coroutines.launch

import com.mai.wol.R
import com.mai.wol.MainViewModel

@Composable
fun BackupAndRestoreDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pendingImport by remember { mutableStateOf<BackupData?>(null) }
    var pendingEncryptedRawJson by remember { mutableStateOf<String?>(null) }

    var showExportOptionsDialog by remember { mutableStateOf(false) }
    var exportPin by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(
                context = context,
                uri = uri,
                pin = exportPin,
                onSuccess = {
                    Toast.makeText(context, context.getString(R.string.backup_success), Toast.LENGTH_SHORT).show()
                    exportPin = null
                    onDismiss()
                },
                onError = { err ->
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    exportPin = null
                }
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val rawJson = BackupManager.readStringFromUri(context, uri)
            if (rawJson != null) {
                if (BackupManager.isFileEncrypted(rawJson)) {
                    pendingEncryptedRawJson = rawJson
                } else {
                    val backup = BackupManager.parseBackupJson(rawJson)
                    if (backup != null && backup.devices.isNotEmpty()) {
                        pendingImport = backup
                    } else {
                        Toast.makeText(context, context.getString(R.string.invalid_backup_file), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.backup_and_restore))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.backup_restore_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { showExportOptionsDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.export_backup))
                }

                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.import_backup))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )

    if (showExportOptionsDialog) {
        var isEncryptedOption by remember { mutableStateOf(false) }
        var selectedPinLength by remember { mutableIntStateOf(4) }
        var pinInput by remember { mutableStateOf("") }
        var pinConfirm by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showExportOptionsDialog = false },
            title = { Text(stringResource(R.string.export_backup)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !isEncryptedOption,
                            onClick = { isEncryptedOption = false },
                            label = { Text(stringResource(R.string.plain_backup)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = isEncryptedOption,
                            onClick = { isEncryptedOption = true },
                            label = { Text(stringResource(R.string.encrypted_backup)) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (isEncryptedOption) {
                        HorizontalDivider()
                        Text(stringResource(R.string.pin_length), style = MaterialTheme.typography.titleSmall)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(2, 4, 6, 8).forEach { len ->
                                FilterChip(
                                    selected = selectedPinLength == len,
                                    onClick = {
                                        selectedPinLength = len
                                        pinInput = ""
                                        pinConfirm = ""
                                    },
                                    label = { Text("$len ${stringResource(R.string.digits)}") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= selectedPinLength && it.all { c -> c.isDigit() }) pinInput = it },
                            label = { Text(stringResource(R.string.set_pin)) },
                            placeholder = { Text("•".repeat(selectedPinLength)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = pinConfirm,
                            onValueChange = { if (it.length <= selectedPinLength && it.all { c -> c.isDigit() }) pinConfirm = it },
                            label = { Text(stringResource(R.string.confirm_pin)) },
                            placeholder = { Text("•".repeat(selectedPinLength)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (pinError != null) {
                            Text(pinError ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isEncryptedOption) {
                            if (pinInput.length != selectedPinLength) {
                                pinError = context.getString(R.string.pin_required_warning)
                                return@TextButton
                            }
                            if (pinInput != pinConfirm) {
                                pinError = context.getString(R.string.pin_mismatch)
                                return@TextButton
                            }
                            exportPin = pinInput
                        } else {
                            exportPin = null
                        }
                        showExportOptionsDialog = false
                        val fileName = BackupManager.generateBackupFileName()
                        exportLauncher.launch(fileName)
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportOptionsDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingEncryptedRawJson?.let { rawJson ->
        var pinInput by remember { mutableStateOf("") }
        var isPinError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { pendingEncryptedRawJson = null },
            title = { Text(stringResource(R.string.enter_backup_pin)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.enter_backup_pin_desc), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 8 && it.all { c -> c.isDigit() }) {
                                pinInput = it
                                isPinError = false
                            }
                        },
                        label = { Text("PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = isPinError,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isPinError) {
                        Text(stringResource(R.string.decrypt_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val decrypted = BackupManager.decryptBackupJson(rawJson, pinInput)
                        if (decrypted != null && decrypted.devices.isNotEmpty()) {
                            pendingEncryptedRawJson = null
                            pendingImport = decrypted
                        } else {
                            isPinError = true
                        }
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingEncryptedRawJson = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingImport?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = {
                Text(stringResource(R.string.import_confirm_desc, backup.devices.size, backup.schedules.size))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.importBackup(
                            context = context,
                            backupData = backup,
                            onSuccess = { count ->
                                Toast.makeText(context, context.getString(R.string.import_success, count), Toast.LENGTH_SHORT).show()
                                pendingImport = null
                                onDismiss()
                            },
                            onError = { err ->
                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                pendingImport = null
                            }
                        )
                    }
                ) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }
}
