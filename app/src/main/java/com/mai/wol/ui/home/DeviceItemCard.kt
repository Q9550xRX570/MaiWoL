package com.mai.wol.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mai.wol.data.DeviceEntity
import com.mai.wol.network.DeviceStatus
import com.mai.wol.network.StatusReason
import com.mai.wol.network.StatusResult

import com.mai.wol.R

@Composable
fun DeviceItemCard(
    device: DeviceEntity,
    status: StatusResult,
    showStatusBadge: Boolean,
    macDisplay: String,
    localIpDisplay: String,
    wanIpDisplay: String,
    portDisplay: String,
    onRefreshStatus: () -> Unit,
    onSendWol: () -> Unit,
    onSendShutdown: () -> Unit,
    onChangeGroupRequest: () -> Unit,
    onEditRequest: () -> Unit,
    onScheduleRequest: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isShutdownConfigured = device.shutdownType != "NONE"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (device.groupName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = device.groupName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    if (showStatusBadge) {
                        Spacer(modifier = Modifier.height(2.dp))
                        DeviceStatusBadge(
                            result = status,
                            onRefresh = onRefreshStatus
                        )
                    }
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.change_group)) },
                            onClick = {
                                showMenu = false
                                onChangeGroupRequest()
                            },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.schedules)) },
                            onClick = {
                                showMenu = false
                                onScheduleRequest()
                            },
                            leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = {
                                showMenu = false
                                onEditRequest()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDeleteRequest()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }

            val hasDetails = macDisplay != "hide" || (wanIpDisplay != "hide" && device.ipAddress.isNotBlank()) || localIpDisplay != "hide" || portDisplay != "hide"

            if (hasDetails) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (macDisplay != "hide") {
                        val macText = if (macDisplay == "mask") maskMac(device.macAddress) else device.macAddress
                        Text(
                            text = macText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (wanIpDisplay != "hide" && device.ipAddress.isNotBlank()) {
                        val wanText = if (wanIpDisplay == "mask") maskWan(device.ipAddress) else device.ipAddress
                        Text(
                            text = wanText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val ipPortText = buildString {
                        if (localIpDisplay != "hide" && device.localIp.isNotBlank()) {
                            val ipText = if (localIpDisplay == "mask") maskIp(device.localIp) else device.localIp
                            append("$ipText · ")
                        }
                        if (portDisplay != "hide") {
                            val pText = if (portDisplay == "mask") "***" else device.port.toString()
                            append("Port $pText")
                        }
                    }

                    if (ipPortText.isNotBlank()) {
                        Text(
                            text = ipPortText.trimEnd(' ', '·'),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isShutdownConfigured) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSendWol,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.wake_up))
                    }

                    OutlinedButton(
                        onClick = onSendShutdown,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.shutdown))
                    }
                }
            } else {
                Button(
                    onClick = onSendWol,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.wake_up))
                }
            }
        }
    }
}

fun maskMac(mac: String): String {
    if (mac.length < 5) return "••••••••••••"
    return "${mac.take(5)}:••:••:••"
}

fun maskIp(ip: String): String {
    val parts = ip.split(".")
    if (parts.size == 4) {
        return "${parts[0]}.${parts[1]}.***.***"
    }
    return "***.***.***.***"
}

fun maskWan(wan: String): String {
    if (wan.contains(".")) {
        val domain = wan.substringAfterLast(".", "")
        return "*****.***.$domain"
    }
    return "********"
}

@Composable
fun statusReasonText(result: StatusResult): String? = when (result.reason) {
    StatusReason.SSH_BANNER -> stringResource(R.string.reason_ssh_banner, result.detail)
    StatusReason.SERVICE_PORT -> stringResource(R.string.reason_service_port, result.detail)
    StatusReason.PING_REPLY -> stringResource(R.string.reason_ping_reply)
    StatusReason.WAN_PORT -> stringResource(R.string.reason_wan_port, result.detail)
    StatusReason.SAME_SUBNET_SILENT -> stringResource(R.string.reason_same_subnet_silent)
    StatusReason.NO_ROUTE -> stringResource(R.string.reason_no_route)
    StatusReason.NOT_CHECKED -> null
}

@Composable
fun DeviceStatusBadge(
    result: StatusResult,
    onRefresh: () -> Unit
) {
    val (bgColor, textColor, text) = when (result.status) {
        DeviceStatus.ONLINE -> Triple(
            Color(0xFF4CAF50).copy(alpha = 0.15f),
            Color(0xFF2E7D32),
            stringResource(R.string.status_online)
        )

        DeviceStatus.STANDBY -> Triple(
            Color(0xFFFFB300).copy(alpha = 0.15f),
            Color(0xFFE65100),
            stringResource(R.string.status_standby)
        )

        DeviceStatus.UNREACHABLE -> Triple(
            Color(0xFFE57373).copy(alpha = 0.15f),
            Color(0xFFC62828),
            stringResource(R.string.status_unreachable)
        )

        DeviceStatus.CHECKING -> Triple(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.primary,
            stringResource(R.string.status_checking)
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onRefresh() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = textColor
            )
            statusReasonText(result)?.let { reason ->
                Text(
                    text = " · $reason",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
