package ru.shlyahten.cvt

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.shlyahten.cvt.ui.CvtTempFormula
import ru.shlyahten.cvt.ui.MainViewModel
import ru.shlyahten.cvt.ui.theme.CVTTheme

class MainActivity : ComponentActivity() {
    private lateinit var vm: MainViewModel

    private val requestBluetoothPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }
            vm.setHasConnectPermission(allGranted)
            if (allGranted) vm.refreshBondedDevices()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
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
                                    Manifest.permission.BLUETOOTH_SCAN
                                )
                            )
                        }
                    },
                    vm = vm
                )
            }
        }
        // Initialize ViewModel after setting content
        vm.initialize(this)
        val granted = if (Build.VERSION.SDK_INT < 31) {
            true
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        }
        vm.setHasConnectPermission(granted)
        vm.refreshBondedDevices()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MainScreen(
    modifier: Modifier = Modifier,
    requestBtPermission: () -> Unit = {},
    vm: MainViewModel = viewModel(),
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.screen_main_top_bar_title)) })
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            if (Build.VERSION.SDK_INT >= 31 && !state.hasConnectPermission) {
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.screen_main_permission_message))
                        Button(onClick = requestBtPermission) {
                            Text(stringResource(R.string.screen_main_permission_button))
                        }
                    }
                }
            }

            // Devices
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.screen_main_device_title), style = MaterialTheme.typography.titleMedium)

                    if (state.bondedDevices.isEmpty()) {
                        Text(stringResource(R.string.screen_main_no_paired_devices))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.bondedDevices.forEach { d ->
                                val selected = d.address == state.selectedDeviceAddress
                                FilterChip(
                                    selected = selected,
                                    onClick = { vm.selectDevice(d.address) },
                                    label = { Text("${d.name ?: stringResource(R.string.app_name)} (${d.address})") },
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !state.isConnected && !state.isConnecting,
                            onClick = { vm.connect(ctx) },
                        ) {
                            Text(
                                if (state.isConnecting)
                                    stringResource(R.string.screen_main_button_connecting)
                                else
                                    stringResource(R.string.screen_main_button_connect)
                            )
                        }

                        Button(
                            enabled = state.isConnected || state.isConnecting,
                            onClick = { vm.disconnect() },
                        ) {
                            Text(stringResource(R.string.screen_main_button_disconnect))
                        }

                        Button(onClick = { vm.refreshBondedDevices() }) {
                            Text(stringResource(R.string.screen_main_button_refresh))
                        }
                    }

                    Text(stringResource(R.string.screen_main_status_label, state.status))
                }
            }

            // CVT temp
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.screen_main_cvt_temp_title), style = MaterialTheme.typography.titleMedium)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.cvtTempFormula == CvtTempFormula.Temp1,
                            onClick = { vm.setFormula(CvtTempFormula.Temp1) },
                            label = { Text(stringResource(R.string.screen_main_cvt_temp_chip_1)) },
                        )
                        FilterChip(
                            selected = state.cvtTempFormula == CvtTempFormula.Temp2,
                            onClick = { vm.setFormula(CvtTempFormula.Temp2) },
                            label = { Text(stringResource(R.string.screen_main_cvt_temp_chip_2)) },
                        )
                    }

                    val t = state.cvtTempC
                    Text(
                        text = if (t == null)
                            stringResource(R.string.screen_main_cvt_temp_no_data)
                        else
                            stringResource(R.string.screen_main_cvt_temp_value, t),
                        style = MaterialTheme.typography.displaySmall,
                    )
                }
            }

            // Oil
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.screen_main_oil_title), style = MaterialTheme.typography.titleMedium)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = state.isConnected,
                            onClick = { vm.readOilDegradationOnce() }
                        ) {
                            Text(stringResource(R.string.screen_main_oil_button_read))
                        }
                        Text(state.oilDegradation?.toString()
                            ?: stringResource(R.string.screen_main_oil_no_data))
                    }
                }
            }

            // Raw
            state.lastRaw?.takeIf { it.isNotBlank() }?.let { raw ->
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.screen_main_raw_title), style = MaterialTheme.typography.titleMedium)
                        Text(raw)
                    }
                }
            }

            // Log
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.screen_main_journal_title), style = MaterialTheme.typography.titleMedium)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val logText = state.logEntries.joinToString("\n")
                                    if (logText.isNotEmpty()) {
                                        scope.launch {
                                            clipboard.setText(AnnotatedString(logText))
                                        }
                                    }
                                },
                                enabled = state.logEntries.isNotEmpty(),
                            ) {
                                Text(stringResource(R.string.screen_main_journal_button_copy))
                            }

                            Button(
                                onClick = vm::clearLogPublic,
                                enabled = state.logEntries.isNotEmpty(),
                            ) {
                                Text(stringResource(R.string.screen_main_journal_button_clear))
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(state.logEntries) { entry ->
                            Text(
                                text = entry,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CVTTheme {
        MainScreen(
            modifier = Modifier.fillMaxSize(),
            requestBtPermission = {},
            vm = viewModel()
        )
    }
}
