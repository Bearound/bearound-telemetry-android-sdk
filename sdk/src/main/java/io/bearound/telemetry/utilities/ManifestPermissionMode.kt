package io.bearound.telemetry.utilities

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * Detects, at runtime, which BLUETOOTH_SCAN regime the HOST app's MERGED manifest
 * ended up with.
 *
 * In BOTH supported modes (standalone, and companion alongside the Bearound SDK — which
 * also declares the flag) `neverForLocation` is expected to SURVIVE the merge: telemetry
 * then works with location denied/off, and HarvestScanManager compensates OEM denylists.
 *
 * The flag being ABSENT means a third-party library declared BLUETOOTH_SCAN without it
 * and the merge dropped it — the app then runs location-capable: no denylist applies
 * (harvest correctly stays off) but Android withholds every scan result unless fine
 * location is granted, so telemetry goes blind for users without location. That state
 * is a misintegration to surface, not a mode to embrace — hosts fix it with
 * tools:replace (see README §Permissions).
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
