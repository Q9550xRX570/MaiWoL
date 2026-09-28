package com.mai.wol.ui.lock

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.security.MessageDigest

import com.mai.wol.R
import com.mai.wol.MainActivity

@Composable
fun LockIconWithCenterDot(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        modifier = modifier
            .size(86.dp)
            .border(1.5.dp, tint.copy(alpha = 0.35f), CircleShape)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(width = 30.dp, height = 40.dp)) {
                val w = size.width
                val h = size.height
                val strokeWidth = 3.dp.toPx()

                val bodyW = w * 0.90f
                val bodyH = h * 0.54f
                val bodyX = (w - bodyW) / 2
                val bodyY = h * 0.42f
                val cornerRadius = 6.dp.toPx()

                val shackleW = w * 0.62f
                val shackleX = (w - shackleW) / 2
                val shackleTop = h * 0.06f
                val archRadius = shackleW / 2

                val shacklePath = Path().apply {
                    moveTo(shackleX, bodyY)
                    lineTo(shackleX, shackleTop + archRadius)
                    arcTo(
                        rect = Rect(
                            left = shackleX,
                            top = shackleTop,
                            right = shackleX + shackleW,
                            bottom = shackleTop + shackleW
                        ),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 180f,
                        forceMoveTo = false
                    )
                    lineTo(shackleX + shackleW, bodyY)
                }

                drawPath(
                    path = shacklePath,
                    color = tint,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )

                drawRoundRect(
                    color = tint,
                    topLeft = Offset(bodyX, bodyY),
                    size = Size(bodyW, bodyH),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                    style = Stroke(width = strokeWidth)
                )

                val dotRadius = 3.2.dp.toPx()
                val dotCenterY = bodyY + (bodyH * 0.5f)
                drawCircle(
                    color = tint,
                    radius = dotRadius,
                    center = Offset(w / 2, dotCenterY)
                )
            }
        }
    }
}

@Composable
fun AppLockScreen(onSuccess: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember { context.getSharedPreferences("wol_settings", Context.MODE_PRIVATE) }

    val pinLength = remember { prefs.getInt("security_pin_length", 4) }
    val savedPinHash = remember { prefs.getString("security_pin_hash", "") ?: "" }
    val biometricEnabled = remember { prefs.getBoolean("security_biometric_enabled", true) }

    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    var cancellationSignal by remember { mutableStateOf<CancellationSignal?>(null) }

    fun triggerBiometrics() {
        if (biometricEnabled && activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cancellationSignal?.cancel()
            cancellationSignal = showBiometricPromptSafe(
                activity = activity,
                title = context.getString(R.string.app_name),
                subtitle = context.getString(R.string.biometric_prompt_subtitle),
                onSuccess = onSuccess,
                onError = { err ->
                    if (!err.isNullOrBlank()) {
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(250)
        triggerBiometrics()
    }

    DisposableEffect(Unit) {
        onDispose {
            cancellationSignal?.cancel()
        }
    }

    fun submitPin() {
        if (enteredPin.length == pinLength) {
            if (hashPin(enteredPin) == savedPinHash) {
                onSuccess()
            } else {
                isError = true
                enteredPin = ""
                Toast.makeText(context, context.getString(R.string.incorrect_pin), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(enteredPin) {
        if (enteredPin.length == pinLength) {
            submitPin()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LockIconWithCenterDot(
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = stringResource(R.string.enter_pin_to_unlock),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(top = 10.dp, bottom = 12.dp)
                ) {
                    for (i in 0 until pinLength) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val keypadRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("DEL", "0", "CHECK")
                )

                keypadRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { key ->
                            when (key) {
                                "DEL" -> {
                                    IconButton(
                                        onClick = {
                                            if (enteredPin.isNotEmpty()) {
                                                enteredPin = enteredPin.dropLast(1)
                                                isError = false
                                            }
                                        },
                                        modifier = Modifier.size(68.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Backspace,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                                "CHECK" -> {
                                    IconButton(
                                        onClick = { submitPin() },
                                        modifier = Modifier.size(68.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (enteredPin.length == pinLength) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                if (enteredPin.length < pinLength) {
                                                    enteredPin += key
                                                    isError = false
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 28.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (biometricEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { triggerBiometrics() }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.use_biometrics),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun AppLockSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val prefs = remember { context.getSharedPreferences("wol_settings", Context.MODE_PRIVATE) }

    val savedPinHash = remember { prefs.getString("security_pin_hash", "") ?: "" }
    val savedPinLength = remember { prefs.getInt("security_pin_length", 4) }
    val hasExistingPin = savedPinHash.isNotBlank()

    var lockEnabled by remember { mutableStateOf(prefs.getBoolean("app_lock_enabled", false)) }
    var isChangingPin by remember { mutableStateOf(!hasExistingPin) }

    var selectedPinLength by remember { mutableIntStateOf(savedPinLength) }
    var biometricEnabled by remember { mutableStateOf(prefs.getBoolean("security_biometric_enabled", true)) }
    var widgetLockEnabled by remember { mutableStateOf(prefs.getBoolean("widget_lock_enabled", false)) }
    var tileLockEnabled by remember { mutableStateOf(prefs.getBoolean("lock_tile_enabled", false)) }

    var pinInput by remember { mutableStateOf("") }
    var pinConfirmInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showConfirmSaveDialog by remember { mutableStateOf(false) }

    fun performSave() {
        if (lockEnabled) {
            val finalPinHash = if (isChangingPin) hashPin(pinInput) else savedPinHash
            val finalPinLength = if (isChangingPin) selectedPinLength else savedPinLength

            prefs.edit()
                .putBoolean("app_lock_enabled", true)
                .putInt("security_pin_length", finalPinLength)
                .putString("security_pin_hash", finalPinHash)
                .putBoolean("security_biometric_enabled", biometricEnabled)
                .putBoolean("widget_lock_enabled", widgetLockEnabled)
                .putBoolean("lock_tile_enabled", tileLockEnabled)
                .apply()

            activity?.updateWindowSecurity(true)
            activity?.isAppUnlocked?.value = true
            Toast.makeText(context, context.getString(R.string.pin_saved), Toast.LENGTH_SHORT).show()
        } else {
            prefs.edit()
                .putBoolean("app_lock_enabled", false)
                .putBoolean("widget_lock_enabled", false)
                .putBoolean("lock_tile_enabled", false)
                .apply()
            activity?.updateWindowSecurity(false)
            activity?.isAppUnlocked?.value = true
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.app_lock_settings))
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
                    Text(stringResource(R.string.enable_app_lock), style = MaterialTheme.typography.titleSmall)
                    Switch(
                        checked = lockEnabled,
                        onCheckedChange = { lockEnabled = it }
                    )
                }

                if (lockEnabled) {
                    HorizontalDivider()

                    if (hasExistingPin && !isChangingPin) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.pin_active_format, savedPinLength),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(onClick = {
                                    isChangingPin = true
                                    pinInput = ""
                                    pinConfirmInput = ""
                                }) {
                                    Text(stringResource(R.string.change_pin))
                                }
                            }
                        }
                    }

                    if (isChangingPin) {
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
                                        pinConfirmInput = ""
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
                            value = pinConfirmInput,
                            onValueChange = { if (it.length <= selectedPinLength && it.all { c -> c.isDigit() }) pinConfirmInput = it },
                            label = { Text(stringResource(R.string.confirm_pin)) },
                            placeholder = { Text("•".repeat(selectedPinLength)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.biometric_auth), style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = stringResource(R.string.biometric_auth_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = { biometricEnabled = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.lock_widgets), style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = stringResource(R.string.lock_widgets_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = widgetLockEnabled,
                            onCheckedChange = { widgetLockEnabled = it }
                        )
                    }

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
                        }
                        Switch(
                            checked = tileLockEnabled,
                            onCheckedChange = { tileLockEnabled = it }
                        )
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (lockEnabled) {
                        if (isChangingPin) {
                            if (pinInput.length != selectedPinLength) {
                                errorMessage = context.getString(R.string.pin_required_warning)
                                return@TextButton
                            }
                            if (pinInput != pinConfirmInput) {
                                errorMessage = context.getString(R.string.pin_mismatch)
                                return@TextButton
                            }
                            showConfirmSaveDialog = true
                        } else {
                            performSave()
                        }
                    } else {
                        performSave()
                    }
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

    if (showConfirmSaveDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmSaveDialog = false },
            title = { Text(stringResource(R.string.confirm_pin_save_title)) },
            text = { Text(stringResource(R.string.confirm_pin_save_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmSaveDialog = false
                        performSave()
                    }
                ) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmSaveDialog = false }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }
}

private fun hashPin(pin: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun showBiometricPromptSafe(
    activity: Activity,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onError: (String?) -> Unit
): CancellationSignal? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        onError("Biyometrik doğrulama desteklenmiyor")
        return null
    }
    return try {
        val cancellationSignal = CancellationSignal()
        val executor = activity.mainExecutor

        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButton(activity.getString(R.string.cancel), executor) { _, _ ->
                onError(null)
            }
            .build()

        prompt.authenticate(
            cancellationSignal,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    super.onAuthenticationSucceeded(result)
                    activity.runOnUiThread { onSuccess() }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED) {
                        activity.runOnUiThread { onError(errString?.toString()) }
                    } else {
                        activity.runOnUiThread { onError(null) }
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            }
        )
        cancellationSignal
    } catch (e: Throwable) {
        e.printStackTrace()
        onError(e.localizedMessage)
        null
    }
}
