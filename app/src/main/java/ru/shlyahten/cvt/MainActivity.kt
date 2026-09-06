package ru.shlyahten.cvt

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.shlyahten.cvt.ui.CvtTempFormula
import ru.shlyahten.cvt.ui.MainViewModel
import ru.shlyahten.cvt.ui.theme.AutoAmber
import ru.shlyahten.cvt.ui.theme.AutoBackground
import ru.shlyahten.cvt.ui.theme.AutoBorder
import ru.shlyahten.cvt.ui.theme.AutoCyan
import ru.shlyahten.cvt.ui.theme.AutoEmerald
import ru.shlyahten.cvt.ui.theme.AutoRed
import ru.shlyahten.cvt.ui.theme.AutoSurface
import ru.shlyahten.cvt.ui.theme.AutoSurfaceCard
import ru.shlyahten.cvt.ui.theme.AutoTextPrimary
import ru.shlyahten.cvt.ui.theme.AutoTextSecondary
import ru.shlyahten.cvt.ui.theme.CVTTheme

class MainActivity : ComponentActivity() {
    private lateinit var vm: MainViewModel

    private val requestBluetoothPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }
            vm.setHasConnectPermission(allGranted)
            if (allGranted) vm.refreshBondedDevices(this)
        }

    private val requestPostNotificationsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                vm.setFloatingOverlayDesired(this, false)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this)[MainViewModel::class.java]
        vm.initialize(this)

        enableEdgeToEdge()
        setContent {
            CVTTheme {
                MainScreen(
                    modifier = Modifier.fillMaxSize(),
                    requestBtPermission = {
                        if (Build.VERSION.SDK_INT >= 31) {
                            requestBluetoothPermissions.launch(
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                    Manifest.permission.BLUETOOTH_SCAN,
                                )
                            )
                        }
                    },
                    requestOverlayPermission = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"),
                        )
                        startActivity(intent)
                    },
                    vm = vm
                )
            }
        }

        checkInitialPermissions()
    }

    private fun checkInitialPermissions() {
        val granted = if (Build.VERSION.SDK_INT < 31) {
            true
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        }
        vm.setHasConnectPermission(granted)
        vm.refreshBondedDevices(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPostNotificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MainScreen(
    modifier: Modifier = Modifier,
    requestBtPermission: () -> Unit = {},
    requestOverlayPermission: () -> Unit = {},
    vm: MainViewModel = viewModel(),
) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()

    Scaffold(
        modifier = modifier.background(AutoBackground),
        containerColor = AutoBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.screen_main_top_bar_title),
                            fontWeight = FontWeight.Bold,
                            color = AutoTextPrimary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        // Service indicator badge
                        val badgeColor = if (state.isServiceRunning) AutoEmerald else AutoTextSecondary
                        val badgeText = if (state.isServiceRunning) "BG SERVICE ACTIVE" else "STOPPED"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeColor.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                badgeText,
                                color = badgeColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AutoSurface,
                    titleContentColor = AutoTextPrimary,
                )
            )
        },
    ) { inner ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(12.dp)
        ) {
            val isLandscape = maxWidth >= 600.dp

            if (isLandscape) {
                // Two-column layout for Teyes landscape screen
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Column: Live Gauge and Quick Actions
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TemperatureDashboardCard(
                            state = state,
                            onConnect = { vm.connect(ctx) },
                            onDisconnect = { vm.disconnect(ctx) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Right Column: Controls, Settings & Log
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ControlsAndSettingsSection(
                            state = state,
                            vm = vm,
                            requestBtPermission = requestBtPermission,
                            requestOverlayPermission = requestOverlayPermission
                        )
                    }
                }
            } else {
                // Single column layout for portrait
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TemperatureDashboardCard(
                        state = state,
                        onConnect = { vm.connect(ctx) },
                        onDisconnect = { vm.disconnect(ctx) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ControlsAndSettingsSection(
                        state = state,
                        vm = vm,
                        requestBtPermission = requestBtPermission,
                        requestOverlayPermission = requestOverlayPermission
                    )
                }
            }
        }
    }
}

@Composable
private fun TemperatureDashboardCard(
    state: ru.shlyahten.cvt.ui.UiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val temp = state.cvtTempC
    val formula = state.cvtTempFormula

    val zoneColor by animateColorAsState(
        targetValue = when {
            temp == null -> AutoTextSecondary
            formula == CvtTempFormula.RawCount -> AutoCyan
            temp < 50.0 -> AutoCyan
            temp in 50.0..89.9 -> AutoEmerald
            temp in 90.0..99.9 -> AutoAmber
            else -> AutoRed
        },
        label = "zoneColor"
    )

    val zoneLabel = when {
        temp == null -> "—"
        formula == CvtTempFormula.RawCount -> "RAW DATA"
        temp < 50.0 -> stringResource(R.string.screen_main_temp_zone_cold)
        temp in 50.0..89.9 -> stringResource(R.string.screen_main_temp_zone_normal)
        temp in 90.0..99.9 -> stringResource(R.string.screen_main_temp_zone_warm)
        else -> stringResource(R.string.screen_main_temp_zone_hot)
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = AutoSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, zoneColor.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header / Zone badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CVT FLUID TEMPERATURE",
                    color = AutoTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(zoneColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = zoneLabel,
                        color = zoneColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Digital Readout
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val tempText = when {
                    temp == null -> "--.-"
                    formula == CvtTempFormula.RawCount -> "${temp.toInt()}"
                    else -> String.format("%.1f", temp)
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = tempText,
                        color = zoneColor,
                        fontSize = 68.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (formula == CvtTempFormula.RawCount) "cnt" else "°C",
                        color = AutoTextSecondary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                state.cvtTempCount?.let { count ->
                    Text(
                        text = "Raw Count N = $count",
                        color = AutoTextSecondary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Connection status line
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val statusDotColor = if (state.isConnected) AutoEmerald else if (state.isServiceRunning) AutoAmber else AutoTextSecondary
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.status,
                        color = AutoTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!state.isServiceRunning) {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AutoCyan),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "START MONITOR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.Black
                        )
                    }
                } else {
                    Button(
                        onClick = onDisconnect,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AutoRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "STOP MONITOR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlsAndSettingsSection(
    state: ru.shlyahten.cvt.ui.UiState,
    vm: MainViewModel,
    requestBtPermission: () -> Unit,
    requestOverlayPermission: () -> Unit
) {
    val ctx = LocalContext.current

    // Bluetooth permission card if needed
    if (Build.VERSION.SDK_INT >= 31 && !state.hasConnectPermission) {
        Card(
            colors = CardDefaults.cardColors(containerColor = AutoSurfaceCard),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AutoAmber)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.screen_main_permission_message),
                    color = AutoTextPrimary
                )
                Button(
                    onClick = requestBtPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = AutoAmber)
                ) {
                    Text(stringResource(R.string.screen_main_permission_button), color = Color.Black)
                }
            }
        }
    }

    // OBD Adapter Selection
    Card(
        colors = CardDefaults.cardColors(containerColor = AutoSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AutoBorder)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.screen_main_device_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = AutoTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = { vm.refreshBondedDevices(ctx) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.screen_main_button_refresh), color = AutoCyan)
                }
            }

            if (state.bondedDevices.isEmpty()) {
                Text(
                    stringResource(R.string.screen_main_no_paired_devices),
                    color = AutoTextSecondary,
                    fontSize = 13.sp
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.bondedDevices.forEach { dev ->
                        val selected = dev.address == state.selectedDeviceAddress
                        FilterChip(
                            selected = selected,
                            onClick = { vm.selectDevice(dev.address) },
                            label = {
                                Text(
                                    "${dev.name ?: "OBD Device"} (${dev.address})",
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AutoCyan,
                                selectedLabelColor = Color.Black,
                                containerColor = AutoSurfaceCard,
                                labelColor = AutoTextPrimary
                            )
                        )
                    }
                }
            }
        }
    }

    // Formula Selection
    Card(
        colors = CardDefaults.cardColors(containerColor = AutoSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AutoBorder)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.screen_main_cvt_temp_title),
                style = MaterialTheme.typography.titleMedium,
                color = AutoTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.cvtTempFormula == CvtTempFormula.Temp1,
                    onClick = { vm.setFormula(CvtTempFormula.Temp1) },
                    label = { Text("Temp 1 (PIDs.csv)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AutoEmerald,
                        selectedLabelColor = Color.Black
                    )
                )
                FilterChip(
                    selected = state.cvtTempFormula == CvtTempFormula.Temp2,
                    onClick = { vm.setFormula(CvtTempFormula.Temp2) },
                    label = { Text("Temp 2 (Cubic)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AutoEmerald,
                        selectedLabelColor = Color.Black
                    )
                )
                FilterChip(
                    selected = state.cvtTempFormula == CvtTempFormula.RawCount,
                    onClick = { vm.setFormula(CvtTempFormula.RawCount) },
                    label = { Text("Raw N") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AutoEmerald,
                        selectedLabelColor = Color.Black
                    )
                )
            }
        }
    }

    // Floating Widget & Teyes Autostart Settings
    Card(
        colors = CardDefaults.cardColors(containerColor = AutoSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AutoBorder)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Teyes Settings & Overlay",
                style = MaterialTheme.typography.titleMedium,
                color = AutoTextPrimary,
                fontWeight = FontWeight.Bold
            )

            // Floating Overlay Switch
            val hasOverlayPermission = Settings.canDrawOverlays(ctx)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.screen_main_floating_widget_title),
                        color = AutoTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.screen_main_floating_widget_label),
                        color = AutoTextSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = state.floatingOverlayDesired,
                    onCheckedChange = { enabled ->
                        if (enabled && !hasOverlayPermission) {
                            requestOverlayPermission()
                        } else {
                            vm.setFloatingOverlayDesired(ctx, enabled)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AutoCyan,
                        checkedTrackColor = AutoCyan.copy(alpha = 0.3f)
                    )
                )
            }

            if (state.floatingOverlayDesired && !hasOverlayPermission) {
                OutlinedButton(
                    onClick = requestOverlayPermission,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AutoAmber)
                ) {
                    Text("Grant Overlay Permission (SYSTEM_ALERT_WINDOW)")
                }
            }

            // Autostart on boot Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.screen_main_autostart_title),
                        color = AutoTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.screen_main_autostart_label),
                        color = AutoTextSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = state.autostartDesired,
                    onCheckedChange = { vm.setAutostartDesired(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AutoEmerald,
                        checkedTrackColor = AutoEmerald.copy(alpha = 0.3f)
                    )
                )
            }

            // Autoconnect Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.screen_main_autoconnect_title),
                        color = AutoTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.screen_main_autoconnect_label),
                        color = AutoTextSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = state.autoconnectDesired,
                    onCheckedChange = { vm.setAutoconnectDesired(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AutoEmerald,
                        checkedTrackColor = AutoEmerald.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }

    // Oil Degradation (PID 2110)
    Card(
        colors = CardDefaults.cardColors(containerColor = AutoSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AutoBorder)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.screen_main_oil_title),
                style = MaterialTheme.typography.titleMedium,
                color = AutoTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    enabled = state.isConnected,
                    onClick = { vm.readOilDegradationOnce(ctx) },
                    colors = ButtonDefaults.buttonColors(containerColor = AutoCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.screen_main_oil_button_read), color = Color.Black)
                }
                Text(
                    text = state.oilDegradation?.let { "$it degr" }
                        ?: stringResource(R.string.screen_main_oil_no_data),
                    color = AutoTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }

    // Diagnostic Log Card
    Card(
        colors = CardDefaults.cardColors(containerColor = AutoSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AutoBorder)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.screen_main_journal_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = AutoTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val logText = state.logEntries.joinToString("\n")
                            if (logText.isNotEmpty()) {
                                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("CVT Log", logText))
                            }
                        },
                        enabled = state.logEntries.isNotEmpty(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.screen_main_journal_button_copy), color = AutoCyan)
                    }

                    OutlinedButton(
                        onClick = vm::clearLogPublic,
                        enabled = state.logEntries.isNotEmpty(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.screen_main_journal_button_clear), color = AutoTextSecondary)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AutoBackground)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.logEntries) { entry ->
                    Text(
                        text = entry,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = AutoTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

