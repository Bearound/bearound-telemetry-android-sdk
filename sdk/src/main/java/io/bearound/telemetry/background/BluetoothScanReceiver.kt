package io.bearound.telemetry.background

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import io.bearound.telemetry.BearoundTelemetrySDK

/**
 * BroadcastReceiver for PendingIntent-based BLE scan (API 26+)
 * System wakes up the app when beacon is detected
 */
class BluetoothScanReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BearoundTelemetrySDK-BTReceiver"
        const val ACTION_BLUETOOTH_SCAN = "io.bearound.telemetry.ACTION_BLUETOOTH_SCAN"
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (!hasRequiredPermissions(context)) {
            Log.w(TAG, "Missing required permissions")
            return
        }

        try {
            val sdk = BearoundTelemetrySDK.getInstance(context.applicationContext)

            if (!sdk.isConfigured) {
                sdk.attemptConfigRestore()
                if (!sdk.isConfigured) {
                    Log.e(TAG, "SDK not configured - skipping scan")
                    return
                }
            }

            val scanResults = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(
                    BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
                    ScanResult::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)
            }

            if (!scanResults.isNullOrEmpty()) {
                sdk.processBroadcastResults(scanResults)
            } else {
                // OEM denylist in action (Moto stock): the offloaded filter matched — a
                // Bearound beacon IS nearby — but the aggregated record was withheld.
                // Use the wake-up as an alarm clock and collect on the callback path.
                Log.d(TAG, "EMPTY delivery (denylist upstream) — opening harvest window")
                HarvestScanManager.start(context.applicationContext, "empty-delivery wake")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing scan: ${e.message}")
            io.bearound.telemetry.telemetry.ErrorReporter.report(e, "BluetoothScanReceiver.onReceive")
        }
    }

    private fun hasRequiredPermissions(context: Context): Boolean {
        // Two "eyes": Location and Bluetooth. Accept results if AT LEAST ONE is granted.
        val hasLocation =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val hasBluetoothScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Pre-Android-12: BLUETOOTH/BLUETOOTH_ADMIN are install-time normal permissions.
            true
        }

        return hasLocation || hasBluetoothScan
    }
}
