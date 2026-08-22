package com.mai.wol

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.PowerManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mai.wol.data.AppDatabase
import com.mai.wol.network.WolManager
import com.mai.wol.ui.theme.MaiWoLTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale

class WidgetUnlockActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("wol_settings", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_language", "") ?: ""
        val locale = if (lang.isEmpty()) Locale.getDefault() else Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val targetDeviceId = intent.getLongExtra("device_id", -1L)
        if (targetDeviceId == -1L) {
            finish()
            return
        }

        val prefs = getSharedPreferences("wol_settings", Context.MODE_PRIVATE)
        val isAppLockEnabled = prefs.getBoolean("app_lock_enabled", false)
        val isWidgetLockEnabled = prefs.getBoolean("widget_lock_enabled", false)

        if (!isAppLockEnabled || !isWidgetLockEnabled) {
            wakeDeviceAndFinish(targetDeviceId)
            return
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val appThemeSetting = prefs.getString("app_theme", "system") ?: "system"
        val systemInDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDark = when (appThemeSetting) {
            "light" -> false
            "dark" -> true
            else -> systemInDark
        }
        val bgColor = if (isDark) 0xFF141218.toInt() else 0xFFFEF7FF.toInt()
        window.setBackgroundDrawable(ColorDrawable(bgColor))

        enableEdgeToEdge()

        setContent {
            var targetDeviceName by remember { mutableStateOf("") }

            LaunchedEffect(targetDeviceId) {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val dev = db.deviceDao().getDeviceById(targetDeviceId)
                    withContext(Dispatchers.Main) {
                        targetDeviceName = dev?.name ?: "Cihaz"
                    }
                }
            }

            MaiWoLTheme(darkTheme = isDark) {
                WidgetLockScreenContent(
                    deviceName = targetDeviceName,
                    onSuccess = {
                        wakeDeviceAndFinish(targetDeviceId)
                    }
                )
            }
        }
    }

    private fun wakeDeviceAndFinish(deviceId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            var wakeLock: PowerManager.WakeLock? = null
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "MaiWoL:WidgetUnlockWakeLock"
                )?.apply {
                    acquire(4000)
                }

                val db = AppDatabase.getDatabase(applicationContext)
                val device = db.deviceDao().getDeviceById(deviceId)

                if (device != null) {
                    val prefs = getSharedPreferences("wol_settings", Context.MODE_PRIVATE)
                    val packetCount = prefs.getInt("packet_count", 3)

                    val result = WolManager.sendMagicPacket(
                        macAddress = device.macAddress,
                        ipAddress = device.ipAddress,
                        localIp = device.localIp,
                        port = device.port,
                        secureOnPassword = device.secureOnPassword,
                        packetCount = packetCount
                    )

                    withContext(Dispatchers.Main) {
                        result.fold(
                            onSuccess = {
                                val msg = getString(R.string.packet_sent_success, device.name, packetCount)
                                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                            },
                            onFailure = {
                                val msg = getString(R.string.packet_sent_error, it.localizedMessage ?: "")
                                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }
    }
}

@Composable
fun WidgetLockScreenContent(
    deviceName: String,
    onSuccess: () -> Unit
) {
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
            val promptSubtitle = if (deviceName.isNotBlank()) {
                context.getString(R.string.widget_unlock_title_format, deviceName)
            } else {
                context.getString(R.string.biometric_prompt_subtitle)
            }

            cancellationSignal = showWidgetBiometricPrompt(
                activity = activity,
                title = context.getString(R.string.app_name),
                subtitle = promptSubtitle,
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
            if (hashPinWidget(enteredPin) == savedPinHash) {
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
                if (deviceName.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.widget_unlock_title_format, deviceName),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                LockIconWithCenterDot(tint = MaterialTheme.colorScheme.primary)

                Spacer(modifier = Modifier.height(4.dp))

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

private fun hashPinWidget(pin: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun showWidgetBiometricPrompt(
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