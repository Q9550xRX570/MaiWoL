package com.mai.wol.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

import com.mai.wol.R

@Composable
fun ThemeSelectionDialog(
    currentTheme: String,
    onDismiss: () -> Unit,
    onThemeSelected: (String) -> Unit
) {
    val options = listOf(
        "system" to stringResource(R.string.theme_system_default),
        "light" to stringResource(R.string.theme_light),
        "dark" to stringResource(R.string.theme_dark)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_theme)) },
        text = {
            Column {
                options.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (currentTheme == key),
                                onClick = { onThemeSelected(key) }
                            )
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (currentTheme == key),
                            onClick = { onThemeSelected(key) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun GroupCustomizationDialog(
    hideGroupCounts: Boolean,
    showBatchWakeButton: Boolean,
    onDismiss: () -> Unit,
    onToggleHideCounts: (Boolean) -> Unit,
    onToggleShowBatchWakeButton: (Boolean) -> Unit
) {
    var hideCountsState by remember { mutableStateOf(hideGroupCounts) }
    var showBatchWakeState by remember { mutableStateOf(showBatchWakeButton) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.group_customization), style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.hide_group_counts),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.hide_group_counts_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = hideCountsState,
                        onCheckedChange = {
                            hideCountsState = it
                            onToggleHideCounts(it)
                        }
                    )
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.show_batch_wake_button),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.show_batch_wake_button_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = showBatchWakeState,
                        onCheckedChange = {
                            showBatchWakeState = it
                            onToggleShowBatchWakeButton(it)
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

@Composable
fun LanguageSelectionDialog(
    currentLangTag: String,
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit
) {
    val options = listOf(
        "" to stringResource(R.string.system_default),
        "tr" to stringResource(R.string.turkish),
        "en" to stringResource(R.string.english)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_language)) },
        text = {
            Column {
                options.forEach { (tag, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (currentLangTag == tag),
                                onClick = { onLanguageSelected(tag) }
                            )
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (currentLangTag == tag),
                            onClick = { onLanguageSelected(tag) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun CardCustomizationDialog(
    currentMac: String,
    currentLocalIp: String,
    currentWanIp: String,
    currentPort: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var macOpt by remember { mutableStateOf(currentMac) }
    var localIpOpt by remember { mutableStateOf(currentLocalIp) }
    var wanIpOpt by remember { mutableStateOf(currentWanIp) }
    var portOpt by remember { mutableStateOf(currentPort) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.card_customization), style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CustomizationRow(
                    label = stringResource(R.string.field_mac_address),
                    selectedOption = macOpt,
                    onOptionSelected = { macOpt = it }
                )
                CustomizationRow(
                    label = stringResource(R.string.field_local_ip),
                    selectedOption = localIpOpt,
                    onOptionSelected = { localIpOpt = it }
                )
                CustomizationRow(
                    label = stringResource(R.string.field_wan_address),
                    selectedOption = wanIpOpt,
                    onOptionSelected = { wanIpOpt = it }
                )
                CustomizationRow(
                    label = stringResource(R.string.field_port),
                    selectedOption = portOpt,
                    onOptionSelected = { portOpt = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(macOpt, localIpOpt, wanIpOpt, portOpt) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun CustomizationRow(
    label: String,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedOption == "show",
                onClick = { onOptionSelected("show") },
                label = { Text(stringResource(R.string.display_show), style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedOption == "mask",
                onClick = { onOptionSelected("mask") },
                label = { Text(stringResource(R.string.display_mask), style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedOption == "hide",
                onClick = { onOptionSelected("hide") },
                label = { Text(stringResource(R.string.display_hide), style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun PacketCountSettingsDialog(
    currentCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var tempCount by remember { mutableIntStateOf(currentCount) }
    var showManualInput by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onConfirm(tempCount) },
        title = { Text(stringResource(R.string.packet_count_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.packet_count_range),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { if (tempCount > 1) tempCount-- },
                        enabled = tempCount > 1
                    ) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null)
                    }

                    Surface(
                        modifier = Modifier
                            .clickable { showManualInput = true }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.packets_format, tempCount),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    IconButton(
                        onClick = { if (tempCount < 20) tempCount++ },
                        enabled = tempCount < 20
                    ) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                    }
                }

                Slider(
                    value = tempCount.toFloat(),
                    onValueChange = { tempCount = it.roundToInt().coerceIn(1, 20) },
                    valueRange = 1f..20f,
                    steps = 18
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { tempCount = 3 }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.default_3),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = { onConfirm(tempCount) }) { Text(stringResource(R.string.ok)) }
                }
            }
        },
        dismissButton = null
    )

    if (showManualInput) {
        ManualPacketCountDialog(
            currentCount = tempCount,
            onDismiss = { showManualInput = false },
            onConfirm = { newCount ->
                tempCount = newCount.coerceIn(1, 20)
                showManualInput = false
            }
        )
    }
}

@Composable
fun ManualPacketCountDialog(
    currentCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf(currentCount.toString()) }

    AlertDialog(
        onDismissRequest = {
            val count = textValue.toIntOrNull() ?: currentCount
            onConfirm(count)
        },
        title = { Text(stringResource(R.string.enter_packet_count)) },
        text = {
            Column {
                Text(stringResource(R.string.enter_packet_count_manual))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val count = textValue.toIntOrNull() ?: currentCount
                    onConfirm(count)
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
