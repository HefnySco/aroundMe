<p align="center">
  <img src="docs/logo.svg" width="128" alt="Around Me logo" />
</p>

# Around Me

An Android app that passively scans nearby Wi-Fi access points and Bluetooth
devices, tags every sighting with time + GPS + speed, and detects devices that
move with you — followers, companions, your own gadgets.

Devices (identified by MAC address) can be attached to user-defined virtual
objects (person, place, car, ...) in a many-to-many relation, so the app can
alert you when "your car" or "a person you tagged" appears nearby.

## Features

- **Now tab** — live list of devices currently around you, with sorting
  (newest appeared / latest update / most seen), grouping by name, new-device
  highlight, and filters for stale and nameless random-MAC entries
- **Objects tab** — define virtual objects and attach devices by MAC or name;
  get alerted when a tagged object appears
- **Map tab** — osmdroid map of sightings with object/device modes, icons, and
  a heatmap overlay
- **Alerts tab** — moving-with-you detections, object-appeared alerts, and
  co-occurrence suggestions (devices frequently seen together)
- **History tab** — browse past sightings per device
- **Background tracking** — foreground service scans on a configurable
  interval, survives reboot via `BootReceiver`

## Detection logic

- Devices seen repeatedly at your location while you move are flagged as
  "moving with you"
- Devices attached to objects trigger an alert when they reappear after an
  absence
- Co-occurrence analysis suggests devices that may belong together
- Phones mostly advertise randomized MACs — those are tagged "(random MAC)"
  and same-name random MACs are merged for linking

## Build

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk` (debug-signed).

Toolchain: JDK 17, Android SDK platform 34, build-tools 34.0.0,
Gradle 8.14 (wrapper), AGP 8.4.2, Kotlin 2.0.21. minSdk 26, targetSdk 34.

## Permissions

- `ACCESS_FINE_LOCATION` (+ location services ON) — required by Android to get
  Wi-Fi scan results; also used to tag sightings with GPS
- `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` — BLE + classic scanning on API 31+
- `POST_NOTIFICATIONS` — alerts on API 33+
- `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE`,
  `RECEIVE_BOOT_COMPLETED` — background scanning

All data stays on-device in a local Room database — no accounts, no cloud.

## Layout

- `scan/` — `ScanService` (foreground scan loop: Wi-Fi `startScan`,
  `BluetoothLeScanner`, classic discovery, fused location, DB writes),
  `BleAdvParser`, `BootReceiver`
- `logic/Analyzer.kt` — moving-with-you detection, object-appeared alerts,
  co-occurrence suggestions
- `data/` — Room: `devices`, `sightings`, `objects`, `object_devices`,
  `alerts`
- `ui/` — Now / Objects / Map / Alerts / History tabs, device and object
  detail screens, settings
- `util/` — preferences, permissions, geo math, device classification

## Notes

- Wi-Fi scan results older than 90 s are dropped to avoid logging stale APs
- Scan interval is configurable (10 s – 2 min); Wi-Fi and Bluetooth scanning
  can be toggled independently
