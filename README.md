# 🐻 Bearound Telemetry SDK for Android

[![JitPack](https://jitpack.io/v/Bearound/bearound-telemetry-android-sdk.svg)](https://jitpack.io/#Bearound/bearound-telemetry-android-sdk)
[![API](https://img.shields.io/badge/API-23%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=23)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Kotlin SDK for Android — **hardware telemetry for Bearound BLE beacons** (battery,
temperature, movement, firmware, signal), designed to run **without any location
permission**. Companion to the
[Bearound SDK](https://github.com/Bearound/bearound-android-sdk) (tracking): plug & play
alongside it, or standalone in apps that cannot ask for location.

> [!IMPORTANT]
> **This SDK does not track people.** It never collects, derives or uploads user location,
> proximity or positioning of any kind — its data is **exclusively telemetry about the
> beacon hardware** (fleet health). Tracking and indoor positioning live in the
> [Bearound SDK](https://github.com/Bearound/bearound-android-sdk); install both when you
> need both.

> [!TIP]
> **⚡ Set it up with an AI agent.** Don't wire the integration by hand — hand
> [one prompt](./AI-AGENT-SETUP.md) to your AI coding agent (Claude Code, Cursor, Copilot)
> and let it pilot the whole install. → [Set up with an AI agent](#set-up-with-an-ai-agent)

[![Agent setup prompt](https://img.shields.io/badge/Agent_setup_prompt-open_%26_copy-2563eb?style=for-the-badge)](./AI-AGENT-SETUP.md)

> [!NOTE]
> **Payload compatibility.** The sync payload is intentionally identical to the main
> Bearound SDK's — same ingest endpoint, same schema, zero backend changes. What differs
> is the permission model and the product scope (beacon-hardware telemetry only).

## What the SDK collects

Per detected Bearound beacon — passively, from BLE advertisements only (it never connects):

- **Identity**: UUID / major / minor
- **Battery** level and **temperature** (from the beacon's sensor payload)
- **Movement counter** and **firmware version**
- **Signal**: RSSI

Detection matches every Bearound hardware generation — `0xBEAD` service data, `0xBEAD`
manufacturer data, and the Bearound iBeacon frame (identity-only until a sensor frame is
captured).

**What it never collects**: user location, movement profiles, advertising IDs, or
anything about people. Beacon hardware health only. (For schema compatibility with the
main SDK, per-beacon radio fields — RSSI and its derived proximity bucket for the
encounter — travel in the payload; they describe the radio link to the beacon, and this
SDK never uses them for positioning.)

## Two integration modes — plug & play

The SDK declares `BLUETOOTH_SCAN` with `neverForLocation` and **no location permissions**.
The final regime is decided by your app's **manifest merge**, and the SDK adapts at runtime
by reading the merged manifest — nothing to configure:

| | **Companion** (recommended) | **Standalone** |
|---|---|---|
| When | Your app also ships the [Bearound SDK](https://github.com/Bearound/bearound-android-sdk) (tracking) | Location is off-limits for your app |
| Manifest merge | **Both SDKs declare `BLUETOOTH_SCAN` with `neverForLocation`** → clean merge, flag preserved | The flag survives → Bluetooth-only regime |
| Resulting regime | Tracking detects while location is granted + on; **telemetry keeps working even when the user denies location** — the reason this pairing exists | Works with location **denied** and Location toggle **off** (Android 12+) |
| Telemetry flows when | "Nearby devices" granted — location state irrelevant | "Nearby devices" granted — that's all |
| Extra machinery | Harvest scan active where OEM denylists bite (Moto stock, realme) | Harvest scan compensates OEM denylists |

Tracking and telemetry stay **separate products with separate pipelines** — the tracking
SDK owns the person/positioning domain, this SDK owns the beacon-health domain. Running
both in one app is the intended "full Bearound" setup: users who grant location get
tracking + telemetry; users who deny it still contribute fleet health.

The two SDKs are engineered to coexist in one app without stepping on each other:
independent broadcast actions, WorkManager unique names, SharedPreferences namespaces,
notification channels/ids and offline batch directories (`bearound_telemetry_*` /
`com.bearound.telemetry.*`).

## Requirements

- Android 6.0+ (API 23+); effective **standalone** collection starts at Android 12
  (below 12 the legacy BLE-scan gate is the location permission, which this SDK does not
  declare — in companion mode the tracking SDK covers it)
- Bluetooth LE hardware
- Your app's build: `compileSdk` 35+ and AGP 8.6.0+ (required transitively by
  `androidx.core` 1.16.0)
- A Bearound **business token** — same token as the main SDK (see
  [Getting a business token](https://github.com/Bearound/bearound-android-sdk#getting-a-business-token))

## Installation

### JitPack

```groovy
// settings.gradle — repositories
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}

// app/build.gradle — dependencies
dependencies {
    implementation 'com.github.Bearound:bearound-telemetry-android-sdk:v0.2.0'
}
```

## Set up with an AI agent

Instead of wiring the setup by hand, hand it to an **AI coding agent** (Claude Code,
Cursor, Copilot, …). This README is written to be **agent-readable** — the agent reads it
and does the whole integration. There's one ready-made prompt to give it:

[![Agent setup prompt](https://img.shields.io/badge/Agent_setup_prompt-open_%26_copy-2563eb?style=for-the-badge)](./AI-AGENT-SETUP.md)

Open [`AI-AGENT-SETUP.md`](./AI-AGENT-SETUP.md) and click the **copy icon** on its code
block, then paste it into your agent with your app's repo open. Web-capable agents can
fetch its [raw URL](https://raw.githubusercontent.com/Bearound/bearound-telemetry-android-sdk/main/AI-AGENT-SETUP.md)
directly.

## Permissions

**You don't need to declare any permission yourself.** The SDK ships them in its own
`AndroidManifest.xml` and Gradle's manifest merge injects them into your app:

| Merged into your app | Purpose |
|---|---|
| `BLUETOOTH_SCAN` (`neverForLocation`) | BLE scanning on Android 12+ — the flag must survive the merge in [both modes](#two-integration-modes--plug--play) |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` (≤ API 30) | Legacy BLE scanning |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Telemetry upload |
| `ACCESS_WIFI_STATE` | Wi-Fi SSID telemetry field |
| `POST_NOTIFICATIONS` | Only if you opt into the foreground service |
| `RECEIVE_BOOT_COMPLETED` | Resume collection after reboot |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Optional foreground service |

**No location permission is added by this SDK.** Standalone hosts carry no "Location"
entry in Play Data Safety on its account.

At runtime, request **Nearby devices** on Android 12+ (in companion mode, request it
together with the location permissions the tracking SDK needs — one dialog flow):

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN), REQUEST_CODE)
}
```

**Both modes**: if some *other* third-party library declares `BLUETOOTH_SCAN` without
`neverForLocation`, the merge drops the flag and telemetry goes blind for users without
location (the SDK detects this at runtime and logs it). Re-declare it in your app
manifest, forcing it to win:

```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation"
    tools:replace="android:usesPermissionFlags" />
```

## Quick Start

```kotlin
import io.bearound.telemetry.BearoundTelemetrySDK

val telemetry = BearoundTelemetrySDK.getInstance(applicationContext)
telemetry.configure(businessToken = "your-business-token")

// after the runtime permission flow:
telemetry.startScanning()
```

`configure` also takes `scanPrecision` (`ScanPrecision.HIGH` / `MEDIUM` / `LOW`,
default `MEDIUM`): it prices the **background** radio duty (one continuous
hardware-managed scan — BALANCED ~20% / LOW_POWER ~10%) and the sync cadence.
In the **foreground** every precision scans at `LOW_LATENCY` — detection always
feels fluid while the app is in use.

`stopScanning()` stops collection. The public surface mirrors the main SDK —
`reliabilityStatus()`, `diagnostics()`, battery-optimization helpers, foreground-service
opt-in (`enableForegroundScanning`) — see the KDoc.

Companion apps initialize **both** SDKs side by side; they scan and sync independently.
The tracking SDK's `configure()` returns the instance (self) — hand it straight to
telemetry, which extracts the business token **and the device id** from it, so both
SDKs report as the **same device**; plain fill-in also works, both paths are supported:

```kotlin
// tracking first — configure() returns self
val bearound = BeAroundSDK
    .getInstance(this)
    .configure(businessToken = "your-business-token")

// companion one-liner: credentials + deviceId handoff from the instance
BearoundTelemetrySDK
    .getInstance(this)
    .configure(bearound)

// …or fill it in normally (standalone style):
BearoundTelemetrySDK.getInstance(this).configure(businessToken = "your-business-token")
```

## Background collection

- **PendingIntent BLE scan** (API 26+) with offloaded filters for the Bearound frame
  signatures — the system wakes the SDK when a beacon is nearby, even with the app
  process dead. An **inexact watchdog alarm** (~15 min) self-heals the scan registration,
  flushes pending uploads and re-arms after reboot. No exact alarms.
- **Harvest scan (OEM workaround).** Some OEM builds (observed on Moto stock,
  Android 14/15) withhold every PendingIntent delivery that contains an iBeacon signature
  for `neverForLocation` apps. The SDK uses the empty wake-up as an alarm clock and opens
  a short filtered `ScanCallback` window — a path that still receives the pure `0xBEAD`
  frames. Field-validated on a Moto G35 (Android 15) with production beacons. Runs only
  while the merged manifest carries the `neverForLocation` flag (its natural state in
  both modes); it turns itself off automatically if a third-party conflict strips the flag.
- Readings are buffered offline and uploaded in batches to the Bearound ingest,
  authenticated by the business token.

## Roadmap

- [x] `configure(bearoundSdk)` overload — pass the tracking instance itself. The
      handoff is dynamic **by design** (no compile-time dependency on the tracking
      SDK): it is what lets the Flutter/React Native wrappers hand off reflectively
      when both SDKs share an app, with no version coupling between the two.
- [ ] Sample app + CI
- [ ] Release workflow (tags → JitPack), mirroring the main SDK train
- [ ] Shared-scan optimization in companion mode (single scan client feeding both SDKs)

## License

MIT — see [LICENSE](LICENSE).
