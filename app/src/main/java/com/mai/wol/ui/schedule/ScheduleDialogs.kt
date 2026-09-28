package com.mai.wol.ui.schedule

import android.app.DatePickerDialog
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mai.wol.automation.AlarmScheduler
import com.mai.wol.data.DeviceEntity
import com.mai.wol.data.ScheduleEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import com.mai.wol.R
import com.mai.wol.MainViewModel

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
