package io.bearound.telemetry.interfaces

import io.bearound.telemetry.models.BeaconMetadata
import java.util.UUID

/**
 * Listener interface for Bluetooth manager events
 */
interface BluetoothManagerListener {
    /**
     * Called when a beacon is discovered via BLE scanning
     */
    fun onBeaconDiscovered(
        uuid: UUID,
        major: Int,
        minor: Int,
        rssi: Int,
        txPower: Int,
        metadata: BeaconMetadata?,
        isConnectable: Boolean
    )

    /**
     * Called when Bluetooth state changes
     */
    fun onBluetoothStateChanged(isPoweredOn: Boolean)
}
