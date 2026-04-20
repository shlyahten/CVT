package ru.shlyahten.cvt

import android.Manifest
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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
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
                vm.refreshBondedDevices(this)
            }
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

    val hasConnectPermission = remember(state.hasConnectPermission) { state.hasConnectPermission }

    LaunchedEffect(Unit) {
        val granted = if (Build.VERSION.SDK_INT < 31) {
            true
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        }
        vm.setHasConnectPermission(granted)
        vm.refreshBondedDevices(ctx)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("CVT (ELM327)") })
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (Build.VERSION.SDK_INT >= 31 && !hasConnectPermission) {
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Нужно разрешение Bluetooth для доступа к спаренным устройствам.")
                        Button(onClick = requestBtPermission) { Text("Разрешить BLUETOOTH_CONNECT") }
                    }
                }
            }

            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Устройство (спаренное)", style = MaterialTheme.typography.titleMedium)
                    if (state.bondedDevices.isEmpty()) {
                        Text("Нет спаренных устройств. Спарьте ELM327 в настройках Bluetooth Android.")
                    } else {
                        state.bondedDevices.forEach { d ->
                            val selected = d.address == state.selectedDeviceAddress
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = selected,
                                    onClick = { vm.selectDevice(d.address) },
                                    label = { Text("${d.name ?: "ELM327"} (${d.address})") },
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !state.isConnected && !state.isConnecting,
                            onClick = { vm.connect(ctx) },
                        ) { Text(if (state.isConnecting) "Connecting..." else "Connect") }
                        Button(
                            enabled = state.isConnected || state.isConnecting,
                            onClick = { vm.disconnect() },
                        ) { Text("Disconnect") }
                        Button(onClick = { vm.refreshBondedDevices(ctx) }) { Text("Refresh") }
                    }
                    Text("Status: ${state.status}")
                }
            }

            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CVT температура", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.cvtTempFormula == CvtTempFormula.Temp1,
                            onClick = { vm.setFormula(CvtTempFormula.Temp1) },
                            label = { Text("Temp 1") },
                        )
                        FilterChip(
                            selected = state.cvtTempFormula == CvtTempFormula.Temp2,
                            onClick = { vm.setFormula(CvtTempFormula.Temp2) },
                            label = { Text("Temp 2") },
                        )
                    }
                    val t = state.cvtTempC
                    Text(
                        text = if (t == null) "—" else String.format("%.1f °C", t),
                        style = MaterialTheme.typography.displaySmall,
                    )
                }
            }

            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CVT oil degradation (2110)", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = state.isConnected, onClick = { vm.readOilDegradationOnce() }) {
                            Text("Read once")
                        }
                        Text(text = state.oilDegradation?.toString() ?: "—")
                    }
                }
            }

            if (!state.lastRaw.isNullOrBlank()) {
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Последний RAW ответ", style = MaterialTheme.typography.titleMedium)
                        Text(state.lastRaw ?: "")
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