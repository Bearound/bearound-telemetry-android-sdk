package io.bearound.telemetry.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.bearound.telemetry.BearoundTelemetrySDK
import io.bearound.telemetry.interfaces.BearoundTelemetrySDKListener
import io.bearound.telemetry.models.Beacon

/**
 * Standalone sample: telemetry only. Shows live what the SDK is detecting —
 * beacon identity, RSSI and the sensor payload (battery/temperature) — plus the
 * device id and sync results. Business token comes from local.properties
 * (BUSINESS_TOKEN=...), never committed.
 */
class MainActivity : Activity(), BearoundTelemetrySDKListener {

    private lateinit var statusView: TextView
    private lateinit var sdk: BearoundTelemetrySDK

    private val lastSeen = LinkedHashMap<String, Beacon>()
    private var syncOk = 0
    private var syncFail = 0
    private var scanning = false

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 2_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sdk = BearoundTelemetrySDK.getInstance(applicationContext)
        sdk.listener = this

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        statusView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
        }
        root.addView(statusView)

        root.addButton("1. Conceder permissão (Nearby)") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN), 1)
            } else {
                toast("Nada a pedir nesta versão")
            }
        }
        root.addButton("2. Iniciar telemetria") {
            sdk.configure(businessToken = BuildConfig.BUSINESS_TOKEN)
            sdk.startScanning()
            render()
        }
        root.addButton("Parar") {
            sdk.stopScanning()
            render()
        }

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresh)
    }

    // -------------------------------------------------------------------------
    // BearoundTelemetrySDKListener
    // -------------------------------------------------------------------------

    override fun onBeaconsUpdated(beacons: List<Beacon>) {
        runOnUiThread {
            for (b in beacons) lastSeen["${b.major}/${b.minor}"] = b
            render()
        }
    }

    override fun onScanningStateChanged(isScanning: Boolean) {
        runOnUiThread {
            scanning = isScanning
            render()
        }
    }

    override fun onSyncCompleted(beaconCount: Int, success: Boolean, error: Exception?) {
        runOnUiThread {
            if (success) syncOk++ else syncFail++
            render()
        }
    }

    override fun onError(error: Exception) {
        runOnUiThread { toast("Erro: ${error.message}") }
    }

    // -------------------------------------------------------------------------

    private fun render() {
        val now = System.currentTimeMillis()
        statusView.text = buildString {
            appendLine("BEAROUND TELEMETRY — EXAMPLE (standalone)")
            appendLine()
            appendLine("deviceId : ${sdk.deviceId}")
            appendLine("scanning : $scanning")
            appendLine("manifest : ${flagState()}")
            appendLine("sync     : $syncOk ok / $syncFail falhas")
            appendLine()
            if (lastSeen.isEmpty()) {
                appendLine("(nenhum beacon detectado ainda)")
            } else {
                appendLine("beacons (${lastSeen.size}):")
                lastSeen.values.forEach { b ->
                    val meta = b.metadata?.let {
                        "batt ${it.batteryLevel}mV  ${it.temperature}°C  fw${it.firmwareVersion}"
                    } ?: "(sem payload de sensor ainda)"
                    appendLine("• ${b.major}/${b.minor}  rssi ${b.rssi}  $meta")
                    appendLine("  ${(now - b.timestamp.time) / 1000}s atrás  ${b.proximity}")
                }
            }
        }
    }

    private fun flagState(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return "n/a (< Android 12)"
        return try {
            val pi = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            val idx = pi.requestedPermissions?.indexOf(Manifest.permission.BLUETOOTH_SCAN) ?: -1
            val flags = pi.requestedPermissionsFlags?.getOrNull(idx) ?: 0
            if ((flags and PackageInfo.REQUESTED_PERMISSION_NEVER_FOR_LOCATION) != 0)
                "neverForLocation OK" else "FLAG PERDIDA (conflito de merge!)"
        } catch (e: Exception) {
            "?"
        }
    }

    private fun LinearLayout.addButton(label: String, onClick: () -> Unit) {
        addView(Button(context).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        })
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
