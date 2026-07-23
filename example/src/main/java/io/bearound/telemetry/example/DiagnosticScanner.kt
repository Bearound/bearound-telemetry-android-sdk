package io.bearound.telemetry.example

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.bearound.telemetry.BearoundTelemetrySDK
import io.bearound.telemetry.interfaces.BearoundTelemetrySDKListener
import io.bearound.telemetry.models.Beacon
import io.bearound.telemetry.utilities.IBeaconParser
import java.util.concurrent.ConcurrentHashMap

/**
 * Field diagnostic for scan quality — NOT part of the product UI.
 *
 * Launch with:
 *   adb shell am start -n io.bearound.telemetry.example/.MainActivity -e diag 1
 *
 * Phases (sequential):
 *  1. 60s raw baseline    — unfiltered LOW_LATENCY scan: what the radio actually delivers.
 *  2. 60s SDK filters     — same LOW_LATENCY scan through the SDK's 3 hardware filters.
 *  3. 120s real SDK       — configure + startScanning (default precision), counting
 *                           listener deliveries per beacon.
 *
 * Per beacon it reports: packet count, packets/min, max gap between packets, and the
 * frame mix (BEAD service-data vs iBeacon). Everything goes to logcat tag "BearoundDiag";
 * the final consolidated line is prefixed DIAG_RESULT for grep.
 */
class DiagnosticScanner(private val context: Context) {

    companion object {
        private const val TAG = "BearoundDiag"
        private const val PHASE1_MS = 60_000L
        private const val PHASE2_MS = 60_000L
        private const val PHASE3_MS = 120_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scanner
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter?.bluetoothLeScanner

    private class Counter {
        var total = 0
        var beadFrames = 0
        var ibeaconFrames = 0
        var firstTs = 0L
        var lastTs = 0L
        var maxGapMs = 0L

        fun hit(now: Long, hasBead: Boolean, hasIBeacon: Boolean) {
            if (firstTs == 0L) firstTs = now
            if (lastTs != 0L) maxGapMs = maxOf(maxGapMs, now - lastTs)
            lastTs = now
            total++
            if (hasBead) beadFrames++
            if (hasIBeacon) ibeaconFrames++
        }
    }

    private val counters = ConcurrentHashMap<String, Counter>()
    private var phaseStart = 0L
    private var phaseName = ""

    private fun record(result: ScanResult) {
        val rec = result.scanRecord ?: return
        val bead = IBeaconParser.parseServiceData(rec, result.rssi)
        val ib = IBeaconParser.parseIBeaconFrame(rec, result.rssi)
        val parsed = bead ?: ib ?: return
        val key = "${parsed.major}.${parsed.minor}"
        counters.getOrPut(key) { Counter() }
            .hit(System.currentTimeMillis(), bead != null, ib != null)
    }

    private val rawCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = record(result)
        override fun onBatchScanResults(results: List<ScanResult>) =
            results.forEach { record(it) }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "[$phaseName] scan failed: $errorCode")
        }
    }

    /** Mirror of the SDK's metadata/ranging hardware filters. */
    private fun sdkFilters() = listOf(
        ScanFilter.Builder()
            .setManufacturerData(IBeaconParser.BEAROUND_MANUFACTURER_ID, byteArrayOf())
            .build(),
        ScanFilter.Builder()
            .setServiceData(IBeaconParser.BEAD_SERVICE_UUID, byteArrayOf(), byteArrayOf())
            .build(),
        ScanFilter.Builder()
            .setManufacturerData(
                IBeaconParser.APPLE_MANUFACTURER_ID,
                IBeaconParser.BEAROUND_IBEACON_PREFIX,
                IBeaconParser.BEAROUND_IBEACON_MASK
            )
            .build()
    )

    private fun lowLatency() = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setReportDelay(0)
        .build()

    private fun dump(phase: String, elapsedMs: Long) {
        val mins = elapsedMs / 60_000.0
        if (counters.isEmpty()) {
            Log.i(TAG, "DIAG_RESULT phase=$phase elapsed=${elapsedMs / 1000}s NO_PACKETS")
            return
        }
        counters.entries.sortedBy { it.key }.forEach { (key, c) ->
            Log.i(
                TAG,
                "DIAG_RESULT phase=$phase beacon=$key pkts=${c.total} " +
                    "rate=${"%.1f".format(c.total / mins)}/min " +
                    "maxGap=${c.maxGapMs / 1000}s bead=${c.beadFrames} ibeacon=${c.ibeaconFrames}"
            )
        }
    }

    private fun startPhase(name: String) {
        counters.clear()
        phaseName = name
        phaseStart = System.currentTimeMillis()
        Log.i(TAG, "=== PHASE $name started ===")
    }

    /**
     * Phase-3-only mode with a 35s cooldown: the 3-phase chain spends the whole OS
     * scan-start quota (5/30s) right before the SDK starts, depressing its first minute.
     * This mode measures the SDK in steady state.
     */
    fun runSdkOnly() {
        Log.i(TAG, "Diagnostic (SDK-only): 35s quota cooldown, then 180s real SDK")
        handler.postDelayed({ runSdkPhase(durationMs = 180_000L) }, 35_000L)
    }

    @SuppressLint("MissingPermission")
    fun run() {
        Log.i(TAG, "Diagnostic starting (60s raw + 60s filtered + 120s SDK)")

        // Phase 1 — raw radio baseline
        startPhase("1-raw")
        scanner?.startScan(null, lowLatency(), rawCallback)
        handler.postDelayed({
            scanner?.stopScan(rawCallback)
            dump("1-raw", PHASE1_MS)

            // Phase 2 — SDK hardware filters
            startPhase("2-sdk-filters")
            scanner?.startScan(sdkFilters(), lowLatency(), rawCallback)
            handler.postDelayed({
                scanner?.stopScan(rawCallback)
                dump("2-sdk-filters", PHASE2_MS)
                runSdkPhase()
            }, PHASE2_MS)
        }, PHASE1_MS)
    }

    private fun runSdkPhase(durationMs: Long = PHASE3_MS) {
        startPhase("3-sdk-real")
        val sdk = BearoundTelemetrySDK.getInstance(context)
        sdk.listener = object : BearoundTelemetrySDKListener {
            private val lastSeenTs = ConcurrentHashMap<String, Long>()
            override fun onBeaconsUpdated(beacons: List<Beacon>) {
                val now = System.currentTimeMillis()
                for (b in beacons) {
                    val key = "${b.major}.${b.minor}"
                    val ts = b.timestamp.time
                    // Count only fresh samples (timestamp advanced), not re-emissions.
                    if (lastSeenTs.put(key, ts) != ts) {
                        counters.getOrPut(key) { Counter() }
                            .hit(now, b.metadata != null, false)
                    }
                }
            }
        }
        sdk.configure(businessToken = BuildConfig.BUSINESS_TOKEN)
        sdk.startScanning()
        handler.postDelayed({
            dump("3-sdk-real", durationMs)
            sdk.stopScanning()
            Log.i(TAG, "=== DIAG COMPLETE ===")
        }, durationMs)
    }
}
