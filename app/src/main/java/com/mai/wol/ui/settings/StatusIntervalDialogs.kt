package com.mai.wol.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

import com.mai.wol.R

@Composable
fun StatusIntervalSettingsDialog(
    currentInterval: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var isEnabled by remember { mutableStateOf(currentInterval > 0) }
    var tempInterval by remember { mutableIntStateOf(if (currentInterval > 0) currentInterval else 5000) }
    var showManualInput by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onConfirm(if (isEnabled) tempInterval else 0) },
        title = { Text(stringResource(R.string.enter_interval_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.enable_status_check), style = MaterialTheme.typography.titleSmall)
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it }
                    )
                }

                if (isEnabled) {
                    HorizontalDivider()

                    Text(
                        text = stringResource(R.string.interval_range),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { if (tempInterval > 1000) tempInterval -= 1000 },
                            enabled = tempInterval > 1000
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
                                text = stringResource(R.string.interval_ms_format, tempInterval, tempInterval / 1000f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        IconButton(
                            onClick = { if (tempInterval < 60000) tempInterval += 1000 },
                            enabled = tempInterval < 60000
                        ) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                        }
                    }

                    Slider(
                        value = tempInterval.toFloat(),
                        onValueChange = { tempInterval = it.roundToInt().coerceIn(1000, 60000) },
                        valueRange = 1000f..60000f,
                        steps = 58
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            isEnabled = true
                            tempInterval = 5000
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.default_5000),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = { onConfirm(if (isEnabled) tempInterval else 0) }) { Text(stringResource(R.string.ok)) }
                }
            }
        },
        dismissButton = null
    )

    if (showManualInput) {
        ManualStatusIntervalDialog(
            currentInterval = tempInterval,
            onDismiss = { showManualInput = false },
            onConfirm = { newInterval ->
                if (newInterval <= 0) {
                    isEnabled = false
                } else {
                    isEnabled = true
                    tempInterval = newInterval.coerceIn(1000, 60000)
                }
                showManualInput = false
            }
        )
    }
}

@Composable
fun ManualStatusIntervalDialog(
    currentInterval: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf(currentInterval.toString()) }

    AlertDialog(
        onDismissRequest = {
            val interval = textValue.toIntOrNull() ?: currentInterval
            onConfirm(interval)
        },
        title = { Text(stringResource(R.string.enter_interval_title)) },
        text = {
            Column {
                Text(stringResource(R.string.enter_interval_manual))
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
                    val interval = textValue.toIntOrNull() ?: currentInterval
                    onConfirm(interval)
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
