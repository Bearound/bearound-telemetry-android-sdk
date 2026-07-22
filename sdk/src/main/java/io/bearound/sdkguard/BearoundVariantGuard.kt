package io.bearound.sdkguard

/**
 * MUTUAL-EXCLUSION GUARD — do not rename, move or "deduplicate" this class.
 *
 * A class with this exact fully-qualified name ships in BOTH Bearound artifacts
 * (bearound-sdk and bearound-telemetry). The two declare BLUETOOTH_SCAN with
 * opposite `neverForLocation` semantics, and Android resolves that per-APK: a host
 * app that adds both dependencies would end up with ONE merged declaration that
 * silently breaks one of the two SDKs in production.
 *
 * Thanks to this class, that mistake fails at BUILD TIME instead, with:
 *   "Duplicate class io.bearound.sdkguard.BearoundVariantGuard found in modules ..."
 *
 * If you are reading this because of that error: pick ONE Bearound artifact.
 */
object BearoundVariantGuard {
    const val VARIANT: String = "telemetry"
}
