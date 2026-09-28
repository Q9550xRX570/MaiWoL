package com.mai.wol

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mai.wol.data.AppDatabase
import com.mai.wol.data.BackupData
import com.mai.wol.data.BackupManager
import com.mai.wol.ui.home.HomeScreen
import com.mai.wol.ui.lock.AppLockScreen
import com.mai.wol.ui.theme.MaiWoLTheme
import rikka.shizuku.Shizuku
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {
        private const val SHIZUKU_REQ_CODE = 1001
    }

    val isAppUnlocked = mutableStateOf(true)
    private var pendingImportData = mutableStateOf<BackupData?>(null)
    private var pendingEncryptedRawJson = mutableStateOf<String?>(null)

    private val viewModel: MainViewModel by viewModels {
        val db = AppDatabase.getDatabase(applicationContext)
        val prefs = applicationContext.getSharedPreferences("wol_settings", Context.MODE_PRIVATE)
        MainViewModelFactory(db.deviceDao(), db.scheduleDao(), prefs, applicationContext)
    }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQ_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                viewModel.setShizukuEnabled(true)
            } else {
                viewModel.setShizukuEnabled(false)
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("wol_settings", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_language", "") ?: ""
        val locale = if (lang.isEmpty()) Locale.getDefault() else Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    private fun applyWindowTheme(isDark: Boolean) {
        val bgColor = if (isDark) 0xFF141218.toInt() else 0xFFFEF7FF.toInt()
        window.setBackgroundDrawable(ColorDrawable(bgColor))
        window.decorView.setBackgroundColor(bgColor)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Exception) {}

        val prefs = getSharedPreferences("wol_settings", Context.MODE_PRIVATE)
        val isAppLockEnabled = prefs.getBoolean("app_lock_enabled", false)
        isAppUnlocked.value = !isAppLockEnabled
        updateWindowSecurity(isAppLockEnabled)

        val appThemeSetting = prefs.getString("app_theme", "system") ?: "system"
        val systemInDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDark = when (appThemeSetting) {
            "light" -> false
            "dark" -> true
            else -> systemInDark
        }
        applyWindowTheme(isDark)

        handleIncomingIntent(intent)

        enableEdgeToEdge()
        setContent {
            var currentThemeSetting by remember { mutableStateOf(prefs.getString("app_theme", "system") ?: "system") }
            val systemInDarkTheme = isSystemInDarkTheme()

            val isDarkTheme = when (currentThemeSetting) {
                "light" -> false
                "dark" -> true
                else -> systemInDarkTheme
            }

            LaunchedEffect(isDarkTheme) {
                applyWindowTheme(isDarkTheme)
            }

            val unlocked by isAppUnlocked
            val incomingBackup by pendingImportData
            val encryptedJson by pendingEncryptedRawJson

            MaiWoLTheme(darkTheme = isDarkTheme) {
                if (!unlocked) {
                    AppLockScreen(
                        onSuccess = { isAppUnlocked.value = true }
                    )
                } else {
                    HomeScreen(
                        viewModel = viewModel,
                        onRequestShizukuPermission = { requestShizukuPermission() },
                        currentTheme = currentThemeSetting,
                        onThemeChange = { newTheme ->
                            prefs.edit().putString("app_theme", newTheme).apply()
                            currentThemeSetting = newTheme
                        }
                    )

                    encryptedJson?.let { rawJson ->
                        var pinInput by remember { mutableStateOf("") }
                        var isPinError by remember { mutableStateOf(false) }

                        AlertDialog(
                            onDismissRequest = { pendingEncryptedRawJson.value = null },
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
                                            pendingEncryptedRawJson.value = null
                                            pendingImportData.value = decrypted
                                        } else {
                                            isPinError = true
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.ok))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { pendingEncryptedRawJson.value = null }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    }

                    incomingBackup?.let { backup ->
                        AlertDialog(
                            onDismissRequest = { pendingImportData.value = null },
                            title = { Text(stringResource(R.string.import_confirm_title)) },
                            text = {
                                Text(stringResource(R.string.import_confirm_desc, backup.devices.size, backup.schedules.size))
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.importBackup(
                                            context = this@MainActivity,
                                            backupData = backup,
                                            onSuccess = { count ->
                                                Toast.makeText(this@MainActivity, getString(R.string.import_success, count), Toast.LENGTH_SHORT).show()
                                                pendingImportData.value = null
                                            },
                                            onError = { err ->
                                                Toast.makeText(this@MainActivity, err, Toast.LENGTH_SHORT).show()
                                                pendingImportData.value = null
                                            }
                                        )
                                    }
                                ) {
                                    Text(stringResource(R.string.yes))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { pendingImportData.value = null }) {
                                    Text(stringResource(R.string.no))
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW) {
            val rawJson = BackupManager.readStringFromUri(this, uri)
            if (rawJson != null) {
                if (BackupManager.isFileEncrypted(rawJson)) {
                    pendingEncryptedRawJson.value = rawJson
                } else {
                    val backup = BackupManager.parseBackupJson(rawJson)
                    if (backup != null && backup.devices.isNotEmpty()) {
                        pendingImportData.value = backup
                    } else {
                        Toast.makeText(this, getString(R.string.invalid_backup_file), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val prefs = getSharedPreferences("wol_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("app_lock_enabled", false)) {
            isAppUnlocked.value = false
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("wol_settings", Context.MODE_PRIVATE)
        val isAppLockEnabled = prefs.getBoolean("app_lock_enabled", false)
        updateWindowSecurity(isAppLockEnabled)

        val appThemeSetting = prefs.getString("app_theme", "system") ?: "system"
        val systemInDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDark = when (appThemeSetting) {
            "light" -> false
            "dark" -> true
            else -> systemInDark
        }
        applyWindowTheme(isDark)
    }

    fun updateWindowSecurity(isLockEnabled: Boolean) {
        runOnUiThread {
            if (isLockEnabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Exception) {}
    }

    private fun requestShizukuPermission() {
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.requestPermission(SHIZUKU_REQ_CODE)
            } else {
                viewModel.setShizukuEnabled(false)
                Toast.makeText(this, getString(R.string.shizuku_not_running), Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            viewModel.setShizukuEnabled(false)
            Toast.makeText(this, getString(R.string.shizuku_not_running), Toast.LENGTH_SHORT).show()
        }
    }
}
