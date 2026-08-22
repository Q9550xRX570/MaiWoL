package com.mai.wol.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mai.wol.R
import com.mai.wol.data.AppDatabase
import com.mai.wol.data.DeviceEntity
import com.mai.wol.ui.theme.MaiWoLTheme
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class DeviceWidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            val scope = rememberCoroutineScope()
            var deviceList by remember { mutableStateOf<List<DeviceEntity>>(emptyList()) }
            var isLoading by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                val db = AppDatabase.getDatabase(applicationContext)
                deviceList = db.deviceDao().getAllDevices().firstOrNull() ?: emptyList()
                isLoading = false
            }

            MaiWoLTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.select_device_for_widget)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                                }
                            }
                        )
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else if (deviceList.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_devices_for_schedule),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(deviceList, key = { it.id }) { device ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                scope.launch {
                                                    val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                                                    prefs.edit().putLong("widget_device_$appWidgetId", device.id).apply()

                                                    val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                                                    DeviceWidgetProvider.updateAppWidget(applicationContext, appWidgetManager, appWidgetId)
                                                    DeviceIconWidgetProvider.updateAppWidget(applicationContext, appWidgetManager, appWidgetId)

                                                    val resultValue = Intent().apply {
                                                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                                    }
                                                    setResult(Activity.RESULT_OK, resultValue)
                                                    finish()
                                                }
                                            },
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Computer,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column {
                                                Text(
                                                    text = device.name,
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = device.macAddress,
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
            }
        }
    }
}