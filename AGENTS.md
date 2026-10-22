# Around Me

Android app that passively scans Wi-Fi APs and Bluetooth devices, tags every
sighting with time + GPS + speed, and detects devices that move with you
(followers/companions). Devices (MACs) can be attached to user-defined virtual
objects (person/place/car/...) in a many-to-many relation.

## Build

```bash
export JAVA_HOME=/home/mhefny/android-build/jdk
export ANDROID_HOME=/home/mhefny/android-build/sdk
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug --no-daemon
```

APK output: `app/build/outputs/apk/debug/app-debug.apk` (debug-signed).

Toolchain: JDK 17, Android SDK platform android-34, build-tools 34.0.0,
Gradle 8.14 (wrapper), AGP 8.4.2, Kotlin 2.0.21 (kapt falls back to 1.9 stubs).

## Layout

- `scan/ScanService.kt` — foreground service; scan loop (Wi-Fi `startScan`,
  BLE `BluetoothLeScanner`, classic BT discovery) + fused location + DB writes
- `logic/Analyzer.kt` — moving-with-you detection, object-appeared alerts,
  co-occurrence suggestions
- `data/` — Room: `devices`, `sightings`, `objects`, `object_devices`, `alerts`
- `ui/` — Now / Objects / Map (osmdroid) / Alerts tabs, device + object detail
- `util/Prefs.kt` — scan toggles + interval + last known location

## Runtime notes

- Needs `ACCESS_FINE_LOCATION` (+ location services ON) for Wi-Fi scan results;
  `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` on API 31+; notifications on API 33+.
- Wi-Fi scan results older than 90 s are dropped to avoid logging stale APs.
- Phones mostly advertise randomized MACs — expect "(random MAC)" tagging.
