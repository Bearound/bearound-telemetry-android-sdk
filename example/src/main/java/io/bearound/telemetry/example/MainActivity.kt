package io.bearound.telemetry.example

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.bearound.telemetry.BearoundTelemetrySDK
import io.bearound.telemetry.example.ui.theme.BearoundTelemetryTheme
import io.bearound.telemetry.interfaces.BearoundTelemetrySDKListener
import io.bearound.telemetry.models.Beacon
import kotlinx.coroutines.delay

/**
 * Bearound Telemetry — sample app.
 *
 * Shows EXCLUSIVELY beacon-hardware telemetry (battery, temperature, movement,
 * firmware, signal). No tracking, no proximity, no location — by design.
 * Same visual family as the Bearound SDK sample (BeAroundScan).
 */
class MainActivity : ComponentActivity(), BearoundTelemetrySDKListener {

    private lateinit var sdk: BearoundTelemetrySDK

    private val beaconsState = mutableStateMapOf<String, Beacon>()
    private val collecting = mutableStateOf(false)
    private val syncOk = mutableIntStateOf(0)
    private val syncFail = mutableIntStateOf(0)
    private val nearbyGranted = mutableStateOf(false)
    private val btOn = mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshEnvironment()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sdk = BearoundTelemetrySDK.getInstance(applicationContext)
        sdk.listener = this
        refreshEnvironment()

        setContent {
            BearoundTelemetryTheme {
                TelemetryScreen(
                    nearbyGranted = nearbyGranted.value,
                    btOn = btOn.value,
                    collecting = collecting.value,
                    syncOk = syncOk.intValue,
                    syncFail = syncFail.intValue,
                    beacons = beaconsState.values.sortedBy { it.minor },
                    onRequestPermission = ::requestNearby,
                    onStart = ::startCollection,
                    onStop = ::stopCollection,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshEnvironment()
    }

    private fun refreshEnvironment() {
        nearbyGranted.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        btOn.value = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter?.isEnabled == true
    }

    private fun requestNearby() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN))
        }
    }

    private fun startCollection() {
        sdk.configure(businessToken = BuildConfig.BUSINESS_TOKEN)
        sdk.startScanning()
        collecting.value = true
    }

    private fun stopCollection() {
        sdk.stopScanning()
        collecting.value = false
    }

    // -------------------------------------------------------------------------
    // BearoundTelemetrySDKListener
    // -------------------------------------------------------------------------

    override fun onBeaconsUpdated(beacons: List<Beacon>) {
        runOnUiThread {
            for (b in beacons) beaconsState["${b.major}/${b.minor}"] = b
        }
    }

    override fun onSyncCompleted(beaconCount: Int, success: Boolean, error: Exception?) {
        runOnUiThread { if (success) syncOk.intValue++ else syncFail.intValue++ }
    }
}

// =============================================================================
// UI
// =============================================================================

@Composable
private fun TelemetryScreen(
    nearbyGranted: Boolean,
    btOn: Boolean,
    collecting: Boolean,
    syncOk: Int,
    syncFail: Int,
    beacons: List<Beacon>,
    onRequestPermission: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Bearound Telemetry", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { AboutBanner() }
            item { EnvironmentCard(nearbyGranted, btOn, onRequestPermission) }
            item { CollectionCard(collecting, syncOk, syncFail, nearbyGranted, btOn, onStart, onStop) }
            item {
                Text(
                    "Telemetria dos beacons (${beacons.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (beacons.isEmpty()) {
                item { EmptyState(collecting) }
            }
            items(beacons, key = { "${it.major}/${it.minor}" }) { beacon ->
                BeaconTelemetryCard(beacon, now)
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AboutBanner() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.MonitorHeart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Saúde da frota de beacons: bateria, temperatura, movimento e sinal. " +
                    "Este SDK não faz rastreio de pessoas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun EnvironmentCard(nearbyGranted: Boolean, btOn: Boolean, onRequestPermission: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ambiente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            StatusRow(
                icon = Icons.Filled.BluetoothSearching,
                label = "Dispositivos próximos",
                ok = nearbyGranted,
                okText = "Concedida",
                badText = "Negada",
                action = if (!nearbyGranted) onRequestPermission else null,
            )
            StatusRow(
                icon = Icons.Filled.Bluetooth,
                label = "Bluetooth",
                ok = btOn,
                okText = "Ligado",
                badText = "Desligado",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocationOff,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text("Localização", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "não necessária",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    ok: Boolean,
    okText: String,
    badText: String,
    action: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (ok) okText else badText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
        if (action != null) {
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = action) { Text("Solicitar") }
        }
    }
}

@Composable
private fun CollectionCard(
    collecting: Boolean,
    syncOk: Int,
    syncFail: Int,
    nearbyGranted: Boolean,
    btOn: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Coleta", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    if (collecting) "Coletando" else "Parada",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (collecting) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Sincronizações: $syncOk ok · $syncFail falhas",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onStart,
                    enabled = !collecting && nearbyGranted && btOn,
                    modifier = Modifier.weight(1f),
                ) { Text("Iniciar coleta") }
                OutlinedButton(
                    onClick = onStop,
                    enabled = collecting,
                    modifier = Modifier.weight(1f),
                ) { Text("Parar") }
            }
        }
    }
}

@Composable
private fun EmptyState(collecting: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.Sensors,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (collecting) "Procurando beacons por perto…"
                else "Inicie a coleta para ver a telemetria dos beacons.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BeaconTelemetryCard(beacon: Beacon, now: Long) {
    val meta = beacon.metadata
    val age = ((now - beacon.timestamp.time) / 1000).coerceAtLeast(0)
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Sensors,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Beacon ${beacon.major}.${beacon.minor}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "há ${age}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric(Icons.Filled.BatteryFull, meta?.batteryLevel?.let { "$it mV" } ?: "—", "Bateria")
                Metric(Icons.Filled.Thermostat, meta?.temperature?.let { "$it °C" } ?: "—", "Temperatura")
                Metric(Icons.Filled.Vibration, meta?.movements?.toString() ?: "—", "Movimentos")
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric(Icons.Filled.Bluetooth, "${beacon.rssi} dB", "Sinal")
                Metric(Icons.Filled.Memory, meta?.firmwareVersion ?: "—", "Firmware")
                Spacer(Modifier.width(64.dp))
            }
        }
    }
}

@Composable
private fun Metric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
