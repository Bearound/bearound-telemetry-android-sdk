# Bearound Telemetry — AI agent setup prompt

Hover the block below and click the **copy icon** in its top-right corner to copy
the prompt, then paste it into your AI coding agent (Claude Code, Cursor, Copilot, …)
with your app's repo open. The agent reads the [SDK README](./README.md) and wires
the full integration.

```text
Integrate the Bearound Telemetry SDK (com.github.Bearound:bearound-telemetry-android-sdk)
into this native Android (Kotlin/Java) app. First READ the SDK's README end to end —
especially "Two integration modes", "Permissions" and "Quick Start" — then do ALL of
the following.

1. Detect the integration MODE first: search the app's dependencies for
   com.github.Bearound:bearound-android-sdk (the Bearound tracking SDK).
   - If present → COMPANION mode: the two SDKs are plug & play — both declare
     BLUETOOTH_SCAN WITH neverForLocation, so the merge is clean and the flag is
     preserved. Users who grant location get tracking + telemetry; users who deny
     it still produce telemetry.
   - If absent → STANDALONE mode: telemetry-only integration.
   In BOTH modes the neverForLocation flag must survive the merged manifest
   (step 4 audits it).

2. Install: add the JitPack repository — maven("https://jitpack.io") under
   dependencyResolutionManagement in settings.gradle(.kts) (or allprojects in a
   legacy root build.gradle) — then add
   implementation("com.github.Bearound:bearound-telemetry-android-sdk:main-SNAPSHOT")
   to the app module's build.gradle(.kts) and sync Gradle (pin the tagged version once
   a release exists). If the app module's compileSdk is below 35 or the project AGP is
   below 8.6.0, RAISE them (compileSdk 35+, AGP 8.6.0+) BEFORE syncing —
   androidx.core 1.16.0 is pulled transitively and requires it. AGP 8.6.0+ also needs
   Gradle wrapper 8.7+ — bump distributionUrl in
   gradle/wrapper/gradle-wrapper.properties in lockstep.

3. Permissions: the SDK's manifest merge already injects everything it needs
   (BLUETOOTH_SCAN with neverForLocation, legacy BLUETOOTH/BLUETOOTH_ADMIN ≤ API 30,
   INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, RECEIVE_BOOT_COMPLETED,
   POST_NOTIFICATIONS, FOREGROUND_SERVICE + FOREGROUND_SERVICE_CONNECTED_DEVICE).
   Do NOT re-declare them and do NOT add any location permission on behalf of this
   SDK — running without location is its purpose. In COMPANION mode the location
   permissions come from the tracking SDK and that is expected.

4. BOTH modes — audit the merged manifest: run
   ./gradlew :app:processDebugMainManifest and inspect
   app/build/intermediates/merged_manifests/debug/.../AndroidManifest.xml. CONFIRM
   BLUETOOTH_SCAN still carries usesPermissionFlags="neverForLocation" (the Bearound
   tracking SDK also declares it, so in companion mode the merge stays clean). If any
   OTHER third-party library dropped it, re-declare in the app manifest forcing the
   flag to win:
   <uses-permission android:name="android.permission.BLUETOOTH_SCAN"
   android:usesPermissionFlags="neverForLocation"
   tools:replace="android:usesPermissionFlags" /> — and add xmlns:tools to <manifest>.

5. Configure + start (Quick Start): get the singleton with
   BearoundTelemetrySDK.getInstance(context).
   - COMPANION mode: configure the TRACKING SDK first (it returns self), then hand
     the instance over — telemetry extracts the business token and device id from it
     so both SDKs report as the same device:
       val bearound = BeAroundSDK.getInstance(this).configure(businessToken = <ASK ME>)
       BearoundTelemetrySDK.getInstance(this).configure(bearound)
     Bundle BLUETOOTH_SCAN into the same permission launcher as the tracking SDK's
     location permissions so the user sees one flow.
   - STANDALONE mode: configure(businessToken = <ASK ME FOR IT>) and request
     BLUETOOTH_SCAN on Android 12+ (S+).
   Then call startScanning() once granted.

6. Validate: build, install on a physical device with a Bearound beacon nearby, and
   check logcat for tags BearoundTelemetrySDK* (scan registration, batch sync) —
   with, in STANDALONE mode on Android 12+, the system Location toggle turned OFF to
   prove the no-location regime; readings must keep flowing. On Moto/realme devices
   expect "harvest" log lines in standalone mode — that is the OEM-denylist workaround
   working, not an error. Report back: mode detected, merged-manifest flag state,
   and the logcat evidence of the first successful sync.

Ask me for: the business token; whether this app should also ship the Bearound
tracking SDK (decides the mode in step 1).
```
