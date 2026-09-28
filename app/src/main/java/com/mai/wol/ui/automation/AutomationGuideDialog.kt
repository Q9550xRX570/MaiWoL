package com.mai.wol.ui.automation

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.mai.wol.R

@Composable
fun AutomationGuideDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val currentLocale = context.resources.configuration.locales[0]
    val isEnglish = currentLocale.language == "en"

    val sampleDeviceName = if (isEnglish) "Computer" else "Bilgisayar"

    val broadcastActionCode = "com.mai.wol.ACTION_WAKE_DEVICE"
    val method1Code = "device_name: $sampleDeviceName"
    val method2Code = "mac_address: AA:BB:CC:DD:EE:FF"
    val adbCommandCode = "am broadcast -a com.mai.wol.ACTION_WAKE_DEVICE -p com.mai.wol --es device_name \"$sampleDeviceName\""

    fun copyCode(code: String) {
        clipboardManager.setText(AnnotatedString(code))
        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.automation_guide_title))
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.automation_guide_desc),
                    style = MaterialTheme.typography.bodyMedium
                )

                CodeSnippetCard(
                    title = stringResource(R.string.guide_broadcast_action),
                    code = broadcastActionCode,
                    onCopy = { copyCode(broadcastActionCode) }
                )

                CodeSnippetCard(
                    title = stringResource(R.string.guide_method_name),
                    code = method1Code,
                    onCopy = { copyCode(method1Code) }
                )

                CodeSnippetCard(
                    title = stringResource(R.string.guide_method_mac),
                    code = method2Code,
                    onCopy = { copyCode(method2Code) }
                )

                CodeSnippetCard(
                    title = stringResource(R.string.guide_adb_command),
                    code = adbCommandCode,
                    onCopy = { copyCode(adbCommandCode) }
                )
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
fun CodeSnippetCard(
    title: String,
    code: String,
    onCopy: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.copied),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
