package io.bearound.telemetry.background

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import io.bearound.telemetry.BearoundTelemetrySDK
import io.bearound.telemetry.utilities.IBeaconParser
import io.bearound.telemetry.utilities.ScanStartBudget

/**
 * HARVEST SCAN — OEM-denylist workaround, exclusive to the telemetry variant.
 *
 * On Moto stock (field-validated on a G35/Android 15, 2026-07-22) the PendingIntent
 * delivery path aggregates each beacon into one record that includes the scan
 * response; for v3 firmware that record carries the iBeacon signature, so the
 * `neverForLocation` denylist drops it WHOLE (system log: "BtGatt.GattService:
 * Skipping data matching denylist") and every delivery arrives EMPTY. The
 * ScanCallback path on the same UID, however, receives the pure-BEAD frames
 * individually.
 *
 * So: the PendingIntent scan stays as the ALARM CLOCK (it wakes the process on
 * every beacon encounter even when the delivery is empty), and this manager runs a
 * short, filtered, time-boxed ScanCallback window to do the actual collecting.
 * Results enter the exact same pipeline (processBroadcastResults).
 *
 * Triggers: empty PendingIntent delivery (BluetoothScanReceiver) and the watchdog
 * tick. Rate-limited and budgeted (ScanStartBudget) — see constants.
 */
internal object HarvestScanManager {

    private const val TAG = "BearoundTelemetry-Harvest"

    /** LOW_LATENCY inside the window: denylist survivors are rare (~1 every 30 s
     *  observed on Moto for v3 beacons) — the window must not miss them. */
    private const val HARVEST_WINDOW_MS = 30_000L

    /** Production pacing: one window per 5 min. Aligned with the backend sync
     *  cadence — readings from the same beacon are redundant at shorter intervals,
     *  and on denylist OEMs the wake-ups fire every ~5 s, so a shorter limit would
     *  keep a LOW_LATENCY scan running almost continuously (battery). Worst-case
     *  duty with a beacon permanently nearby: 30 s of scan per 5 min (~10%). */
    private const val HARVEST_MIN_INTERVAL_MS = 300_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var callback: ScanCallback? = null
    private var lastStart = 0L

    val isRunning: Boolean get() = callback != null

    /**
     * Opens a harvest window if allowed (12+ only — below S there is no denylist and
     * the PendingIntent path delivers normally). No-op when already running,
     * rate-limited, over the scan-start budget, or with Bluetooth off.
     */
    @SuppressLint("MissingPermission") // callers run inside permission-gated paths
    fun start(context: Context, reason: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        // Companion regime (merged manifest lost the flag because the Bearound SDK is
        // present): no denylist applies and PendingIntent deliveries are complete — an
        // empty delivery just means no beacon around, so harvesting would waste battery.
        if (!io.bearound.telemetry.utilities.ManifestPermissionMode.hasNeverForLocation(context)) return
        if (callback != null) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastStart < HARVEST_MIN_INTERVAL_MS) return

        val app = context.applicationContext
        val scanner = (app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter?.bluetoothLeScanner ?: return

        if (!ScanStartBudget.tryAcquire("harvest")) {
            Log.d(TAG, "Harvest deferred — scan-start budget exhausted")
            return
        }

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result ?: return
                try {
                    val sdk = BearoundTelemetrySDK.getInstance(app)
                    if (!sdk.isConfigured) {
                        sdk.attemptConfigRestore()
                        if (!sdk.isConfigured) return
                    }
                    sdk.processBroadcastResults(listOf(result))
                } catch (e: Exception) {
                    Log.e(TAG, "Harvest result processing failed: ${e.message}")
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "Harvest scan failed (code $errorCode)")
                if (errorCode == 6 /* SCAN_FAILED_SCANNING_TOO_FREQUENTLY */) {
                    ScanStartBudget.freeze()
                }
                callback = null
            }
        }

        val filters = listOf(
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

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .build()

        try {
            scanner.startScan(filters, settings, cb)
        } catch (e: Exception) {
            Log.e(TAG, "Harvest start threw: ${e.message}")
            return
        }
        callback = cb
        lastStart = now
        Log.d(TAG, "Harvest window opened ($reason, ${HARVEST_WINDOW_MS / 1000}s)")
        mainHandler.postDelayed({ stop(app) }, HARVEST_WINDOW_MS)
    }

    @SuppressLint("MissingPermission")
    fun stop(context: Context) {
        val cb = callback ?: return
        callback = null
        try {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                ?.adapter?.bluetoothLeScanner?.stopScan(cb)
        } catch (e: Exception) {
            Log.w(TAG, "Harvest stop: ${e.message}")
        }
        Log.d(TAG, "Harvest window closed")
    }
}
