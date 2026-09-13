package ru.shlyahten.cvt

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import ru.shlyahten.cvt.data.AppSettings
import ru.shlyahten.cvt.ui.PermissionsScreen
import ru.shlyahten.cvt.ui.PermissionsState
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextDecoration
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
    private lateinit var settings: AppSettings

    private val permissionsState = mutableStateOf(PermissionsState())
    private val showPermissionsScreen = mutableStateOf(false)

    private val requestBluetoothPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            updatePermissionsState()
            val btGranted = permissionsState.value.bluetoothGranted
            vm.setHasConnectPermission(btGranted)
            if (btGranted) vm.refreshBondedDevices(this)
        }

    private val requestPostNotificationsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            updatePermissionsState()
        }

    private val requestAllPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            updatePermissionsState()
            val btGranted = permissionsState.value.bluetoothGranted
            vm.setHasConnectPermission(btGranted)
            if (btGranted) vm.refreshBondedDevices(this)
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                vm.refreshBondedDevices(this)
            }
        }

    private fun requestEnableBluetooth() {
        vm.enableBluetooth(this) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            runCatching {
                enableBluetoothLauncher.launch(enableBtIntent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings.getInstance(this)
        vm = ViewModelProvider(this)[MainViewModel::class.java]
        vm.initialize(this)

        updatePermissionsState()

        val needsOnboarding = !settings.isOnboardingCompleted() || !permissionsState.value.bluetoothGranted
        showPermissionsScreen.value = needsOnboarding

        enableEdgeToEdge()
        setContent {
            CVTTheme {
                val currentPermState by permissionsState
                val isShowingPerms by showPermissionsScreen

                if (isShowingPerms) {
                    BackHandler(enabled = currentPermState.canProceed && settings.isOnboardingCompleted()) {
                        showPermissionsScreen.value = false
                    }
                    PermissionsScreen(
                        state = currentPermState,
                        onRequestBluetooth = ::requestBluetooth,
                        onRequestNotifications = ::requestNotifications,
                        onRequestOverlay = ::requestOverlay,
                        onRequestAll = ::requestAllNeededPermissions,
                        onContinue = {
                            settings.setOnboardingCompleted(true)
                            showPermissionsScreen.value = false
                            vm.refreshBondedDevices(this@MainActivity)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    MainScreen(
                        modifier = Modifier.fillMaxSize(),
                        requestBtPermission = ::requestBluetooth,
                        requestEnableBluetooth = ::requestEnableBluetooth,
                        requestOverlayPermission = ::requestOverlay,
                        onOpenPermissions = { showPermissionsScreen.value = true },
                        vm = vm
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsState()
        if (permissionsState.value.bluetoothGranted) {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            if (adapter?.isEnabled == false && settings.isAutoEnableBluetoothEnabled()) {
                requestEnableBluetooth()
            } else {
                vm.refreshBondedDevices(this)
            }
        }
    }

    private fun computePermissionsState(): PermissionsState {
        val btGranted = if (Build.VERSION.SDK_INT >= 31) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        val notifGranted = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val overlayGranted = Settings.canDrawOverlays(this)
        return PermissionsState(
            bluetoothGranted = btGranted,
            notificationsGranted = notifGranted,
            overlayGranted = overlayGranted,
        )
    }

    private fun updatePermissionsState() {
        val state = computePermissionsState()
        permissionsState.value = state
        vm.setHasConnectPermission(state.bluetoothGranted)
    }

    private fun requestBluetooth() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestBluetoothPermissions.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                )
            )
        } else {
            requestBluetoothPermissions.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPostNotificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestOverlay() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        startActivity(intent)
    }

    private fun requestAllNeededPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (perms.isNotEmpty()) {
            requestAllPermissionsLauncher.launch(perms.toTypedArray())
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MainScreen(
    modifier: Modifier = Modifier,
    requestBtPermission: () -> Unit = {},
    requestEnableBluetooth: () -> Unit = {},
    requestOverlayPermission: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
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
                        val (badgeColor, badgeText) = when {
                            state.isDemoMode -> AutoCyan to stringResource(R.string.badge_demo_mode)
                            state.isServiceRunning -> AutoEmerald to stringResource(R.string.badge_service_running)
                            else -> AutoTextSecondary to stringResource(R.string.badge_stopped)
                        }
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
                actions = {
                    OutlinedButton(
                        onClick = onOpenPermissions,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            stringResource(R.string.permissions_topbar_button),
                            color = AutoCyan,
                            fontSize = 12.sp,
                        )
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
                            requestEnableBluetooth = requestEnableBluetooth,
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
                        requestEnableBluetooth = requestEnableBluetooth,
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
        formula == CvtTempFormula.RawCount -> stringResource(R.string.screen_main_zone_raw_data)
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
                    text = stringResource(R.string.screen_main_cvt_fluid_temp_header),
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
                        text = stringResource(R.string.screen_main_raw_count_display, count),
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
                    val statusDotColor = if (state.isDemoMode) AutoCyan else if (state.isConnected) AutoEmerald else if (state.isServiceRunning) AutoAmber else AutoTextSecondary
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.status.isBlank() || state.status.equals("Idle", ignoreCase = true)) stringResource(R.string.status_idle) else state.status,
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
                            text = stringResource(R.string.screen_main_button_start_monitor),
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
                            text = if (state.isDemoMode) stringResource(R.string.screen_main_button_stop_demo) else stringResource(R.string.screen_main_button_stop_monitor),
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
    requestEnableBluetooth: () -> Unit,
    requestOverlayPermission: () -> Unit
) {
    val ctx = LocalContext.current
    var isDeviceSectionCollapsed by rememberSaveable { mutableStateOf(state.isConnected) }

    LaunchedEffect(state.isConnected) {
        if (state.isConnected) {
            isDeviceSectionCollapsed = true
        }
    }

    // Bluetooth disabled card if Bluetooth 2 is turned off
    if (!state.isBluetoothEnabled) {
        Card(
            colors = CardDefaults.cardColors(containerColor = AutoSurfaceCard),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AutoRed)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(AutoRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.screen_main_bt_disabled_title),
                        fontWeight = FontWeight.Bold,
                        color = AutoRed,
                        fontSize = 14.sp
                    )
                }
                Text(
                    stringResource(R.string.screen_main_bt_disabled_desc),
                    color = AutoTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Button(
                    onClick = requestEnableBluetooth,
                    colors = ButtonDefaults.buttonColors(containerColor = AutoCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (state.isBluetoothEnabling) {
                            stringResource(R.string.screen_main_button_enabling_bt)
                        } else {
                            stringResource(R.string.screen_main_button_enable_bt)
                        },
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

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

    var showManualMacDialog by rememberSaveable { mutableStateOf(false) }
    var manualMacInput by rememberSaveable { mutableStateOf("") }
    var manualNameInput by rememberSaveable { mutableStateOf("") }
    var manualMacError by rememberSaveable { mutableStateOf(false) }

    if (showManualMacDialog) {
        AlertDialog(
            onDismissRequest = {
                showManualMacDialog = false
                manualMacError = false
            },
            title = {
                Text(
                    text = stringResource(R.string.screen_main_dialog_mac_title),
                    fontWeight = FontWeight.Bold,
                    color = AutoTextPrimary,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.screen_main_dialog_mac_desc),
                        color = AutoTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    OutlinedTextField(
                        value = manualMacInput,
                        onValueChange = {
                            manualMacInput = it.uppercase()
                            manualMacError = false
                        },
                        label = { Text(stringResource(R.string.screen_main_dialog_mac_label)) },
                        placeholder = { Text("00:1D:A5:68:98:8B") },
                        isError = manualMacError,
                        supportingText = if (manualMacError) {
                            { Text(stringResource(R.string.screen_main_dialog_mac_error), color = AutoRed) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualNameInput,
                        onValueChange = { manualNameInput = it },
                        label = { Text(stringResource(R.string.screen_main_dialog_mac_name_label)) },
                        placeholder = { Text("OBDII") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mac = manualMacInput.trim()
                        val macRegex = Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")
                        if (macRegex.matches(mac)) {
                            vm.addManualDevice(ctx, mac, manualNameInput.takeIf { it.isNotBlank() })
                            showManualMacDialog = false
                            manualMacError = false
                            manualMacInput = ""
                            manualNameInput = ""
                        } else {
                            manualMacError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AutoCyan)
                ) {
                    Text(stringResource(R.string.screen_main_dialog_mac_add), color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showManualMacDialog = false
                    manualMacError = false
                }) {
                    Text(stringResource(R.string.screen_main_dialog_mac_cancel), color = AutoTextSecondary)
                }
            },
            containerColor = AutoSurfaceCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // OBD Adapter Selection (Collapsible after connection)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isDeviceSectionCollapsed = !isDeviceSectionCollapsed }
                        .padding(vertical = 4.dp)
                ) {
                    if (state.isConnected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AutoEmerald)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        stringResource(R.string.screen_main_device_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = AutoTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isDeviceSectionCollapsed) {
                        OutlinedButton(
                            onClick = { vm.refreshBondedDevices(ctx) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.screen_main_button_refresh), color = AutoCyan, fontSize = 12.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = { isDeviceSectionCollapsed = !isDeviceSectionCollapsed },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        val label = if (isDeviceSectionCollapsed) {
                            if (state.isConnected) stringResource(R.string.screen_main_device_button_change)
                            else stringResource(R.string.screen_main_device_button_expand)
                        } else {
                            stringResource(R.string.screen_main_device_button_hide)
                        }
                        val arrow = if (isDeviceSectionCollapsed) "▼" else "▲"
                        Text(
                            text = "$label $arrow",
                            color = AutoCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isDeviceSectionCollapsed) {
                val selectedDevice = state.bondedDevices.find { it.address == state.selectedDeviceAddress }
                val customDev = state.customDevices.find { it.first.equals(state.selectedDeviceAddress, ignoreCase = true) }
                val devDefault = stringResource(R.string.device_default_name)
                val devName = selectedDevice?.name ?: customDev?.second ?: devDefault
                val devAddr = selectedDevice?.address ?: state.selectedDeviceAddress ?: ""
                val prefix = if (state.isConnected) {
                    stringResource(R.string.screen_main_device_connected_prefix)
                } else {
                    stringResource(R.string.screen_main_device_selected_prefix)
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isDeviceSectionCollapsed = false },
                    color = AutoSurfaceCard.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (devAddr.isNotBlank()) "$prefix $devName ($devAddr)" else stringResource(R.string.screen_main_no_paired_devices),
                            color = if (state.isConnected) AutoEmerald else AutoTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            AnimatedVisibility(visible = !isDeviceSectionCollapsed) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Quick Action Buttons: Scan, Enter MAC, System BT Settings
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (state.isScanning) {
                                        vm.stopDiscovery(ctx)
                                    } else {
                                        vm.startDiscovery(ctx)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (state.isScanning) AutoAmber.copy(alpha = 0.15f) else Color.Transparent
                                )
                            ) {
                                Text(
                                    text = if (state.isScanning) stringResource(R.string.screen_main_button_scanning) else stringResource(R.string.screen_main_button_scan),
                                    color = if (state.isScanning) AutoAmber else AutoCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            OutlinedButton(
                                onClick = { showManualMacDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.screen_main_button_add_mac),
                                    color = AutoCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        ctx.startActivity(intent)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.screen_main_button_bt_settings),
                                    color = AutoTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Saved & Paired Devices Chips
                        if (state.bondedDevices.isEmpty()) {
                            Text(
                                stringResource(R.string.screen_main_no_paired_devices),
                                color = AutoTextSecondary,
                                fontSize = 13.sp
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                state.bondedDevices.forEach { dev ->
                                    val selected = dev.address == state.selectedDeviceAddress
                                    val customDev = state.customDevices.find { it.first.equals(dev.address, ignoreCase = true) }
                                    val devDefault = stringResource(R.string.device_default_name)
                                    val displayName = dev.name ?: customDev?.second ?: devDefault
                                    val isCustom = customDev != null

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        FilterChip(
                                            selected = selected,
                                            onClick = { vm.selectDevice(dev.address) },
                                            label = {
                                                Text(
                                                    "$displayName (${dev.address})${if (isCustom) " ★" else ""}",
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = AutoCyan,
                                                selectedLabelColor = Color.Black,
                                                containerColor = AutoSurfaceCard,
                                                labelColor = AutoTextPrimary
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (isCustom) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            TextButton(
                                                onClick = { vm.removeCustomDevice(ctx, dev.address) },
                                            ) {
                                                Text("✕", color = AutoTextSecondary, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Discovered Devices list during Bluetooth scan
                        if (state.discoveredDevices.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.screen_main_discovered_devices_title),
                                color = AutoCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                state.discoveredDevices.forEach { disc ->
                                    val isAlreadySelected = disc.address == state.selectedDeviceAddress
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AutoSurfaceCard)
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = disc.name ?: stringResource(R.string.device_unknown_name),
                                                color = AutoTextPrimary,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "${disc.address}${disc.rssi?.let { " ($it dBm)" } ?: ""}",
                                                color = AutoTextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (!disc.isBonded) {
                                                OutlinedButton(
                                                    onClick = { vm.pairDevice(ctx, disc.device) },
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(stringResource(R.string.screen_main_pair_device_action), fontSize = 11.sp, color = AutoCyan)
                                                }
                                            }
                                            Button(
                                                onClick = {
                                                    vm.addManualDevice(ctx, disc.address, disc.name)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = if (isAlreadySelected) AutoEmerald else AutoCyan),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = if (isAlreadySelected) "✓" else stringResource(R.string.screen_main_device_select_action),
                                                    color = Color.Black,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
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
                    label = { Text(stringResource(R.string.screen_main_cvt_temp_chip_1)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AutoEmerald,
                        selectedLabelColor = Color.Black
                    )
                )
                FilterChip(
                    selected = state.cvtTempFormula == CvtTempFormula.Temp2,
                    onClick = { vm.setFormula(CvtTempFormula.Temp2) },
                    label = { Text(stringResource(R.string.screen_main_cvt_temp_chip_2)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AutoEmerald,
                        selectedLabelColor = Color.Black
                    )
                )
                FilterChip(
                    selected = state.cvtTempFormula == CvtTempFormula.RawCount,
                    onClick = { vm.setFormula(CvtTempFormula.RawCount) },
                    label = { Text(stringResource(R.string.screen_main_cvt_temp_chip_3)) },
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
                stringResource(R.string.screen_main_teyes_settings_title),
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
                    Text(stringResource(R.string.screen_main_grant_overlay_permission))
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

            // Auto-enable Bluetooth Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.screen_main_auto_enable_bt_title),
                        color = AutoTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.screen_main_auto_enable_bt_label),
                        color = AutoTextSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = state.autoEnableBluetoothDesired,
                    onCheckedChange = { vm.setAutoEnableBluetoothDesired(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AutoCyan,
                        checkedTrackColor = AutoCyan.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }

    // Widget Demo Mode Card
    Card(
        colors = CardDefaults.cardColors(containerColor = AutoSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (state.isDemoMode) AutoCyan else AutoBorder
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.screen_main_demo_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = AutoTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.screen_main_demo_desc),
                        color = AutoTextSecondary,
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = state.isDemoMode,
                    onCheckedChange = { active ->
                        val hasOverlayPermission = Settings.canDrawOverlays(ctx)
                        if (active) {
                            if (!hasOverlayPermission) {
                                requestOverlayPermission()
                            }
                            vm.startDemoMode(ctx, cycle = true)
                        } else {
                            vm.stopDemoMode(ctx)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AutoCyan,
                        checkedTrackColor = AutoCyan.copy(alpha = 0.3f)
                    )
                )
            }

            if (state.isDemoMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = state.demoCycleActive,
                        onClick = { vm.setDemoCycle(ctx) },
                        label = { Text(stringResource(R.string.screen_main_demo_cycle)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AutoCyan,
                            selectedLabelColor = Color.Black,
                            containerColor = AutoSurfaceCard,
                            labelColor = AutoTextPrimary
                        )
                    )

                    FilterChip(
                        selected = !state.demoCycleActive && state.demoPresetTemp == 40.0,
                        onClick = { vm.setDemoPresetTemp(ctx, 40.0) },
                        label = { Text(stringResource(R.string.screen_main_demo_preset_cold)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF38BDF8),
                            selectedLabelColor = Color.Black,
                            containerColor = AutoSurfaceCard,
                            labelColor = AutoTextPrimary
                        )
                    )

                    FilterChip(
                        selected = !state.demoCycleActive && state.demoPresetTemp == 75.0,
                        onClick = { vm.setDemoPresetTemp(ctx, 75.0) },
                        label = { Text(stringResource(R.string.screen_main_demo_preset_normal)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF22C55E),
                            selectedLabelColor = Color.Black,
                            containerColor = AutoSurfaceCard,
                            labelColor = AutoTextPrimary
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !state.demoCycleActive && state.demoPresetTemp == 94.0,
                        onClick = { vm.setDemoPresetTemp(ctx, 94.0) },
                        label = { Text(stringResource(R.string.screen_main_demo_preset_warm)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFF59E0B),
                            selectedLabelColor = Color.Black,
                            containerColor = AutoSurfaceCard,
                            labelColor = AutoTextPrimary
                        )
                    )

                    FilterChip(
                        selected = !state.demoCycleActive && state.demoPresetTemp == 106.0,
                        onClick = { vm.setDemoPresetTemp(ctx, 106.0) },
                        label = { Text(stringResource(R.string.screen_main_demo_preset_hot)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFEF4444),
                            selectedLabelColor = Color.White,
                            containerColor = AutoSurfaceCard,
                            labelColor = AutoTextPrimary
                        )
                    )
                }
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

                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.oilDegradation?.let { "$it degr" }
                                ?: stringResource(R.string.screen_main_oil_no_data),
                            color = AutoTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        if (state.oilDegradation != null) {
                            val diff = state.oilDegradationWeeklyDiff ?: 0L
                            val diffText = if (diff > 0) " (+$diff)" else " ($diff)"
                            val diffColor = if (diff > 50) AutoAmber else AutoEmerald
                            Text(
                                text = diffText,
                                color = diffColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    if (state.oilDegradation != null) {
                        Text(
                            text = "${stringResource(R.string.screen_main_oil_weekly_subtitle)} +${state.oilDegradationWeeklyDiff ?: 0}",
                            color = AutoTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
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
                                clipboard?.setPrimaryClip(ClipData.newPlainText(ctx.getString(R.string.screen_main_journal_clipboard_label), logText))
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

    // GitHub Link Footer
    GitHubFooter()
}

@Composable
private fun GitHubFooter(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/shlyahten/CVT/")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    ctx.startActivity(intent)
                } catch (_: Exception) {
                }
            }
        ) {
            Text(
                text = stringResource(R.string.screen_main_github_link),
                color = AutoCyan,
                fontSize = 13.sp,
                textDecoration = TextDecoration.Underline
            )
        }
    }
}

