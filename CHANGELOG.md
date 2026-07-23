# Changelog

All notable changes to the BeAround Telemetry Android SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
