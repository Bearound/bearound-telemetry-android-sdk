# Changelog

All notable changes to the BeAround Telemetry Android SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - 2026-07-23

### Changed

- **Continuous scan architecture** (ported from the main SDK's 3.5.2 scan-reliability work): MEDIUM/LOW precision now keep ONE scan registration with a hardware-managed duty cycle (BALANCED ~20% / LOW_POWER ~10%) instead of the manual 10s-scan/10s-pause cycle. The old design consumed 3-4 of the 5 scan-starts/30s the OS allows, so any extra start silently starved every scanner for 30s+ — bench on realme C61: the SDK delivered 2.5-3 readings/min (max gaps of 54-62s) while the radio had 11-13/min available.
- Foreground scanning is always `SCAN_MODE_LOW_LATENCY` regardless of precision — precision now only prices the background duty and sync cadence. A foreground/background flip re-registers the ranging client with the matching mode.
- Beacon retention tuned for the continuous modes: eviction 15s (HIGH/MEDIUM) / 25s (LOW), stale fade at 10s.

### Added

- Weak-receiver compensation: Unisoc/Spreadtrum-class SoCs (Moto G35 T760, realme C61 T612 — bench-measured capturing 2-8% of the frames of a mid-range receiver) get doubled retention windows and a 20s stale threshold, so the beacon list holds steady instead of flickering.
- Self-heal for `SCAN_FAILED_ALREADY_STARTED` zombie scan registrations (stack/SDK state desync after failed stops or Bluetooth cycles).

## [0.1.3] - 2026-07-23

### Changed

- Harvest scan tuned for production: one 30s filtered window per 5 minutes (was back-to-back windows tuned for bench testing). Keeps denylisted OEMs (Moto, realme) covered at a fraction of the battery cost.

### Added

- Unit-test suite ported from the main SDK (80 tests), adapted to the telemetry namespaces.

## [0.1.2] - 2026-07-23

### Changed

- Payload discriminator renamed: `sdk.type` = `"telemetry"` (replaces the short-lived `product` field from 0.1.1).

## [0.1.1] - 2026-07-23

### Added

- `sdk` payload block now identifies the emitting SDK so the backend can tell telemetry traffic from tracking traffic.

## [0.1.0] - 2026-07-22

### Added

- Initial release of the Bearound Telemetry SDK — anonymous-capable beacon fleet-health telemetry (battery, temperature, movement, firmware) with no location permission.
- Published on JitPack: `implementation 'com.github.Bearound:bearound-telemetry-android-sdk:v0.1.0'`.
- Example app (`:example`) demonstrating SDK integration.
