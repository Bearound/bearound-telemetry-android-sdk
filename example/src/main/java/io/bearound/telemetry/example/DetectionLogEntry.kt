package io.bearound.telemetry.example

import io.bearound.telemetry.models.Beacon
import java.util.Date
import java.util.UUID

/**
 * One observed detection (a fresh advertisement processed by the SDK).
 * Mirrors the BearoundScan detection log, minus the positioning fields —
 * this sample never shows proximity/tracking data.
 */
data class DetectionLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Date = Date(),
    val major: Int,
    val minor: Int,
    val rssi: Int,
    /** "BEAD" when the sensor payload was captured, "iBeacon" for identity-only frames. */
    val discoverySource: String,
    val isBackground: Boolean,
) {
    companion object {
        fun from(beacon: Beacon, isBackground: Boolean): DetectionLogEntry = DetectionLogEntry(
            timestamp = Date(),
            major = beacon.major,
            minor = beacon.minor,
            rssi = beacon.rssi,
            discoverySource = if (beacon.metadata != null) "BEAD" else "iBeacon",
            isBackground = isBackground,
        )
    }
}
