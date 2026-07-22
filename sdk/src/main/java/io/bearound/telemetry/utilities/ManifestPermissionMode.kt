package io.bearound.telemetry.utilities

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * Detects, at runtime, which BLUETOOTH_SCAN regime the HOST app's MERGED manifest
 * ended up with. This is the mechanism behind plug & play with the Bearound SDK:
 *
 * - **Standalone** (only this SDK in the app): the `neverForLocation` flag this SDK
 *   declares survives the merge → Bluetooth-only regime. Scanning works with location
 *   denied and the Location toggle off; some OEM builds withhold PendingIntent
 *   deliveries for beacon-shaped packets (denylist) → HarvestScanManager compensates.
 *
 * - **Companion** (the app also ships the Bearound SDK, which declares BLUETOOTH_SCAN
 *   WITHOUT the flag): the manifest merge drops the flag → the whole app runs in the
 *   location-capable regime. No denylist applies and PendingIntent deliveries are
 *   complete, so the harvest workaround is unnecessary; scan results only flow while
 *   fine location is granted and Location is on — the tracking SDK's own permission
 *   UX drives the user through that.
 *
 * Nothing to configure: the SDK adapts by reading the merged manifest.
 */
internal object ManifestPermissionMode {

    /** True when the merged manifest still carries `neverForLocation` (standalone regime). */
    fun hasNeverForLocation(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            val pi = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
            val idx = pi.requestedPermissions?.indexOf(Manifest.permission.BLUETOOTH_SCAN) ?: -1
            if (idx < 0) return false
            val flags = pi.requestedPermissionsFlags?.getOrNull(idx) ?: 0
            (flags and PackageInfo.REQUESTED_PERMISSION_NEVER_FOR_LOCATION) != 0
        } catch (e: Exception) {
            false
        }
    }
}
