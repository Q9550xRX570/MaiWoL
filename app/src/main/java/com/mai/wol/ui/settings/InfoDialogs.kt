package com.mai.wol.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.zip.ZipFile

import com.mai.wol.R

@Composable
fun ShizukuDialog(
    isEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit
) {
    var tempEnabled by remember { mutableStateOf(isEnabled) }
    val statusText = if (tempEnabled) stringResource(R.string.enabled) else stringResource(R.string.disabled)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shizuku_integration)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.shizuku_description),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.shizuku_status, statusText),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (tempEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                    Switch(
                        checked = tempEnabled,
                        onCheckedChange = { tempEnabled = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(tempEnabled)
                    onDismiss()
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

@Composable
fun StatisticsDialog(
    totalWakeUps: Int,
    totalPacketsSent: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val (appVersionInfo, recommendationText) = remember { getAppVersionAndRecommendation(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.statistics)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.total_wake_ups),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "$totalWakeUps",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.total_packets_sent),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "$totalPacketsSent",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.app_version),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = appVersionInfo,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (recommendationText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = recommendationText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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

fun getAppVersionAndRecommendation(context: Context): Pair<String, String> {
    val versionName = try {
        val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        pInfo.versionName ?: "2.2.4"
    } catch (_: Exception) {
        "2.2.4"
    }

    val rawArch = detectApkArchitecture(context)
    val formattedArch = when (rawArch) {
        "arm64-v8a" -> "ARM64 v8A"
        "armeabi-v7a" -> "ARMeabi v7A"
        "x86_64" -> "x86_64"
        "x86" -> "x86"
        "universal" -> context.getString(R.string.universal_arch)
        else -> rawArch
    }

    val primaryAbi = if (Build.SUPPORTED_ABIS.isNotEmpty()) Build.SUPPORTED_ABIS[0].lowercase() else ""
    val isNativeArm64Device = primaryAbi.contains("arm64")
    val isNotArm64Build = rawArch != "arm64-v8a"

    val recommendation = if (isNativeArm64Device && isNotArm64Build) {
        context.getString(R.string.arm64_recommended)
    } else {
        ""
    }

    return Pair("v$versionName - $formattedArch", recommendation)
}

private fun detectApkArchitecture(context: Context): String {
    try {
        val apkFile = ZipFile(context.applicationInfo.sourceDir)
        val abiFolders = mutableSetOf<String>()
        val entries = apkFile.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name.startsWith("lib/")) {
                val parts = entry.name.split("/")
                if (parts.size >= 2 && parts[1].isNotBlank()) {
                    abiFolders.add(parts[1].lowercase())
                }
            }
        }
        apkFile.close()

        if (abiFolders.size > 1) {
            return "universal"
        } else if (abiFolders.size == 1) {
            val abi = abiFolders.first()
            if (abi.contains("arm64")) return "arm64-v8a"
            if (abi.contains("armeabi")) return "armeabi-v7a"
            if (abi.contains("x86_64")) return "x86_64"
            if (abi.contains("x86")) return "x86"
            return abi
        }
    } catch (_: Exception) {}

    try {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        if (!nativeDir.isNullOrBlank()) {
            val lower = nativeDir.lowercase()
            if (lower.contains("arm64")) return "arm64-v8a"
            if (lower.contains("x86_64")) return "x86_64"
            if (lower.contains("arm")) return "armeabi-v7a"
            if (lower.contains("x86")) return "x86"
        }
    } catch (_: Exception) {}

    if (Build.SUPPORTED_ABIS.isNotEmpty()) {
        val primaryAbi = Build.SUPPORTED_ABIS[0].lowercase()
        if (primaryAbi.contains("arm64")) return "arm64-v8a"
        if (primaryAbi.contains("armeabi")) return "armeabi-v7a"
        if (primaryAbi.contains("x86_64")) return "x86_64"
        if (primaryAbi.contains("x86")) return "x86"
        return primaryAbi
    }

    return "universal"
}
