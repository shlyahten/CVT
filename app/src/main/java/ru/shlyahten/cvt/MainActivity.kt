package ru.shlyahten.cvt

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.shlyahten.cvt.R
import ru.shlyahten.cvt.ui.CvtTempFormula
import ru.shlyahten.cvt.ui.MainViewModel
import ru.shlyahten.cvt.ui.theme.CVTTheme

class MainActivity : ComponentActivity() {
    private lateinit var vm: MainViewModel

    private val requestBluetoothPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }
            vm.setHasConnectPermission(allGranted)
            if (allGranted) {
                vm.refreshBondedDevices()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
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
                                    Manifest.permission.BLUETOOTH_SCAN
                                )
                            )
                        }
                    },
                    vm = vm
                )
            }
        }
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
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        vm.initialize(ctx)
        val granted = if (Build.VERSION.SDK_INT < 31) {
            true
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        }
        vm.setHasConnectPermission(granted)
        vm.refreshBondedDevices()
    }

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
                        Button(onClick = requestBtPermission) { Text(stringResource(R.string.screen_main_permission_button)) }
                    }
                }
            }

            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.screen_main_device_title), style = MaterialTheme.typography.titleMedium)
                    if (state.bondedDevices.isEmpty()) {
                        Text(stringResource(R.string.screen_main_no_paired_devices))
                    } else {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            enabled = !state.isConnected && !state.isConnecting,
                            onClick = { vm.connect(ctx) },
                        ) { Text(if (state.isConnecting) stringResource(R.string.screen_main_button_connecting) else stringResource(R.string.screen_main_button_connect)) }
                        Button(
                            enabled = state.isConnected || state.isConnecting,
                            onClick = { vm.disconnect() },
                        ) { Text(stringResource(R.string.screen_main_button_disconnect)) }
                        Button(onClick = { vm.refreshBondedDevices() }) { Text(stringResource(R.string.screen_main_button_refresh)) }
                    }
                    Text(stringResource(R.string.screen_main_status_label, state.status))
                }
            }

            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.screen_main_cvt_temp_title), style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                        text = if (t == null) stringResource(R.string.screen_main_cvt_temp_no_data) else stringResource(R.string.screen_main_cvt_temp_value, t),
                        style = MaterialTheme.typography.displaySmall,
                    )
                }
            }

            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.screen_main_oil_title), style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(enabled = state.isConnected, onClick = { vm.readOilDegradationOnce() }) {
                            Text(stringResource(R.string.screen_main_oil_button_read))
                        }
                        Text(text = state.oilDegradation?.toString() ?: stringResource(R.string.screen_main_oil_no_data))
                    }
                }
            }

            if (!state.lastRaw.isNullOrBlank()) {
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.screen_main_raw_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = state.lastRaw ?: "",
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        )
                    }
                }
            }

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
                                        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText(ctx.getString(R.string.screen_main_journal_clipboard_label), logText))
                                    }
                                },
                                enabled = state.logEntries.isNotEmpty(),
                            ) {
                                Text(stringResource(R.string.screen_main_journal_button_copy))
                            }
                            Button(
                                onClick = { vm.clearLogPublic() },
                                enabled = state.logEntries.isNotEmpty(),
                            ) {
                                Text(stringResource(R.string.screen_main_journal_button_clear))
                            }
                        }
                    }
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(state.logEntries.size) { index ->
                            Text(
                                text = state.logEntries[index],
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
        MainScreen()
    }
}
