# Bearound Telemetry SDK for Android

Kotlin SDK that collects **fleet-health telemetry from Bearound BLE beacons** — battery,
temperature, movement counter, firmware version and signal (RSSI) — **without requiring any
location permission** from the end user.

On Android 12+ it scans with the **"Nearby devices"** grant alone (`BLUETOOTH_SCAN` declared
with `neverForLocation`): telemetry keeps flowing with the system Location toggle **off** and
with location **denied**. This is the companion artifact to the
[Bearound SDK](https://github.com/Bearound/bearound-android-sdk) (tracking); this one owns the
**beacon domain** — knowing the health of the beacon fleet even where tracking cannot run.

> **Status: preview (0.1.x).** This SDK is a split of `bearound-android-sdk` 3.5.1. The sync
> payload currently mirrors the main SDK 1:1 while functional validation runs against the
> ingest pipeline; trimming it down to the pure telemetry domain (beacon-side data only) is
> the next planned step. Do not ship to production yet.

## What it collects

Per detected beacon: identity (UUID / major / minor), RSSI, and the sensor payload advertised
by the beacon firmware — battery level, temperature, movement counter and firmware version.
Collection is passive (BLE advertisements only — the SDK never connects to beacons).

## Requirements

- Android 6.0+ (API 23). **Effective telemetry collection is Android 12+**: below 12 the OS
  gates BLE scan results on the location permission, which this SDK deliberately does not
  declare (collection there only happens if the host app holds location on its own).
- Bearound business token (the same one used by the main SDK).

## Installation

### JitPack

```groovy
// settings.gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}

// app/build.gradle
dependencies {
    implementation 'com.github.Bearound:bearound-telemetry-android-sdk:<version>'
}
```

> ⚠️ **Never add this artifact and `bearound-android-sdk` to the same app.** They declare
> `BLUETOOTH_SCAN` with opposite `neverForLocation` semantics, and Android resolves that
> per-APK — one of the two would silently break. The build fails on purpose with a
> duplicate-class error (`io.bearound.sdkguard.BearoundVariantGuard`) if both are present.
> One app, one Bearound artifact: tracking **or** telemetry.

## Permissions

Everything is declared in the SDK manifest and merges into your app — **no location
permission is added** (your Play Data Safety form carries no "Location" entry on account of
this SDK). At runtime you only need to request **Nearby devices** on Android 12+:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN), REQUEST_CODE)
}
```

If another library in the host app declares `BLUETOOTH_SCAN` **without** `neverForLocation`,
the manifest merger drops the flag and telemetry goes blind for users without location.
Arbitrate explicitly in the app manifest:

```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation"
    tools:node="replace" />
```

## Quick start

```kotlin
import io.bearound.telemetry.BearoundTelemetrySDK

val sdk = BearoundTelemetrySDK.getInstance(applicationContext)
sdk.configure(businessToken = "your-business-token")

// after the Nearby-devices permission is granted (12+):
sdk.startScanning()
```

`stopScanning()` stops collection. The public surface mirrors the main SDK
(`reliabilityStatus()`, `diagnostics()`, battery-optimization helpers, etc.) — see KDoc.

## How it works

- **PendingIntent BLE scan** (API 26+) with offloaded filters for the Bearound frame
  signatures — the system wakes the SDK when a beacon is nearby, even with the app process
  dead. **Inexact watchdog alarm** (~15 min) self-heals the registration and flushes pending
  uploads; scanning resumes after reboot.
- **Harvest scan (OEM denylist workaround).** Some OEM builds (observed on Moto stock,
  Android 14/15) drop every scan record containing an iBeacon signature for
  `neverForLocation` apps, so PendingIntent deliveries arrive empty. The SDK uses that empty
  wake-up as an alarm clock and opens a short, filtered `ScanCallback` window — a delivery
  path that still receives the pure `0xBEAD` frames. Field-validated on a Moto G35
  (Android 15) with production beacons. Beacons running firmware v4/v5 (separated frames)
  multiply the harvest yield.
- Readings are buffered offline and uploaded in batches to the Bearound ingest,
  authenticated by the business token.

## Roadmap

- [ ] Trim the sync payload to the telemetry domain (beacon-side data only, anonymous).
- [ ] Sample app + CI.
- [ ] Publishing workflow (tags → GitHub Packages / JitPack), mirroring the main SDK train.

## License

MIT — see [LICENSE](LICENSE).
