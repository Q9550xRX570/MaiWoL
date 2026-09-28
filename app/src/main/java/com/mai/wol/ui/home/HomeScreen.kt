package com.mai.wol.ui.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mai.wol.data.DeviceEntity
import com.mai.wol.network.StatusResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner

import com.mai.wol.R
import com.mai.wol.MainViewModel
import com.mai.wol.ui.device.AddOrEditDeviceDialog
import com.mai.wol.ui.schedule.DeviceSchedulesDialog
import com.mai.wol.ui.settings.AdvancedFeaturesScreen
import com.mai.wol.ui.settings.CardCustomizationDialog
import com.mai.wol.ui.settings.GroupCustomizationDialog
import com.mai.wol.ui.settings.LanguageSelectionDialog
import com.mai.wol.ui.settings.PacketCountSettingsDialog
import com.mai.wol.ui.settings.StatisticsDialog
import com.mai.wol.ui.settings.StatusIntervalSettingsDialog
import com.mai.wol.ui.settings.ThemeSelectionDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onRequestShizukuPermission: () -> Unit,
    currentTheme: String,
    onThemeChange: (String) -> Unit
) {
    val devices by viewModel.devices.collectAsState()
    val customGroups by viewModel.customGroups.collectAsState()
    val hideGroupCounts by viewModel.hideGroupCounts.collectAsState()
    val showBatchWakeButton by viewModel.showBatchWakeButton.collectAsState()
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences("wol_settings", Context.MODE_PRIVATE) }
    val appLanguage = remember { prefs.getString("app_language", "") ?: "" }

    var selectedGroup by remember { mutableStateOf("") }
    val allGroups = remember(devices, customGroups) {
        val deviceGroups = devices.map { it.groupName.trim() }.filter { it.isNotBlank() }
        val ordered = mutableListOf<String>()
        customGroups.forEach { g -> if (g.isNotBlank() && !ordered.contains(g)) ordered.add(g) }
        deviceGroups.forEach { g -> if (g.isNotBlank() && !ordered.contains(g)) ordered.add(g) }
        ordered.toList()
    }

    var currentGroupsList by remember(allGroups) { mutableStateOf(allGroups) }
    var draggedGroupIndex by remember { mutableStateOf<Int?>(null) }
    var dragGroupAccumulatedOffset by remember { mutableFloatStateOf(0f) }

    fun moveGroupItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex in currentGroupsList.indices && toIndex in currentGroupsList.indices) {
            currentGroupsList = currentGroupsList.toMutableList().apply {
                val item = removeAt(fromIndex)
                add(toIndex, item)
            }
        }
    }

    var showAddGroupDialog by remember { mutableStateOf(false) }
    var groupToManage by remember { mutableStateOf<String?>(null) }
    var deviceToChangeGroup by remember { mutableStateOf<DeviceEntity?>(null) }
    var deviceToShutdown by remember { mutableStateOf<DeviceEntity?>(null) }

    LaunchedEffect(devices, statusCheckInterval, lifecycleOwner) {
        if (statusCheckInterval > 0) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    if (devices.isNotEmpty()) {
                        viewModel.checkAllDevicesStatus(context, devices)
                    }
                    delay(statusCheckInterval.toLong().coerceAtLeast(1000L))
                }
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

    val filteredDevices = remember(currentList, searchQuery, selectedGroup) {
        val trimmedQuery = searchQuery.trim()
        val groupFiltered = if (selectedGroup.isBlank()) {
            currentList
        } else {
            currentList.filter { it.groupName.equals(selectedGroup, ignoreCase = true) }
        }

        if (trimmedQuery.isEmpty()) {
            groupFiltered
        } else {
            groupFiltered.filter { device ->
                device.name.contains(trimmedQuery, ignoreCase = true) ||
                        device.macAddress.contains(trimmedQuery, ignoreCase = true) ||
                        device.localIp.contains(trimmedQuery, ignoreCase = true) ||
                        device.ipAddress.contains(trimmedQuery, ignoreCase = true) ||
                        device.groupName.contains(trimmedQuery, ignoreCase = true)
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
    var showWakeAllConfirmDialog by remember { mutableStateOf(false) }
    var showPacketCountSettingsDialog by remember { mutableStateOf(false) }
    var showStatusIntervalDialog by remember { mutableStateOf(false) }
    var showGroupCustomizationDialog by remember { mutableStateOf(false) }
    var showCardCustomizationDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showStatisticsDialog by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

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
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings),
                                modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 12.dp),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            // 1. WEB SİTESİ
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maiwol.com"))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.website), style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(stringResource(R.string.website_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("maiwol.com", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }

                            // 2. WOL PAKET SAYISI
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showPacketCountSettingsDialog = true }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.wol_packet_count), style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(stringResource(R.string.packet_count_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(stringResource(R.string.packets_format, packetCount), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            // 3. DURUM YOKLAMA SIKLIĞI
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showStatusIntervalDialog = true }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.status_check_interval), style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(stringResource(R.string.status_check_interval_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (statusCheckInterval == 0) stringResource(R.string.disabled)
                                        else stringResource(R.string.interval_ms_format, statusCheckInterval, statusCheckInterval / 1000f),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (statusCheckInterval == 0) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

                            // 4. GRUP ÖZELLEŞTİRME
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showGroupCustomizationDialog = true }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.group_customization), style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(stringResource(R.string.group_customization_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // 5. KART ÖZELLEŞTİRME
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCardCustomizationDialog = true }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.card_customization), style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(stringResource(R.string.card_customization_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // 6. UYGULAMA TEMASI
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showThemeDialog = true }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.app_theme), style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(stringResource(R.string.app_theme_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(currentThemeText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            // 7. UYGULAMA DİLİ
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showLanguageDialog = true }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.app_language), style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(stringResource(R.string.app_language_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(currentLangText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            // 8. İSTATİSTİKLER
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showStatisticsDialog = true }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.statistics), style = MaterialTheme.typography.titleMedium)
                                }
                            }

                            // 9. GELİŞMİŞ ÖZELLİKLER
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
                                Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.advanced_features), style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(stringResource(R.string.advanced_features_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            title = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    LazyRow(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        item {
                                            val isAllSelected = selectedGroup.isEmpty()
                                            val allLabel = if (hideGroupCounts) {
                                                stringResource(R.string.group_all)
                                            } else {
                                                "${stringResource(R.string.group_all)} (${devices.size})"
                                            }

                                            FilterChip(
                                                selected = isAllSelected,
                                                onClick = { selectedGroup = "" },
                                                label = {
                                                    Text(
                                                        text = allLabel,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            )
                                        }

                                        itemsIndexed(currentGroupsList, key = { _, groupName -> groupName }) { index, groupName ->
                                            val isDragging = (draggedGroupIndex == index)
                                            val isSelected = selectedGroup.equals(groupName, ignoreCase = true)
                                            val count = devices.count { it.groupName.equals(groupName, ignoreCase = true) }
                                            val groupLabel = if (hideGroupCounts) groupName else "$groupName ($count)"

                                            val groupElevation by animateDpAsState(
                                                targetValue = if (isDragging) 6.dp else 0.dp,
                                                label = "groupElevation"
                                            )
                                            val groupScale by animateFloatAsState(
                                                targetValue = if (isDragging) 1.08f else 1.0f,
                                                label = "groupScale"
                                            )

                                            val density = LocalContext.current.resources.displayMetrics.density
                                            val swapThresholdX = 65f * density

                                            Box(
                                                modifier = Modifier
                                                    .zIndex(if (isDragging) 10f else 1f)
                                                    .graphicsLayer {
                                                        scaleX = groupScale
                                                        scaleY = groupScale
                                                        translationX = if (isDragging) dragGroupAccumulatedOffset else 0f
                                                    }
                                                    .shadow(groupElevation, shape = CircleShape)
                                                    .pointerInput(Unit) {
                                                        detectDragGesturesAfterLongPress(
                                                            onDragStart = {
                                                                draggedGroupIndex = index
                                                                dragGroupAccumulatedOffset = 0f
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                dragGroupAccumulatedOffset += dragAmount.x

                                                                if (dragGroupAccumulatedOffset > swapThresholdX && index < currentGroupsList.size - 1) {
                                                                    moveGroupItem(index, index + 1)
                                                                    draggedGroupIndex = index + 1
                                                                    dragGroupAccumulatedOffset -= swapThresholdX
                                                                } else if (dragGroupAccumulatedOffset < -swapThresholdX && index > 0) {
                                                                    moveGroupItem(index, index - 1)
                                                                    draggedGroupIndex = index - 1
                                                                    dragGroupAccumulatedOffset += swapThresholdX
                                                                }
                                                            },
                                                            onDragEnd = {
                                                                draggedGroupIndex = null
                                                                dragGroupAccumulatedOffset = 0f
                                                                viewModel.updateGroupsOrder(currentGroupsList)
                                                            },
                                                            onDragCancel = {
                                                                draggedGroupIndex = null
                                                                dragGroupAccumulatedOffset = 0f
                                                            }
                                                        )
                                                    }
                                            ) {
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = { selectedGroup = groupName },
                                                    label = {
                                                        Text(
                                                            text = groupLabel,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    },
                                                    trailingIcon = {
                                                        Icon(
                                                            imageVector = Icons.Default.MoreVert,
                                                            contentDescription = null,
                                                            modifier = Modifier
                                                                .size(14.dp)
                                                                .clickable { groupToManage = groupName }
                                                        )
                                                    }
                                                )
                                            }
                                        }

                                        item {
                                            IconButton(
                                                onClick = { showAddGroupDialog = true },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = stringResource(R.string.add_group),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            actions = {
                                if (showBatchWakeButton) {
                                    IconButton(
                                        onClick = {
                                            val targetDevices = if (selectedGroup.isBlank()) {
                                                devices
                                            } else {
                                                devices.filter { it.groupName.equals(selectedGroup, ignoreCase = true) }
                                            }

                                            if (targetDevices.isEmpty()) {
                                                Toast.makeText(context, context.getString(R.string.no_devices_in_group), Toast.LENGTH_SHORT).show()
                                            } else {
                                                showWakeAllConfirmDialog = true
                                            }
                                        }
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                            contentDescription = stringResource(R.string.wake_all_in_group),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
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
                                                status = deviceStatuses[device.id] ?: StatusResult.CHECKING,
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
                                                onSendShutdown = {
                                                    deviceToShutdown = device
                                                },
                                                onChangeGroupRequest = { deviceToChangeGroup = device },
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
                            existingGroups = allGroups,
                            defaultGroup = selectedGroup,
                            onDismiss = { showAddDialog = false },
                            onConfirm = { newDevice ->
                                viewModel.addDevice(newDevice)
                                showAddDialog = false
                            }
                        )
                    }

                    deviceToEdit?.let { device ->
                        AddOrEditDeviceDialog(
                            deviceToEdit = device,
                            useShizuku = isShizukuEnabled,
                            existingGroups = allGroups,
                            defaultGroup = device.groupName,
                            onDismiss = { deviceToEdit = null },
                            onConfirm = { updatedDevice ->
                                viewModel.updateDevice(updatedDevice)
                                deviceToEdit = null
                            }
                        )
                    }

                    deviceToShutdown?.let { dev ->
                        AlertDialog(
                            onDismissRequest = { deviceToShutdown = null },
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.shutdown_confirm_title))
                                }
                            },
                            text = {
                                Text(stringResource(R.string.shutdown_confirm_desc, dev.name))
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        val target = dev
                                        deviceToShutdown = null
                                        viewModel.sendShutdown(target) { success, msg ->
                                            val text = if (success) {
                                                context.getString(R.string.shutdown_success, target.name)
                                            } else {
                                                context.getString(R.string.shutdown_error, msg ?: "")
                                            }
                                            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                                            viewModel.refreshDeviceStatus(context, target)
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.shutdown), color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { deviceToShutdown = null }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    }

                    if (showGroupCustomizationDialog) {
                        GroupCustomizationDialog(
                            hideGroupCounts = hideGroupCounts,
                            showBatchWakeButton = showBatchWakeButton,
                            onDismiss = { showGroupCustomizationDialog = false },
                            onToggleHideCounts = { hide ->
                                viewModel.updateHideGroupCounts(hide)
                            },
                            onToggleShowBatchWakeButton = { show ->
                                viewModel.updateShowBatchWakeButton(show)
                            }
                        )
                    }

                    if (showWakeAllConfirmDialog) {
                        val targetDevices = remember(devices, selectedGroup) {
                            if (selectedGroup.isBlank()) {
                                devices
                            } else {
                                devices.filter { it.groupName.equals(selectedGroup, ignoreCase = true) }
                            }
                        }
                        val groupDisplayName = if (selectedGroup.isBlank()) stringResource(R.string.group_all) else selectedGroup

                        AlertDialog(
                            onDismissRequest = { showWakeAllConfirmDialog = false },
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.wake_all_confirm_title))
                                }
                            },
                            text = {
                                Text(stringResource(R.string.wake_all_confirm_desc, targetDevices.size, groupDisplayName))
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showWakeAllConfirmDialog = false
                                        viewModel.wakeAllDevices(targetDevices) { successCount, _ ->
                                            val msg = if (successCount > 0) {
                                                context.getString(R.string.wake_all_success, successCount)
                                            } else {
                                                context.getString(R.string.packet_sent_error, "Hata")
                                            }
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            viewModel.checkAllDevicesStatus(context, targetDevices)
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.yes))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showWakeAllConfirmDialog = false }) {
                                    Text(stringResource(R.string.no))
                                }
                            }
                        )
                    }

                    if (showAddGroupDialog) {
                        var newGroupName by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { showAddGroupDialog = false },
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.add_group))
                                }
                            },
                            text = {
                                OutlinedTextField(
                                    value = newGroupName,
                                    onValueChange = { newGroupName = it },
                                    label = { Text(stringResource(R.string.group_name)) },
                                    placeholder = { Text("Ev, Ofis, Sunucular...") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        val trimmed = newGroupName.trim()
                                        if (trimmed.isNotBlank()) {
                                            viewModel.addGroup(trimmed)
                                            selectedGroup = trimmed
                                            Toast.makeText(context, context.getString(R.string.group_created, trimmed), Toast.LENGTH_SHORT).show()
                                        }
                                        showAddGroupDialog = false
                                    }
                                ) {
                                    Text(stringResource(R.string.add))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showAddGroupDialog = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    }

                    groupToManage?.let { groupName ->
                        var showRenameDialog by remember { mutableStateOf(false) }
                        var showDeleteDialog by remember { mutableStateOf(false) }

                        AlertDialog(
                            onDismissRequest = { groupToManage = null },
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(groupName)
                                }
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.rename_group)) },
                                        leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                                        modifier = Modifier.clickable { showRenameDialog = true }
                                    )
                                    ListItem(
                                        headlineContent = { Text(stringResource(R.string.delete_group), color = MaterialTheme.colorScheme.error) },
                                        leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        modifier = Modifier.clickable { showDeleteDialog = true }
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { groupToManage = null }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )

                        if (showRenameDialog) {
                            var renameInput by remember { mutableStateOf(groupName) }
                            AlertDialog(
                                onDismissRequest = {
                                    showRenameDialog = false
                                    groupToManage = null
                                },
                                title = { Text(stringResource(R.string.rename_group)) },
                                text = {
                                    OutlinedTextField(
                                        value = renameInput,
                                        onValueChange = { renameInput = it },
                                        label = { Text(stringResource(R.string.group_name)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            val trimmed = renameInput.trim()
                                            if (trimmed.isNotBlank() && trimmed != groupName) {
                                                viewModel.renameGroup(groupName, trimmed)
                                                if (selectedGroup == groupName) {
                                                    selectedGroup = trimmed
                                                }
                                                Toast.makeText(context, context.getString(R.string.group_renamed, trimmed), Toast.LENGTH_SHORT).show()
                                            }
                                            showRenameDialog = false
                                            groupToManage = null
                                        }
                                    ) {
                                        Text(stringResource(R.string.save))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = {
                                        showRenameDialog = false
                                        groupToManage = null
                                    }) {
                                        Text(stringResource(R.string.cancel))
                                    }
                                }
                            )
                        }

                        if (showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = {
                                    showDeleteDialog = false
                                    groupToManage = null
                                },
                                title = { Text(stringResource(R.string.delete_group)) },
                                text = { Text(stringResource(R.string.delete_group_confirm, groupName)) },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            viewModel.deleteGroup(groupName)
                                            if (selectedGroup == groupName) {
                                                selectedGroup = ""
                                            }
                                            Toast.makeText(context, context.getString(R.string.group_deleted, groupName), Toast.LENGTH_SHORT).show()
                                            showDeleteDialog = false
                                            groupToManage = null
                                        }
                                    ) {
                                        Text(stringResource(R.string.yes), color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = {
                                        showDeleteDialog = false
                                        groupToManage = null
                                    }) {
                                        Text(stringResource(R.string.no))
                                    }
                                }
                            )
                        }
                    }

                    deviceToChangeGroup?.let { device ->
                        var selectedGroupName by remember { mutableStateOf(device.groupName) }

                        AlertDialog(
                            onDismissRequest = { deviceToChangeGroup = null },
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.change_group))
                                }
                            },
                            text = {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(R.string.select_or_create_group),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    OutlinedTextField(
                                        value = selectedGroupName,
                                        onValueChange = { selectedGroupName = it },
                                        label = { Text(stringResource(R.string.group_name_optional)) },
                                        placeholder = { Text(stringResource(R.string.no_group)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        FilterChip(
                                            selected = selectedGroupName.isEmpty(),
                                            onClick = { selectedGroupName = "" },
                                            label = { Text(stringResource(R.string.no_group), style = MaterialTheme.typography.labelSmall) }
                                        )
                                        allGroups.forEach { grp ->
                                            val isSel = selectedGroupName.equals(grp, ignoreCase = true)
                                            FilterChip(
                                                selected = isSel,
                                                onClick = { selectedGroupName = grp },
                                                label = { Text(grp, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.updateDeviceGroup(device.id, selectedGroupName.trim())
                                        deviceToChangeGroup = null
                                    }
                                ) {
                                    Text(stringResource(R.string.save))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { deviceToChangeGroup = null }) {
                                    Text(stringResource(R.string.cancel))
                                }
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
