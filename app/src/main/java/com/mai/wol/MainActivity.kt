package com.mai.wol

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mai.wol.automation.AlarmScheduler
import com.mai.wol.data.AppDatabase
import com.mai.wol.data.DeviceEntity
import com.mai.wol.data.ScheduleEntity
import com.mai.wol.network.DeviceStatus
import com.mai.wol.network.NetworkScanner
import com.mai.wol.network.ScannedDevice
import com.mai.wol.ui.theme.MaiWoLTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import android.content.Intent
import android.net.Uri

class MainActivity : ComponentActivity() {

    companion object {
        private const val SHIZUKU_REQ_CODE = 1001
    }

    val isAppUnlocked = mutableStateOf(true)

    private val viewModel: MainViewModel by viewModels {
        val db = AppDatabase.getDatabase(applicationContext)
        val prefs = applicationContext.getSharedPreferences("wol_settings", Context.MODE_PRIVATE)
        MainViewModelFactory(db.deviceDao(), db.scheduleDao(), prefs)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onRequestShizukuPermission: () -> Unit,
    currentTheme: String,
    onThemeChange: (String) -> Unit
) {
    val devices by viewModel.devices.collectAsState()
    val packetCount by viewModel.packetCount.collectAsState()
    val statusCheckInterval by viewModel.statusCheckInterval.collectAsState()
    val totalWakeUps by viewModel.totalWakeUps.collectAsState()
    val totalPacketsSent by viewModel.totalPacketsSent.collectAsState()
    val isShizukuEnabled by viewModel.isShizukuEnabled.collectAsState()
    val deviceStatuses by viewModel.deviceStatuses.collectAsState()

    val cardMacDisplay by viewModel.cardMacDisplay.collectAsState()
    val cardLocalIpDisplay by viewModel.cardLocalIpDisplay.collectAsState()
    val cardWanIpDisplay by viewModel.cardWanIpDisplay.collectAsState()
    val cardPortDisplay by viewModel.cardPortDisplay.collectAsState()

    var showAdvancedScreen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("wol_settings", Context.MODE_PRIVATE) }
    val appLanguage = remember { prefs.getString("app_language", "") ?: "" }

    LaunchedEffect(devices, statusCheckInterval) {
        if (statusCheckInterval > 0) {
            while (isActive) {
                if (devices.isNotEmpty()) {
                    viewModel.checkAllDevicesStatus(context, devices)
                }
                delay(statusCheckInterval.toLong().coerceAtLeast(1000L))
            }
        }
    }

    if (showAdvancedScreen) {
        BackHandler {
            showAdvancedScreen = false
        }
        AdvancedFeaturesScreen(
            viewModel = viewModel,
            isShizukuEnabled = isShizukuEnabled,
            onRequestShizukuPermission = onRequestShizukuPermission,
            onSetShizukuEnabled = { viewModel.setShizukuEnabled(it) },
            onBack = { showAdvancedScreen = false }
        )
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }

    var currentList by remember(devices) { mutableStateOf(devices) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumulatedOffset by remember { mutableFloatStateOf(0f) }

    val focusManager = LocalFocusManager.current
    val isImeVisible = WindowInsets.isImeVisible

    LaunchedEffect(isImeVisible) {
        if (!isImeVisible) {
            focusManager.clearFocus()
        }
    }

    BackHandler(enabled = isSearchFocused) {
        focusManager.clearFocus()
    }

    val filteredDevices = remember(currentList, searchQuery) {
        val trimmedQuery = searchQuery.trim()
        if (trimmedQuery.isEmpty()) {
            currentList
        } else {
            currentList.filter { device ->
                device.name.contains(trimmedQuery, ignoreCase = true) ||
                        device.macAddress.contains(trimmedQuery, ignoreCase = true) ||
                        device.localIp.contains(trimmedQuery, ignoreCase = true) ||
                        device.ipAddress.contains(trimmedQuery, ignoreCase = true)
            }
        }
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            currentList = currentList.toMutableList().apply {
                val item = removeAt(fromIndex)
                add(toIndex, item)
            }
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var deviceToEdit by remember { mutableStateOf<DeviceEntity?>(null) }
    var deviceToDelete by remember { mutableStateOf<DeviceEntity?>(null) }
    var deviceToSchedule by remember { mutableStateOf<DeviceEntity?>(null) }
    var showPacketCountSettingsDialog by remember { mutableStateOf(false) }
    var showStatusIntervalDialog by remember { mutableStateOf(false) }
    var showCardCustomizationDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showStatisticsDialog by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // SADECE PROJEDE VAR OLAN 3 DİL SEÇENEĞİ
    val currentLangText = when (appLanguage) {
        "tr" -> stringResource(R.string.turkish)
        "en" -> stringResource(R.string.english)
        else -> stringResource(R.string.system_default)
    }

    val currentThemeText = when (currentTheme) {
        "light" -> stringResource(R.string.theme_light)
        "dark" -> stringResource(R.string.theme_dark)
        else -> stringResource(R.string.theme_system_default)
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings),
                                modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 12.dp),
                                style = MaterialTheme.typography.titleLarge
                            )

                            // 0. RESMİ WEB SİTESİ (maiwol.com)
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.website)) },
                                supportingContent = {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.website_description),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "maiwol.com",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .clickable {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maiwol.com"))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                            )

                            // 1. SIHIRLI PAKET SAYISI
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.wol_packet_count)) },
                                supportingContent = {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.packet_count_description),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(R.string.packets_format, packetCount),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .clickable { showPacketCountSettingsDialog = true }
                            )

                            // 2. DURUM YOKLAMA SIKLIĞI
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.status_check_interval)) },
                                supportingContent = {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.status_check_interval_desc),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (statusCheckInterval == 0) stringResource(R.string.disabled)
                                            else stringResource(R.string.interval_ms_format, statusCheckInterval, statusCheckInterval / 1000f),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (statusCheckInterval == 0) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .clickable { showStatusIntervalDialog = true }
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            // KART ÖZELLEŞTİRME
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCardCustomizationDialog = true }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.card_customization),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.card_customization_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // UYGULAMA TEMASI
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showThemeDialog = true }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = PaletteIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.app_theme),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.app_theme_description),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = currentThemeText,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // UYGULAMA DİLİ
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showLanguageDialog = true }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = LanguageIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.app_language),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.app_language_description),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = currentLangText,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // İSTATİSTİKLER
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showStatisticsDialog = true }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.statistics),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }

                            // GELİŞMİŞ ÖZELLİKLER
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch { drawerState.close() }
                                        showAdvancedScreen = true
                                    }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = DnsIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.advanced_features),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.advanced_features_description),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.app_name)) },
                            actions = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_device))
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (devices.isNotEmpty()) {
                            val searchInteractionSource = remember { MutableInteractionSource() }

                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .height(48.dp)
                                    .onFocusChanged { isSearchFocused = it.isFocused },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = { focusManager.clearFocus() }
                                ),
                                interactionSource = searchInteractionSource,
                                decorationBox = { innerTextField ->
                                    OutlinedTextFieldDefaults.DecorationBox(
                                        value = searchQuery,
                                        innerTextField = innerTextField,
                                        enabled = true,
                                        singleLine = true,
                                        visualTransformation = VisualTransformation.None,
                                        interactionSource = searchInteractionSource,
                                        placeholder = {
                                            Text(
                                                text = stringResource(R.string.search_devices),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { searchQuery = "" }) {
                                                    Icon(
                                                        imageVector = Icons.Default.Clear,
                                                        contentDescription = null
                                                    )
                                                }
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        container = {
                                            OutlinedTextFieldDefaults.ContainerBox(
                                                enabled = true,
                                                isError = false,
                                                interactionSource = searchInteractionSource,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                    unfocusedBorderColor = Color.Transparent,
                                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                                                ),
                                                shape = CircleShape
                                            )
                                        }
                                    )
                                }
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            if (devices.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.no_devices_yet),
                                    modifier = Modifier.align(Alignment.Center),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            } else if (filteredDevices.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.no_results_found),
                                    modifier = Modifier.align(Alignment.Center),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            } else {
                                val density = context.resources.displayMetrics.density
                                val swapThreshold = 130f * density

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(filteredDevices, key = { _, item -> item.id }) { index, device ->
                                        val isDragging = (draggedIndex == index)
                                        val canDrag = searchQuery.isBlank()

                                        val elevation by animateDpAsState(
                                            targetValue = if (isDragging) 8.dp else 0.dp,
                                            label = "elevation"
                                        )
                                        val scale by animateFloatAsState(
                                            targetValue = if (isDragging) 1.03f else 1.0f,
                                            label = "scale"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .zIndex(if (isDragging) 10f else 1f)
                                                .graphicsLayer {
                                                    scaleX = scale
                                                    scaleY = scale
                                                    translationY = if (isDragging) dragAccumulatedOffset else 0f
                                                }
                                                .shadow(elevation, shape = MaterialTheme.shapes.medium)
                                                .then(
                                                    if (canDrag) {
                                                        Modifier.pointerInput(Unit) {
                                                            detectDragGesturesAfterLongPress(
                                                                onDragStart = {
                                                                    draggedIndex = index
                                                                    dragAccumulatedOffset = 0f
                                                                },
                                                                onDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    dragAccumulatedOffset += dragAmount.y

                                                                    if (dragAccumulatedOffset > swapThreshold && index < currentList.size - 1) {
                                                                        moveItem(index, index + 1)
                                                                        draggedIndex = index + 1
                                                                        dragAccumulatedOffset -= swapThreshold
                                                                    } else if (dragAccumulatedOffset < -swapThreshold && index > 0) {
                                                                        moveItem(index, index - 1)
                                                                        draggedIndex = index - 1
                                                                        dragAccumulatedOffset += swapThreshold
                                                                    }
                                                                },
                                                                onDragEnd = {
                                                                    draggedIndex = null
                                                                    dragAccumulatedOffset = 0f
                                                                },
                                                                onDragCancel = {
                                                                    draggedIndex = null
                                                                    dragAccumulatedOffset = 0f
                                                                }
                                                            )
                                                        }
                                                    } else Modifier
                                                )
                                        ) {
                                            DeviceItemCard(
                                                device = device,
                                                status = deviceStatuses[device.id] ?: DeviceStatus.CHECKING,
                                                showStatusBadge = (statusCheckInterval > 0),
                                                macDisplay = cardMacDisplay,
                                                localIpDisplay = cardLocalIpDisplay,
                                                wanIpDisplay = cardWanIpDisplay,
                                                portDisplay = cardPortDisplay,
                                                onRefreshStatus = { viewModel.refreshDeviceStatus(context, device) },
                                                onSendWol = {
                                                    viewModel.sendWol(device) { success, error ->
                                                        val msg = if (success) {
                                                            context.getString(R.string.packet_sent_success, device.name, packetCount)
                                                        } else {
                                                            context.getString(R.string.packet_sent_error, error ?: "")
                                                        }
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                        viewModel.refreshDeviceStatus(context, device)
                                                    }
                                                },
                                                onEditRequest = { deviceToEdit = device },
                                                onScheduleRequest = { deviceToSchedule = device },
                                                onDeleteRequest = { deviceToDelete = device }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showAddDialog) {
                        AddOrEditDeviceDialog(
                            deviceToEdit = null,
                            useShizuku = isShizukuEnabled,
                            onDismiss = { showAddDialog = false },
                            onConfirm = { name, mac, ip, localIp, port, secureOn ->
                                viewModel.addDevice(name, mac, ip, localIp, port, secureOn)
                                showAddDialog = false
                            }
                        )
                    }

                    deviceToEdit?.let { device ->
                        AddOrEditDeviceDialog(
                            deviceToEdit = device,
                            useShizuku = isShizukuEnabled,
                            onDismiss = { deviceToEdit = null },
                            onConfirm = { name, mac, ip, localIp, port, secureOn ->
                                val updatedDevice = device.copy(
                                    name = name,
                                    macAddress = mac,
                                    ipAddress = ip,
                                    localIp = localIp,
                                    port = port,
                                    secureOnPassword = secureOn?.ifBlank { null }
                                )
                                viewModel.updateDevice(updatedDevice)
                                deviceToEdit = null
                            }
                        )
                    }

                    deviceToSchedule?.let { device ->
                        DeviceSchedulesDialog(
                            device = device,
                            viewModel = viewModel,
                            onDismiss = { deviceToSchedule = null }
                        )
                    }

                    if (showPacketCountSettingsDialog) {
                        PacketCountSettingsDialog(
                            currentCount = packetCount,
                            onDismiss = { showPacketCountSettingsDialog = false },
                            onConfirm = { newCount ->
                                viewModel.updatePacketCount(newCount)
                                showPacketCountSettingsDialog = false
                            }
                        )
                    }

                    if (showStatusIntervalDialog) {
                        StatusIntervalSettingsDialog(
                            currentInterval = statusCheckInterval,
                            onDismiss = { showStatusIntervalDialog = false },
                            onConfirm = { newInterval ->
                                viewModel.updateStatusCheckInterval(newInterval)
                                showStatusIntervalDialog = false
                            }
                        )
                    }

                    if (showCardCustomizationDialog) {
                        CardCustomizationDialog(
                            currentMac = cardMacDisplay,
                            currentLocalIp = cardLocalIpDisplay,
                            currentWanIp = cardWanIpDisplay,
                            currentPort = cardPortDisplay,
                            onDismiss = { showCardCustomizationDialog = false },
                            onSave = { mac, localIp, wan, port ->
                                viewModel.updateCardCustomization(mac, localIp, wan, port)
                                showCardCustomizationDialog = false
                            }
                        )
                    }

                    if (showThemeDialog) {
                        ThemeSelectionDialog(
                            currentTheme = currentTheme,
                            onDismiss = { showThemeDialog = false },
                            onThemeSelected = { selectedTheme ->
                                onThemeChange(selectedTheme)
                                showThemeDialog = false
                            }
                        )
                    }

                    // SADECE SİSTEM VARSAYILANI, TÜRKÇE VE İNGİLİZCE DİYALOĞU
                    if (showLanguageDialog) {
                        LanguageSelectionDialog(
                            currentLangTag = appLanguage,
                            onDismiss = { showLanguageDialog = false },
                            onLanguageSelected = { selectedTag ->
                                prefs.edit().putString("app_language", selectedTag).apply()
                                showLanguageDialog = false
                                (context as? Activity)?.recreate()
                            }
                        )
                    }

                    if (showStatisticsDialog) {
                        StatisticsDialog(
                            totalWakeUps = totalWakeUps,
                            totalPacketsSent = totalPacketsSent,
                            onDismiss = { showStatisticsDialog = false }
                        )
                    }

                    deviceToDelete?.let { device ->
                        AlertDialog(
                            onDismissRequest = { deviceToDelete = null },
                            title = { Text(stringResource(R.string.delete_device)) },
                            text = { Text(stringResource(R.string.delete_device_confirm, device.name)) },
                            confirmButton = {
                                TextButton(onClick = { deviceToDelete = null }) {
                                    Text(stringResource(R.string.no))
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.deleteDevice(device)
                                        deviceToDelete = null
                                    }
                                ) {
                                    Text(stringResource(R.string.yes), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// SADE VE TERTEMİZ DİL SEÇİMİ (Sadece var olan 3 seçenek)
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
fun DeviceItemCard(
    device: DeviceEntity,
    status: DeviceStatus,
    showStatusBadge: Boolean,
    macDisplay: String,
    localIpDisplay: String,
    wanIpDisplay: String,
    portDisplay: String,
    onRefreshStatus: () -> Unit,
    onSendWol: () -> Unit,
    onEditRequest: () -> Unit,
    onScheduleRequest: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

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
                            imageVector = ComputerIcon,
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
                    if (showStatusBadge) {
                        Spacer(modifier = Modifier.height(2.dp))
                        DeviceStatusBadge(
                            status = status,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedFeaturesScreen(
    viewModel: MainViewModel,
    isShizukuEnabled: Boolean,
    onRequestShizukuPermission: () -> Unit,
    onSetShizukuEnabled: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    var showDnsDialog by remember { mutableStateOf(false) }
    var showPingDialog by remember { mutableStateOf(false) }
    var showInternalAutomationDialog by remember { mutableStateOf(false) }
    var showAutomationGuideDialog by remember { mutableStateOf(false) }
    var showShizukuDialog by remember { mutableStateOf(false) }
    var showAppLockDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("wol_settings", Context.MODE_PRIVATE) }
    var isAppLockActive by remember { mutableStateOf(prefs.getBoolean("app_lock_enabled", false)) }
    val pinLength = remember(isAppLockActive) { prefs.getInt("security_pin_length", 4) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.advanced_features)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDnsDialog = true },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = DnsIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.dns_query_tool),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.dns_query_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPingDialog = true },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.ping_tool),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.ping_tool_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showInternalAutomationDialog = true },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.internal_automation),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.internal_automation_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAutomationGuideDialog = true },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.external_automation),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.external_automation_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showShizukuDialog = true },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_shizuku),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.shizuku_integration),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isShizukuEnabled) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isShizukuEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.shizuku_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAppLockDialog = true },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.app_lock),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isAppLockActive) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isAppLockActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isAppLockActive) "$pinLength " + stringResource(R.string.digits) + " · " + stringResource(R.string.app_lock_description)
                                else stringResource(R.string.app_lock_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                NetworkInfoCard(isShizukuEnabled = isShizukuEnabled)
            }
        }
    }

    if (showInternalAutomationDialog) {
        AllSchedulesOverviewDialog(
            viewModel = viewModel,
            onDismiss = { showInternalAutomationDialog = false }
        )
    }

    if (showDnsDialog) {
        DnsQueryDialog(onDismiss = { showDnsDialog = false })
    }

    if (showPingDialog) {
        PingToolDialog(onDismiss = { showPingDialog = false })
    }

    if (showAutomationGuideDialog) {
        AutomationGuideDialog(onDismiss = { showAutomationGuideDialog = false })
    }

    if (showShizukuDialog) {
        ShizukuDialog(
            isEnabled = isShizukuEnabled,
            onDismiss = { showShizukuDialog = false },
            onConfirm = { enabled ->
                if (enabled) {
                    onRequestShizukuPermission()
                } else {
                    onSetShizukuEnabled(false)
                }
            }
        )
    }

    if (showAppLockDialog) {
        AppLockSettingsDialog(
            onDismiss = {
                showAppLockDialog = false
                isAppLockActive = prefs.getBoolean("app_lock_enabled", false)
            }
        )
    }
}

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

@Composable
fun AllSchedulesOverviewDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val schedules by viewModel.allSchedules.collectAsState()
    val devices by viewModel.devices.collectAsState()

    var showAddOrEditScheduleSheet by remember { mutableStateOf(false) }
    var scheduleToEdit by remember { mutableStateOf<ScheduleEntity?>(null) }
    var scheduleToDelete by remember { mutableStateOf<ScheduleEntity?>(null) }

    var currentScheduleList by remember(schedules) { mutableStateOf(schedules) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumulatedOffset by remember { mutableFloatStateOf(0f) }

    val deviceMap = remember(devices) { devices.associateBy { it.id } }

    fun moveScheduleItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex in currentScheduleList.indices && toIndex in currentScheduleList.indices) {
            currentScheduleList = currentScheduleList.toMutableList().apply {
                val item = removeAt(fromIndex)
                add(toIndex, item)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.internal_automation), style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (devices.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_devices_for_schedule),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                        textAlign = TextAlign.Center
                    )
                } else if (schedules.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.no_schedules_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                scheduleToEdit = null
                                showAddOrEditScheduleSheet = true
                            },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(stringResource(R.string.add_schedule))
                        }
                    }
                } else {
                    val density = context.resources.displayMetrics.density
                    val swapThreshold = 100f * density

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        itemsIndexed(currentScheduleList, key = { _, item -> item.id }) { index, schedule ->
                            val isDragging = (draggedIndex == index)
                            val targetDev = deviceMap[schedule.deviceId]
                            val deviceName = targetDev?.name ?: "Bilinmeyen Cihaz"
                            val nextTime = AlarmScheduler.getNextTriggerTime(schedule)
                            val nextTimeStr = formatDateTime(nextTime)

                            val elevation by animateDpAsState(
                                targetValue = if (isDragging) 8.dp else 0.dp,
                                label = "elevation"
                            )
                            val scale by animateFloatAsState(
                                targetValue = if (isDragging) 1.03f else 1.0f,
                                label = "scale"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (isDragging) 10f else 1f)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationY = if (isDragging) dragAccumulatedOffset else 0f
                                    }
                                    .shadow(elevation, shape = MaterialTheme.shapes.small)
                                    .pointerInput(Unit) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggedIndex = index
                                                dragAccumulatedOffset = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragAccumulatedOffset += dragAmount.y

                                                if (dragAccumulatedOffset > swapThreshold && index < currentScheduleList.size - 1) {
                                                    moveScheduleItem(index, index + 1)
                                                    draggedIndex = index + 1
                                                    dragAccumulatedOffset -= swapThreshold
                                                } else if (dragAccumulatedOffset < -swapThreshold && index > 0) {
                                                    moveScheduleItem(index, index - 1)
                                                    draggedIndex = index - 1
                                                    dragAccumulatedOffset += swapThreshold
                                                }
                                            },
                                            onDragEnd = {
                                                draggedIndex = null
                                                dragAccumulatedOffset = 0f
                                            },
                                            onDragCancel = {
                                                draggedIndex = null
                                                dragAccumulatedOffset = 0f
                                            }
                                        )
                                    }
                            ) {
                                ScheduleItemCard(
                                    deviceName = deviceName,
                                    schedule = schedule,
                                    nextTimeStr = nextTimeStr,
                                    onToggle = { isChecked ->
                                        viewModel.toggleSchedule(context, schedule, isChecked)
                                    },
                                    onEdit = {
                                        scheduleToEdit = schedule
                                        showAddOrEditScheduleSheet = true
                                    },
                                    onDelete = {
                                        scheduleToDelete = schedule
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            scheduleToEdit = null
                            showAddOrEditScheduleSheet = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(stringResource(R.string.add_schedule))
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

    if (showAddOrEditScheduleSheet && devices.isNotEmpty()) {
        AddOrEditScheduleDialog(
            defaultDeviceId = devices.first().id,
            devices = devices,
            scheduleToEdit = scheduleToEdit,
            onDismiss = {
                showAddOrEditScheduleSheet = false
                scheduleToEdit = null
            },
            onSave = { updatedSchedule ->
                viewModel.saveSchedule(context, updatedSchedule)
                showAddOrEditScheduleSheet = false
                scheduleToEdit = null
            }
        )
    }

    scheduleToDelete?.let { schedule ->
        AlertDialog(
            onDismissRequest = { scheduleToDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_schedule_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSchedule(context, schedule)
                        scheduleToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.yes), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { scheduleToDelete = null }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }
}

@Composable
fun ScheduleItemCard(
    deviceName: String,
    schedule: ScheduleEntity,
    nextTimeStr: String,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = deviceName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (schedule.isOneTime && schedule.targetDateMillis != null) {
                        Text(
                            text = formatDateTime(schedule.targetDateMillis),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = "%02d:%02d · %s".format(schedule.hour, schedule.minute, formatDays(schedule.daysOfWeek)),
                            style = MaterialTheme.typography.bodyMedium
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
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (schedule.isEnabled) stringResource(R.string.next_trigger, nextTimeStr) else stringResource(R.string.completed),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (schedule.isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f)
                )

                Switch(
                    checked = schedule.isEnabled,
                    onCheckedChange = onToggle
                )
            }
        }
    }
}

@Composable
fun DeviceSchedulesDialog(
    device: DeviceEntity,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val schedules by viewModel.getSchedulesForDevice(device.id).collectAsState(initial = emptyList())
    var showAddOrEditScheduleSheet by remember { mutableStateOf(false) }
    var scheduleToEdit by remember { mutableStateOf<ScheduleEntity?>(null) }
    var scheduleToDelete by remember { mutableStateOf<ScheduleEntity?>(null) }

    var currentScheduleList by remember(schedules) { mutableStateOf(schedules) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumulatedOffset by remember { mutableFloatStateOf(0f) }

    fun moveScheduleItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex in currentScheduleList.indices && toIndex in currentScheduleList.indices) {
            currentScheduleList = currentScheduleList.toMutableList().apply {
                val item = removeAt(fromIndex)
                add(toIndex, item)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${device.name} - " + stringResource(R.string.schedules))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (schedules.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.no_schedules_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                scheduleToEdit = null
                                showAddOrEditScheduleSheet = true
                            },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(stringResource(R.string.add_schedule))
                        }
                    }
                } else {
                    val density = context.resources.displayMetrics.density
                    val swapThreshold = 100f * density

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        itemsIndexed(currentScheduleList, key = { _, item -> item.id }) { index, schedule ->
                            val isDragging = (draggedIndex == index)
                            val nextTime = AlarmScheduler.getNextTriggerTime(schedule)
                            val nextTimeStr = formatDateTime(nextTime)

                            val elevation by animateDpAsState(
                                targetValue = if (isDragging) 8.dp else 0.dp,
                                label = "elevation"
                            )
                            val scale by animateFloatAsState(
                                targetValue = if (isDragging) 1.03f else 1.0f,
                                label = "scale"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (isDragging) 10f else 1f)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationY = if (isDragging) dragAccumulatedOffset else 0f
                                    }
                                    .shadow(elevation, shape = MaterialTheme.shapes.small)
                                    .pointerInput(Unit) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggedIndex = index
                                                dragAccumulatedOffset = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragAccumulatedOffset += dragAmount.y

                                                if (dragAccumulatedOffset > swapThreshold && index < currentScheduleList.size - 1) {
                                                    moveScheduleItem(index, index + 1)
                                                    draggedIndex = index + 1
                                                    dragAccumulatedOffset -= swapThreshold
                                                } else if (dragAccumulatedOffset < -swapThreshold && index > 0) {
                                                    moveScheduleItem(index, index - 1)
                                                    draggedIndex = index - 1
                                                    dragAccumulatedOffset += swapThreshold
                                                }
                                            },
                                            onDragEnd = {
                                                draggedIndex = null
                                                dragAccumulatedOffset = 0f
                                            },
                                            onDragCancel = {
                                                draggedIndex = null
                                                dragAccumulatedOffset = 0f
                                            }
                                        )
                                    }
                            ) {
                                ScheduleItemCard(
                                    deviceName = device.name,
                                    schedule = schedule,
                                    nextTimeStr = nextTimeStr,
                                    onToggle = { isChecked ->
                                        viewModel.toggleSchedule(context, schedule, isChecked)
                                    },
                                    onEdit = {
                                        scheduleToEdit = schedule
                                        showAddOrEditScheduleSheet = true
                                    },
                                    onDelete = {
                                        scheduleToDelete = schedule
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            scheduleToEdit = null
                            showAddOrEditScheduleSheet = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(stringResource(R.string.add_schedule))
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

    if (showAddOrEditScheduleSheet) {
        AddOrEditScheduleDialog(
            defaultDeviceId = device.id,
            devices = listOf(device),
            scheduleToEdit = scheduleToEdit,
            onDismiss = {
                showAddOrEditScheduleSheet = false
                scheduleToEdit = null
            },
            onSave = { updatedSchedule ->
                viewModel.saveSchedule(context, updatedSchedule)
                showAddOrEditScheduleSheet = false
                scheduleToEdit = null
            }
        )
    }

    scheduleToDelete?.let { schedule ->
        AlertDialog(
            onDismissRequest = { scheduleToDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_schedule_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSchedule(context, schedule)
                        scheduleToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.yes), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { scheduleToDelete = null }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOrEditScheduleDialog(
    defaultDeviceId: Long,
    devices: List<DeviceEntity>,
    scheduleToEdit: ScheduleEntity? = null,
    onDismiss: () -> Unit,
    onSave: (ScheduleEntity) -> Unit
) {
    val context = LocalContext.current
    var selectedDeviceId by remember { mutableLongStateOf(scheduleToEdit?.deviceId ?: defaultDeviceId) }
    var isOneTime by remember { mutableStateOf(scheduleToEdit?.isOneTime ?: false) }

    var hourText by remember { mutableStateOf(scheduleToEdit?.let { "%02d".format(it.hour) } ?: "08") }
    var minuteText by remember { mutableStateOf(scheduleToEdit?.let { "%02d".format(it.minute) } ?: "30") }
    val selectedDays = remember {
        mutableStateListOf<Int>().apply {
            if (scheduleToEdit != null) {
                addAll(scheduleToEdit.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() })
            } else {
                addAll(listOf(1, 2, 3, 4, 5))
            }
        }
    }

    var targetCalendar by remember {
        mutableStateOf(Calendar.getInstance().apply {
            if (scheduleToEdit?.targetDateMillis != null) {
                timeInMillis = scheduleToEdit.targetDateMillis
            } else {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
            }
        })
    }

    val isEnglish = Locale.getDefault().language == "en"
    val dayNames = if (isEnglish) {
        listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
    } else {
        listOf(1 to "Pzt", 2 to "Sal", 3 to "Çar", 4 to "Per", 5 to "Cum", 6 to "Cmt", 7 to "Paz")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (scheduleToEdit == null) R.string.add_schedule else R.string.edit_schedule))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (devices.size > 1) {
                    Text(stringResource(R.string.select_device), style = MaterialTheme.typography.titleSmall)
                    var expanded by remember { mutableStateOf(false) }
                    val currentSelectedDevice = devices.firstOrNull { it.id == selectedDeviceId } ?: devices.first()

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = currentSelectedDevice.name,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            devices.forEach { dev ->
                                DropdownMenuItem(
                                    text = { Text(dev.name) },
                                    onClick = {
                                        selectedDeviceId = dev.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Text(stringResource(R.string.schedule_type), style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isOneTime,
                        onClick = { isOneTime = false },
                        label = { Text(stringResource(R.string.schedule_type_weekly)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isOneTime,
                        onClick = { isOneTime = true },
                        label = { Text(stringResource(R.string.schedule_type_onetime)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isOneTime) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(targetCalendar.time),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Button(onClick = {
                            val c = targetCalendar
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val updated = (targetCalendar.clone() as Calendar).apply {
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month)
                                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    }
                                    targetCalendar = updated
                                },
                                c.get(Calendar.YEAR),
                                c.get(Calendar.MONTH),
                                c.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }) {
                            Text(stringResource(R.string.select_date))
                        }
                    }
                }

                Text(stringResource(R.string.time), style = MaterialTheme.typography.titleSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) hourText = it },
                        label = { Text(stringResource(R.string.hour_label)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Text(":", style = MaterialTheme.typography.headlineMedium)
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) minuteText = it },
                        label = { Text(stringResource(R.string.minute_label)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                if (!isOneTime) {
                    Text(stringResource(R.string.repeat_days), style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        dayNames.forEach { (dayIndex, dayLabel) ->
                            val isSelected = selectedDays.contains(dayIndex)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable {
                                        if (isSelected) {
                                            if (selectedDays.size > 1) selectedDays.remove(dayIndex)
                                        } else {
                                            selectedDays.add(dayIndex)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val h = hourText.toIntOrNull()?.coerceIn(0, 23) ?: 8
                    val m = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 0

                    val schedule = if (isOneTime) {
                        val oneTimeCal = (targetCalendar.clone() as Calendar).apply {
                            set(Calendar.HOUR_OF_DAY, h)
                            set(Calendar.MINUTE, m)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        ScheduleEntity(
                            id = scheduleToEdit?.id ?: 0L,
                            deviceId = selectedDeviceId,
                            hour = h,
                            minute = m,
                            isOneTime = true,
                            targetDateMillis = oneTimeCal.timeInMillis,
                            isEnabled = scheduleToEdit?.isEnabled ?: true
                        )
                    } else {
                        ScheduleEntity(
                            id = scheduleToEdit?.id ?: 0L,
                            deviceId = selectedDeviceId,
                            hour = h,
                            minute = m,
                            daysOfWeek = selectedDays.sorted().joinToString(","),
                            isOneTime = false,
                            targetDateMillis = null,
                            isEnabled = scheduleToEdit?.isEnabled ?: true
                        )
                    }
                    onSave(schedule)
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

fun formatDays(daysStr: String): String {
    val days = daysStr.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    val isEnglish = Locale.getDefault().language == "en"

    if (days.size == 7) return if (isEnglish) "Every day" else "Her gün"
    if (days == setOf(1, 2, 3, 4, 5)) return if (isEnglish) "Weekdays" else "Hafta içi"
    if (days == setOf(6, 7)) return if (isEnglish) "Weekends" else "Hafta sonu"

    val map = if (isEnglish) {
        mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
    } else {
        mapOf(1 to "Pzt", 2 to "Sal", 3 to "Çar", 4 to "Per", 5 to "Cum", 6 to "Cmt", 7 to "Paz")
    }
    return days.sorted().mapNotNull { map[it] }.joinToString(", ")
}

fun formatDateTime(millis: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}

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

@Composable
fun AppLockSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val prefs = remember { context.getSharedPreferences("wol_settings", Context.MODE_PRIVATE) }

    var lockEnabled by remember { mutableStateOf(prefs.getBoolean("app_lock_enabled", false)) }
    var selectedPinLength by remember { mutableIntStateOf(prefs.getInt("security_pin_length", 4)) }
    var biometricEnabled by remember { mutableStateOf(prefs.getBoolean("security_biometric_enabled", true)) }

    var pinInput by remember { mutableStateOf("") }
    var pinConfirmInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showConfirmSaveDialog by remember { mutableStateOf(false) }

    fun performSave() {
        if (lockEnabled) {
            prefs.edit()
                .putBoolean("app_lock_enabled", true)
                .putInt("security_pin_length", selectedPinLength)
                .putString("security_pin_hash", hashPin(pinInput))
                .putBoolean("security_biometric_enabled", biometricEnabled)
                .apply()
            activity?.updateWindowSecurity(true)
            activity?.isAppUnlocked?.value = true
            Toast.makeText(context, context.getString(R.string.pin_saved), Toast.LENGTH_SHORT).show()
        } else {
            prefs.edit().putBoolean("app_lock_enabled", false).apply()
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
                                label = {
                                    Text(
                                        text = "$len ${stringResource(R.string.digits)}",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
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

@Composable
fun DnsQueryDialog(onDismiss: () -> Unit) {
    var domainInput by remember { mutableStateOf("") }
    var isQuerying by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<String>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun performDnsQuery() {
        val domain = domainInput.trim()
        if (domain.isBlank()) return
        isQuerying = true
        errorMessage = null
        results = null

        scope.launch(Dispatchers.IO) {
            try {
                val addresses = InetAddress.getAllByName(domain)
                val resolvedList = addresses.map { addr ->
                    "${addr.hostAddress} (${addr.hostName})"
                }
                withContext(Dispatchers.Main) {
                    results = resolvedList
                    isQuerying = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = e.localizedMessage ?: "Sunucu bulunamadı"
                    isQuerying = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = DnsIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.dns_query_tool))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = domainInput,
                    onValueChange = { domainInput = it },
                    label = { Text(stringResource(R.string.enter_domain_or_ip)) },
                    placeholder = { Text("cloudflare.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { performDnsQuery() })
                )

                Button(
                    onClick = { performDnsQuery() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = domainInput.isNotBlank() && !isQuerying
                ) {
                    if (isQuerying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.querying))
                    } else {
                        Text(stringResource(R.string.query))
                    }
                }

                if (results != null) {
                    Text(
                        text = stringResource(R.string.query_results),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            results?.forEach { item ->
                                Text(
                                    text = "• $item",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: stringResource(R.string.host_not_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
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
fun PingToolDialog(onDismiss: () -> Unit) {
    var hostInput by remember { mutableStateOf("") }
    var pingCountText by remember { mutableStateOf("4") }
    var isPinging by remember { mutableStateOf(false) }
    var pingOutput by remember { mutableStateOf<List<String>>(emptyList()) }

    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentLocale = context.resources.configuration.locales[0]
    val isTurkish = currentLocale.language == "tr"

    fun translatePingLine(line: String): String {
        if (!isTurkish) return line
        var tLine = line
        if (tLine.contains("ping statistics")) {
            tLine = tLine.replace("ping statistics", "Ping İstatistikleri")
        }
        if (tLine.contains("packets transmitted")) {
            tLine = tLine
                .replace("packets transmitted", "paket gönderildi")
                .replace("received", "alındı")
                .replace("packet loss", "paket kaybı")
                .replace("time", "toplam süre:")
        }
        if (tLine.contains("rtt min/avg/max/mdev") || tLine.contains("round-trip min/avg/max")) {
            tLine = tLine
                .replace("rtt", "Gecikme (RTT)")
                .replace("round-trip", "Gecikme")
                .replace("min/avg/max/mdev", "min/ort/maks/sapma")
                .replace("min/avg/max", "min/ort/maks")
        }
        return tLine
    }

    LaunchedEffect(pingOutput.size) {
        if (pingOutput.isNotEmpty()) {
            lazyListState.animateScrollToItem(pingOutput.size - 1)
        }
    }

    fun performPing() {
        val host = hostInput.trim()
        if (host.isBlank()) return
        val count = pingCountText.toIntOrNull()?.coerceAtLeast(1) ?: 4
        isPinging = true
        pingOutput = emptyList()

        scope.launch(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec("ping -c $count $host")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String? = reader.readLine()
                while (line != null) {
                    if (line.isNotBlank()) {
                        val formattedLine = translatePingLine(line)
                        withContext(Dispatchers.Main) {
                            pingOutput = pingOutput + formattedLine
                        }
                    }
                    line = reader.readLine()
                }
                process.waitFor()
            } catch (e: Exception) {
                val err = "Hata: ${e.localizedMessage}"
                withContext(Dispatchers.Main) {
                    pingOutput = pingOutput + err
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isPinging = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.ping_tool))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = hostInput,
                    onValueChange = { hostInput = it },
                    label = { Text(stringResource(R.string.enter_ping_host)) },
                    placeholder = { Text("1.1.1.1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                OutlinedTextField(
                    value = pingCountText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) {
                            pingCountText = input
                        }
                    },
                    label = { Text(stringResource(R.string.ping_count_label)) },
                    placeholder = { Text("4") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { performPing() })
                )

                Button(
                    onClick = { performPing() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hostInput.isNotBlank() && pingCountText.isNotBlank() && !isPinging
                ) {
                    if (isPinging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.pinging))
                    } else {
                        Text(stringResource(R.string.ping))
                    }
                }

                if (pingOutput.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            itemsIndexed(pingOutput) { _, line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
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

@Composable
fun NetworkInfoCard(isShizukuEnabled: Boolean) {
    var wifiIp by remember { mutableStateOf("...") }
    var macAddress by remember { mutableStateOf("...") }

    val restrictedText = stringResource(R.string.privacy_restricted)

    LaunchedEffect(isShizukuEnabled) {
        withContext(Dispatchers.IO) {
            try {
                var foundIp: String? = null
                var foundMac: String? = null

                if (isShizukuEnabled) {
                    val shizukuInfo = getNetworkInfoViaShizuku()
                    foundIp = shizukuInfo.first
                    foundMac = shizukuInfo.second
                }

                if (foundIp.isNullOrBlank()) {
                    foundIp = getPhysicalLocalIpAddress() ?: "Bilinmiyor"
                }

                if (foundMac.isNullOrBlank()) {
                    foundMac = getPhysicalMacAddress() ?: restrictedText
                }

                wifiIp = foundIp
                macAddress = foundMac
            } catch (_: Exception) {
                wifiIp = "Bilinmiyor"
                macAddress = restrictedText
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.local_network_info),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.local_ip_label), style = MaterialTheme.typography.bodyMedium)
                Text(wifiIp, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.mac_address_label), style = MaterialTheme.typography.bodyMedium)
                Text(macAddress, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun getNetworkInfoViaShizuku(): Pair<String?, String?> {
    return try {
        if (!Shizuku.pingBinder()) return Pair(null, null)
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return Pair(null, null)

        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true

        var detectedIp: String? = null
        var detectedMac: String? = null

        val ipCmd = arrayOf("ip", "-4", "addr")
        val ipProcess = method.invoke(null, ipCmd, null, null) as? Process
        if (ipProcess != null) {
            val reader = BufferedReader(InputStreamReader(ipProcess.inputStream))
            var line: String?
            var currentIface = ""
            val ifaceIps = mutableMapOf<String, String>()

            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim() ?: continue
                val ifaceMatch = Regex("""^\d+:\s+([a-zA-Z0-9_-]+):""").find(l)
                if (ifaceMatch != null) {
                    currentIface = ifaceMatch.groupValues[1]
                }
                val inetMatch = Regex("""^inet\s+(\d+\.\d+\.\d+\.\d+)""").find(l)
                if (inetMatch != null && currentIface.isNotBlank()) {
                    val ip = inetMatch.groupValues[1]
                    if (ip != "127.0.0.1") {
                        ifaceIps[currentIface] = ip
                    }
                }
            }
            ipProcess.waitFor()

            detectedIp = ifaceIps["wlan0"]
                ?: ifaceIps["wlan1"]
                        ?: ifaceIps["eth0"]
                        ?: ifaceIps.entries.firstOrNull { (name, _) ->
                    !name.startsWith("tun") && !name.startsWith("dummy") && !name.startsWith("lo") && !name.startsWith("p2p")
                }?.value
        }

        val linkCmd = arrayOf("ip", "link")
        val linkProcess = method.invoke(null, linkCmd, null, null) as? Process
        if (linkProcess != null) {
            val reader = BufferedReader(InputStreamReader(linkProcess.inputStream))
            var line: String?
            var currentIface = ""
            val ifaceMacs = mutableMapOf<String, String>()

            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim() ?: continue
                val ifaceMatch = Regex("""^\d+:\s+([a-zA-Z0-9_-]+):""").find(l)
                if (ifaceMatch != null) {
                    currentIface = ifaceMatch.groupValues[1]
                }
                val macMatch = Regex("""link/ether\s+([0-9a-fA-F:]{17})""").find(l)
                if (macMatch != null && currentIface.isNotBlank()) {
                    val mac = macMatch.groupValues[1].uppercase()
                    if (mac != "00:00:00:00:00:00" && mac != "02:00:00:00:00:00") {
                        ifaceMacs[currentIface] = mac
                    }
                }
            }
            linkProcess.waitFor()

            detectedMac = ifaceMacs["wlan0"]
                ?: ifaceMacs["wlan1"]
                        ?: ifaceMacs["eth0"]
                        ?: ifaceMacs.entries.firstOrNull { (name, _) ->
                    !name.startsWith("tun") && !name.startsWith("dummy") && !name.startsWith("lo") && !name.startsWith("p2p")
                }?.value
        }

        if (detectedMac.isNullOrBlank()) {
            val sysfsCmd = arrayOf("sh", "-c", "cat /sys/class/net/wlan0/address 2>/dev/null || cat /sys/class/net/wlan1/address 2>/dev/null || cat /sys/class/net/eth0/address 2>/dev/null")
            val sysfsProcess = method.invoke(null, sysfsCmd, null, null) as? Process
            if (sysfsProcess != null) {
                val reader = BufferedReader(InputStreamReader(sysfsProcess.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val mac = line?.trim()?.uppercase() ?: continue
                    if (mac.matches(Regex("""^([0-9A-FA-F]{2}:){5}[0-9A-FA-F]{2}$""")) &&
                        mac != "00:00:00:00:00:00" && mac != "02:00:00:00:00:00") {
                        detectedMac = mac
                        break
                    }
                }
                sysfsProcess.waitFor()
            }
        }

        Pair(detectedIp, detectedMac)
    } catch (_: Exception) {
        Pair(null, null)
    }
}

private fun getPhysicalLocalIpAddress(): String? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces().toList()
        val sortedInterfaces = interfaces.sortedWith { o1, o2 ->
            val o1Prio = if (o1.name.startsWith("wlan") || o1.name.startsWith("eth")) 0 else if (o1.name.startsWith("tun")) 2 else 1
            val o2Prio = if (o2.name.startsWith("wlan") || o2.name.startsWith("eth")) 0 else if (o2.name.startsWith("tun")) 2 else 1
            o1Prio.compareTo(o2Prio)
        }

        for (element in sortedInterfaces) {
            val name = element.name.lowercase()
            if (name.startsWith("dummy") || name.startsWith("lo") || name.startsWith("p2p")) continue

            val addresses = element.inetAddresses
            while (addresses.hasMoreElements()) {
                val addr = addresses.nextElement()
                if (!addr.isLoopbackAddress && addr is InetAddress && addr.hostAddress?.contains(":") == false) {
                    val ip = addr.hostAddress
                    if (!ip.isNullOrBlank()) {
                        return ip
                    }
                }
            }
        }
    } catch (_: Exception) {}
    return null
}

private fun getPhysicalMacAddress(): String? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces().toList()
        for (element in interfaces) {
            val name = element.name.lowercase()
            if (name.startsWith("wlan") || name.startsWith("eth")) {
                val hardwareAddress = element.hardwareAddress
                if (hardwareAddress != null && hardwareAddress.isNotEmpty()) {
                    val rawMac = hardwareAddress.joinToString(":") { "%02X".format(it) }
                    if (rawMac != "02:00:00:00:00:00" && rawMac != "00:00:00:00:00:00") {
                        return rawMac
                    }
                }
            }
        }
    } catch (_: Exception) {}
    return null
}

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
fun StatisticsDialog(
    totalWakeUps: Int,
    totalPacketsSent: Int,
    onDismiss: () -> Unit
) {
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOrEditDeviceDialog(
    deviceToEdit: DeviceEntity?,
    useShizuku: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, Int, String?) -> Unit
) {
    var name by remember { mutableStateOf(deviceToEdit?.name ?: "") }
    var mac by remember { mutableStateOf(deviceToEdit?.macAddress ?: "") }
    var ip by remember { mutableStateOf(deviceToEdit?.ipAddress ?: "") }
    var localIp by remember { mutableStateOf(deviceToEdit?.localIp ?: "") }
    var portText by remember { mutableStateOf(deviceToEdit?.port?.toString() ?: "9") }
    var secureOn by remember { mutableStateOf(deviceToEdit?.secureOnPassword ?: "") }

    var showScanSheet by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (deviceToEdit == null) stringResource(R.string.add_device) else stringResource(R.string.edit_device)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.device_name)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = { Text(stringResource(R.string.mac_address)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text(stringResource(R.string.wan_ddns_address)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = localIp,
                    onValueChange = { localIp = it },
                    label = { Text(stringResource(R.string.local_ip_address)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text(stringResource(R.string.port_default)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = secureOn,
                    onValueChange = { secureOn = it },
                    label = { Text(stringResource(R.string.secureon_password)) },
                    singleLine = true
                )
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
                                val port = portText.toIntOrNull() ?: 9
                                onConfirm(name, mac, ip, localIp, port, secureOn.ifBlank { null })
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

@Composable
fun DeviceStatusBadge(
    status: DeviceStatus,
    onRefresh: () -> Unit
) {
    val (bgColor, textColor, text) = when (status) {
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
        }
    }
}

private var _computerIcon: ImageVector? = null

private val ComputerIcon: ImageVector
    get() {
        if (_computerIcon != null) return _computerIcon!!
        _computerIcon = ImageVector.Builder(
            name = "Computer",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(20.0f, 18.0f)
            curveTo(20.55f, 18.0f, 21.0f, 17.55f, 21.0f, 17.0f)
            lineTo(21.0f, 5.0f)
            curveTo(21.0f, 4.45f, 20.55f, 4.0f, 20.0f, 4.0f)
            lineTo(4.0f, 4.0f)
            curveTo(3.45f, 4.0f, 3.0f, 4.45f, 3.0f, 5.0f)
            lineTo(3.0f, 17.0f)
            curveTo(3.0f, 17.55f, 3.45f, 18.0f, 4.0f, 18.0f)
            lineTo(0.0f, 18.0f)
            lineTo(0.0f, 20.0f)
            lineTo(24.0f, 20.0f)
            lineTo(24.0f, 18.0f)
            close()
            moveTo(5.0f, 6.0f)
            lineTo(19.0f, 6.0f)
            lineTo(19.0f, 16.0f)
            lineTo(5.0f, 16.0f)
            close()
        }.build()
        return _computerIcon!!
    }

private var _paletteIcon: ImageVector? = null

private val PaletteIcon: ImageVector
    get() {
        if (_paletteIcon != null) return _paletteIcon!!
        _paletteIcon = ImageVector.Builder(
            name = "Palette",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12.0f, 3.0f)
            curveTo(6.5f, 3.0f, 2.0f, 6.5f, 2.0f, 12.0f)
            curveTo(2.0f, 16.5f, 5.5f, 20.0f, 10.0f, 20.0f)
            curveTo(10.55f, 20.0f, 11.0f, 19.55f, 11.0f, 19.0f)
            curveTo(11.0f, 18.75f, 10.9f, 18.5f, 10.8f, 18.3f)
            curveTo(10.5f, 17.7f, 10.3f, 17.1f, 10.3f, 16.5f)
            curveTo(10.3f, 15.1f, 11.4f, 14.0f, 12.8f, 14.0f)
            lineTo(14.5f, 14.0f)
            curveTo(17.5f, 14.0f, 20.0f, 11.5f, 20.0f, 8.5f)
            curveTo(20.0f, 5.5f, 16.4f, 3.0f, 12.0f, 3.0f)
            close()
            moveTo(6.5f, 12.0f)
            curveTo(5.7f, 12.0f, 5.0f, 11.3f, 5.0f, 10.5f)
            curveTo(5.0f, 9.7f, 5.7f, 9.0f, 6.5f, 9.0f)
            curveTo(7.3f, 9.0f, 8.0f, 9.7f, 8.0f, 10.5f)
            curveTo(8.0f, 11.3f, 7.3f, 12.0f, 6.5f, 12.0f)
            close()
            moveTo(9.5f, 8.0f)
            curveTo(8.7f, 8.0f, 8.0f, 7.3f, 8.0f, 6.5f)
            curveTo(8.0f, 5.7f, 8.7f, 5.0f, 9.5f, 5.0f)
            curveTo(10.3f, 5.0f, 11.0f, 5.7f, 11.0f, 6.5f)
            curveTo(11.0f, 7.3f, 10.3f, 8.0f, 9.5f, 8.0f)
            close()
            moveTo(14.5f, 8.0f)
            curveTo(13.7f, 8.0f, 13.0f, 7.3f, 13.0f, 6.5f)
            curveTo(13.0f, 5.7f, 13.7f, 5.0f, 14.5f, 5.0f)
            curveTo(15.3f, 5.0f, 16.0f, 5.7f, 16.0f, 6.5f)
            curveTo(16.0f, 7.3f, 15.3f, 8.0f, 14.5f, 8.0f)
            close()
            moveTo(17.5f, 12.0f)
            curveTo(16.7f, 12.0f, 16.0f, 11.3f, 16.0f, 10.5f)
            curveTo(16.0f, 9.7f, 16.7f, 9.0f, 17.5f, 9.0f)
            curveTo(18.3f, 9.0f, 19.0f, 9.7f, 19.0f, 10.5f)
            curveTo(19.0f, 11.3f, 18.3f, 12.0f, 17.5f, 12.0f)
            close()
        }.build()
        return _paletteIcon!!
    }

private var _languageIcon: ImageVector? = null

private val LanguageIcon: ImageVector
    get() {
        if (_languageIcon != null) return _languageIcon!!
        _languageIcon = ImageVector.Builder(
            name = "Language",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(11.99f, 2.0f)
            curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
            curveTo(2.0f, 17.52f, 6.47f, 22.0f, 11.99f, 22.0f)
            curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
            curveTo(22.0f, 6.48f, 17.52f, 2.0f, 11.99f, 2.0f)
            close()
            moveTo(18.92f, 8.0f)
            lineTo(15.97f, 8.0f)
            curveTo(15.64f, 6.6f, 15.12f, 5.27f, 14.44f, 4.04f)
            curveTo(16.39f, 4.88f, 17.95f, 6.26f, 18.92f, 8.0f)
            close()
            moveTo(12.0f, 4.04f)
            curveTo(12.83f, 5.24f, 13.48f, 6.59f, 13.91f, 8.0f)
            lineTo(10.09f, 8.0f)
            curveTo(10.52f, 6.59f, 11.17f, 5.24f, 12.0f, 4.04f)
            close()
            moveTo(4.26f, 14.0f)
            curveTo(4.1f, 13.36f, 4.0f, 12.69f, 4.0f, 12.0f)
            curveTo(4.0f, 11.31f, 4.1f, 10.64f, 4.26f, 10.0f)
            lineTo(7.64f, 10.0f)
            curveTo(7.55f, 10.65f, 7.5f, 11.32f, 7.5f, 12.0f)
            curveTo(7.5f, 12.68f, 7.55f, 13.35f, 7.64f, 14.0f)
            lineTo(4.26f, 14.0f)
            close()
            moveTo(5.08f, 16.0f)
            lineTo(8.03f, 16.0f)
            curveTo(8.36f, 17.4f, 8.88f, 18.73f, 9.56f, 19.96f)
            curveTo(7.61f, 19.12f, 6.05f, 17.74f, 5.08f, 16.0f)
            close()
            moveTo(8.03f, 8.0f)
            lineTo(5.08f, 8.0f)
            curveTo(6.05f, 6.26f, 7.61f, 4.88f, 9.56f, 4.04f)
            curveTo(8.88f, 5.27f, 8.36f, 6.6f, 8.03f, 8.0f)
            close()
            moveTo(12.0f, 19.96f)
            curveTo(11.17f, 18.76f, 10.52f, 17.41f, 10.09f, 16.0f)
            lineTo(13.91f, 16.0f)
            curveTo(13.48f, 17.41f, 12.83f, 18.76f, 12.0f, 19.96f)
            close()
            moveTo(14.34f, 14.0f)
            lineTo(9.66f, 14.0f)
            curveTo(9.55f, 13.35f, 9.5f, 12.68f, 9.5f, 12.0f)
            curveTo(9.5f, 11.32f, 9.55f, 10.65f, 9.66f, 10.0f)
            lineTo(14.34f, 10.0f)
            curveTo(14.45f, 10.65f, 14.5f, 11.32f, 14.5f, 12.0f)
            curveTo(14.5f, 12.68f, 14.45f, 13.35f, 14.34f, 14.0f)
            close()
            moveTo(14.44f, 19.96f)
            curveTo(15.12f, 18.73f, 15.64f, 17.4f, 15.97f, 16.0f)
            lineTo(18.92f, 16.0f)
            curveTo(17.95f, 17.74f, 16.39f, 19.12f, 14.44f, 19.96f)
            close()
            moveTo(16.36f, 14.0f)
            curveTo(16.45f, 13.35f, 16.5f, 12.68f, 16.5f, 12.0f)
            curveTo(16.5f, 11.32f, 16.45f, 10.65f, 16.36f, 10.0f)
            lineTo(19.74f, 10.0f)
            curveTo(19.9f, 10.64f, 20.0f, 11.31f, 20.0f, 12.0f)
            curveTo(20.0f, 12.69f, 19.9f, 13.36f, 19.74f, 14.0f)
            lineTo(16.36f, 14.0f)
            close()
        }.build()
        return _languageIcon!!
    }

private var _dnsIcon: ImageVector? = null

private val DnsIcon: ImageVector
    get() {
        if (_dnsIcon != null) return _dnsIcon!!
        _dnsIcon = ImageVector.Builder(
            name = "Dns",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(20.0f, 13.0f)
            lineTo(4.0f, 13.0f)
            curveTo(2.9f, 13.0f, 2.0f, 13.9f, 2.0f, 15.0f)
            lineTo(2.0f, 19.0f)
            curveTo(2.0f, 20.1f, 2.9f, 21.0f, 4.0f, 21.0f)
            lineTo(20.0f, 21.0f)
            curveTo(21.1f, 21.0f, 22.0f, 20.1f, 22.0f, 19.0f)
            lineTo(22.0f, 15.0f)
            curveTo(22.0f, 13.9f, 21.1f, 13.0f, 20.0f, 13.0f)
            close()
            moveTo(7.0f, 19.0f)
            curveTo(5.9f, 19.0f, 5.0f, 18.1f, 5.0f, 17.0f)
            curveTo(5.0f, 15.9f, 5.9f, 15.0f, 7.0f, 15.0f)
            curveTo(8.1f, 15.0f, 9.0f, 15.9f, 9.0f, 17.0f)
            curveTo(9.0f, 18.1f, 8.1f, 19.0f, 7.0f, 19.0f)
            close()
            moveTo(20.0f, 3.0f)
            lineTo(4.0f, 3.0f)
            curveTo(2.9f, 3.0f, 2.0f, 3.9f, 2.0f, 5.0f)
            lineTo(2.0f, 9.0f)
            curveTo(2.0f, 10.1f, 2.9f, 11.0f, 4.0f, 11.0f)
            lineTo(20.0f, 11.0f)
            curveTo(21.1f, 11.0f, 22.0f, 10.1f, 22.0f, 9.0f)
            lineTo(22.0f, 5.0f)
            curveTo(22.0f, 3.9f, 21.1f, 3.0f, 20.0f, 3.0f)
            close()
            moveTo(7.0f, 9.0f)
            curveTo(5.9f, 9.0f, 5.0f, 8.1f, 5.0f, 7.0f)
            curveTo(5.0f, 5.9f, 5.0f, 5.0f, 7.0f, 5.0f)
            curveTo(8.1f, 5.0f, 9.0f, 5.9f, 9.0f, 7.0f)
            curveTo(9.0f, 8.1f, 8.1f, 9.0f, 7.0f, 9.0f)
            close()
        }.build()
        return _dnsIcon!!
    }