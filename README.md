# NeuroSky MindWave Mobile Android SDK

[![JitPack](https://jitpack.io/v/nsk-bci/mindwave-sdk-android.svg)](https://jitpack.io/#nsk-bci/mindwave-sdk-android)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-API%2023%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

Modern Kotlin SDK for NeuroSky MindWave Mobile EEG headsets — BLE + BT Classic.

---

## Getting Started

> [!TIP]
> **Before diving into the steps — read the [Developer Guide (PDF)](docs/developer-guide.pdf) first.**  
> It covers the full connection flow, BLE vs BT Classic internals, signal quality handling, packet timing, advanced patterns, and the complete API reference. Most integration questions are answered there.

### Step 1 — Add JitPack to repositories

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Step 2 — Add the dependency

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.github.nsk-bci:mindwave-sdk-android:v2.0.3")
}
```

### Step 3 — Declare Bluetooth permissions

```xml
<!-- AndroidManifest.xml -->

<!-- Android 12+ (API 31+) -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Android 6–11 (API 23–30) -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

> On Android 12+, runtime permission prompts for `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` are required before connecting.

### Step 4 — Find your device address

`connect()` takes a Bluetooth MAC address. Use `findDeviceAddress()` once on first launch to discover it, then cache it locally for faster subsequent connections.

```kotlin
val sdk = NeuroSkySdk(context)
val prefs = getSharedPreferences("ns_sdk", MODE_PRIVATE)

lifecycleScope.launch {
    val cached = prefs.getString("device_mac", null)
    val address = cached ?: sdk.findDeviceAddress("MindWave Mobile")?.also { mac ->
        prefs.edit().putString("device_mac", mac).apply()  // cache — skips scan next launch
    }

    if (address == null) {
        // device not found within 10 s — check power and permissions
        return@launch
    }

    sdk.connect(address)  // BLE — pass TransportType.BT_CLASSIC as second arg for BT Classic
    sdk.sendCommand(NeuroSkyCommand.NOTCH_60HZ)  // Korea/USA; use NOTCH_50HZ for Europe/China

    sdk.dataFlow.collect { data ->
        println("Attention  : ${data.attention}")
        println("Meditation : ${data.meditation}")
        println("Signal     : ${data.signalQuality}")
    }
}
```

That's it — four steps from zero to streaming EEG data.

---

## Requirements

| | Minimum |
|---|---|
| Android | API 23 (Android 6.0) |
| Kotlin | 1.9+ |
| Bluetooth | BLE adapter (BLE mode) or Classic BT adapter (BT Classic mode) |
| Device pairing | Not required for BLE; required for BT Classic |

## Connection Modes

`connect()` uses **exactly the transport you pass** — there is no automatic fallback. Pick one explicitly:

```kotlin
import com.neurosky.sdk.TransportType

// BLE — no device pairing required; omitting TransportType defaults to BLE
sdk.connect("AA:BB:CC:DD:EE:FF")
sdk.connect("AA:BB:CC:DD:EE:FF", TransportType.BLE)  // same as above, explicit

// BT Classic — pair the device in Android Settings first
sdk.connect("AA:BB:CC:DD:EE:FF", TransportType.BT_CLASSIC)
```

| Transport | When to choose | Pairing required? |
|---|---|---|
| `BLE` (default) | Standard use — no pairing, lower power | No |
| `BT_CLASSIC` | Noisy RF environments where BLE is unstable | Yes |

> **No automatic fallback.** If the chosen transport fails (timeout, adapter unavailable, pairing missing), an exception is thrown. The SDK does not silently retry with the other transport. Handle the exception and prompt the user to choose.

## Simulator (without a real device)

```kotlin
import com.neurosky.sdk.simulator.SimulatorTransport  // package: simulator, not transport

val simulator = SimulatorTransport()
simulator.setMode(SimulatorTransport.Mode.FOCUSED)

lifecycleScope.launch {
    simulator.connect("simulator")

    // Connection state: SimulatorTransport exposes stateFlow (Transport interface).
    // connectionState (StateFlow) is only available on NeuroSkySdk.
    simulator.stateFlow.collect { state -> /* CONNECTED after ~500 ms */ }
}

// Separate coroutine to collect data
lifecycleScope.launch {
    simulator.dataFlow.collect { data ->
        println("Attention: ${data.attention}")
    }
}
```

| Mode | Attention | Meditation | Use case |
|---|---|---|---|
| `RANDOM` | 0~100 (random) | 0~100 (random) | General testing |
| `FOCUSED` | 70~100 | 40~60 | Focused state UI testing |
| `RELAXED` | 20~50 | 70~100 | Relaxed state UI testing |
| `POOR_SIGNAL` | 0 | 0 | Signal loss / error handling test |

## BrainWaveData

| Property | Type | Range | Description |
|---|---|---|---|
| `timestamp` | `Long` | Unix ms | Time of reception |
| `poorSignal` | `Int` | 0~200 | 0=perfect, 200=no signal |
| `attention` | `Int` | 0~100 | eSense attention level |
| `meditation` | `Int` | 0~100 | eSense meditation level |
| `delta` | `Int` | 0~∞ | 0.5~2.75 Hz |
| `theta` | `Int` | 0~∞ | 3.5~6.75 Hz |
| `lowAlpha` | `Int` | 0~∞ | 7.5~9.25 Hz |
| `highAlpha` | `Int` | 0~∞ | 10~11.75 Hz |
| `lowBeta` | `Int` | 0~∞ | 13~16.75 Hz |
| `highBeta` | `Int` | 0~∞ | 18~29.75 Hz |
| `lowGamma` | `Int` | 0~∞ | 31~39.75 Hz |
| `midGamma` | `Int` | 0~∞ | 41~49.75 Hz |
| `rawEeg` | `List<Int>` | -32768~32767 | 512Hz, 10 samples/packet |
| `eyeBlink` | `Int` | 0~255 | Eye blink intensity |
| `signalQuality` | `SignalQuality` | enum | NO_SIGNAL/POOR/FAIR/GOOD |

## Working with dataFlow

### Timing — collect AFTER connect()

`sdk.dataFlow` is a property getter that returns the currently active transport's flow. Always start collecting **after** `connect()` returns — not before.

```kotlin
// Correct — collect inside the same coroutine after connect()
lifecycleScope.launch {
    sdk.connect("AA:BB:CC:DD:EE:FF")
    sdk.dataFlow.collect { data -> /* ... */ }
}

// Wrong — sdk.dataFlow is evaluated before connect() sets the transport
val flow = sdk.dataFlow       // captured before connect()
sdk.connect("AA:BB:CC:DD:EE:FF")
flow.collect { }              // may collect from an idle transport
```

### Packet timing

In BLE mode, two characteristics transmit packets at different rates.

| Characteristic | Fields | Rate |
|---|---|---|
| eSense `039afff8` | attention, meditation, EEG bands | ~1 Hz |
| RawEEG `039afff4` | `rawEeg` (10 samples) | ~51 Hz (512 Hz ÷ 10) |

`ThinkGearParser` accumulates state. Regardless of which characteristic triggered the emit, each `BrainWaveData` object contains the latest accumulated value of every field.

### Caution — attention-based filter

```kotlin
// Wrong pattern — drops all packets in rawEEG-only sessions
sdk.dataFlow
    .filter { it.attention > 0 }  // attention is always 0 when eSense is off
    .collect { ... }
```

If `STOP_ESENSE` is sent or `START_ESENSE` is never called, the device does not transmit attention data. `attention` stays at 0 and the filter silently drops every packet.

**Correct patterns:**

```kotlin
// eSense session — filter by signal quality, not value
sdk.dataFlow
    .filter { it.signalQuality != SignalQuality.NO_SIGNAL }
    .collect { data ->
        println("Attention: ${data.attention}")
    }

// rawEEG-only session
sdk.sendCommand(NeuroSkyCommand.STOP_ESENSE)
sdk.sendCommand(NeuroSkyCommand.START_RAW_EEG)
sdk.dataFlow
    .filter { it.rawEeg.isNotEmpty() }
    .collect { data ->
        data.rawEeg.forEach { sample -> processRawSample(sample) }
    }

// eSense + rawEEG simultaneously — process only the populated fields in each packet
sdk.sendCommand(NeuroSkyCommand.START_RAW_EEG)  // eSense is active by default
sdk.dataFlow.collect { data ->
    if (data.rawEeg.isNotEmpty()) processRawSamples(data.rawEeg)
    if (data.attention > 0)       updateEsenseUI(data)
}
```

## Commands

```kotlin
// Notch filter — removes power-line noise (call after connecting)
sdk.sendCommand(NeuroSkyCommand.NOTCH_60HZ)  // Korea/USA (60Hz)
sdk.sendCommand(NeuroSkyCommand.NOTCH_50HZ)  // China/Europe (50Hz)

// Raw EEG stream (disabled by default)
sdk.sendCommand(NeuroSkyCommand.START_RAW_EEG)
sdk.sendCommand(NeuroSkyCommand.STOP_RAW_EEG)
```

## Transport

| Transport | Method | Requirement |
|---|---|---|
| `BleTransport` | BLE GATT | Android 6.0+, BLE adapter |
| `BtClassicTransport` | RFCOMM SPP | Paired device in Android Settings |
| `SimulatorTransport` | Virtual data | For development/testing |

## ProGuard / R8

The SDK ships consumer ProGuard rules (`consumer-rules.pro`) via `consumerProguardFiles` — **no action required** in most apps.

If you maintain your own rules and override the SDK's, add at minimum:

```proguard
# BLE GATT callbacks — Android BLE stack calls these by name via reflection.
# Omitting this causes BLE data to stop silently in release builds.
-keep class * extends android.bluetooth.BluetoothGattCallback {
    public void onConnectionStateChange(...);
    public void onServicesDiscovered(...);
    public void onDescriptorWrite(...);
    public void onCharacteristicChanged(...);
}
-keep class com.neurosky.sdk.** { *; }
```

> **Symptom of missing rules:** the app works perfectly in debug but receives no EEG data after a release build.

## Project Structure

```
sdk/src/main/kotlin/com/neurosky/sdk/
├── NeuroSkySdk.kt              Entry point (BLE or BT Classic, explicit transport selection)
├── NeuroSkyUUID.kt             BLE UUID constants, command byte constants
├── model/
│   └── BrainWaveData.kt        EEG data model
├── transport/
│   ├── Transport.kt            Common interface, ConnectionState enum
│   ├── BleTransport.kt         Android BLE GATT implementation
│   └── BtClassicTransport.kt   Android RFCOMM SPP implementation
├── parser/
│   └── ThinkGearParser.kt      ThinkGear packet parser
└── simulator/
    └── SimulatorTransport.kt   Simulator for development
```

## Troubleshooting

### JitPack dependency not resolving

JitPack starts building on first request (1–3 minutes). If Gradle sync fails immediately, follow these steps.

**1. Check the build log**

```
https://jitpack.io/com/github/nsk-bci/mindwave-sdk-android/v2.0.3/build.log
```

**2. Build in progress** — if the log shows "build in progress", wait 2–3 minutes and retry Gradle sync.

**3. Build failure** — common causes:

| Cause | Fix |
|---|---|
| Gradle version mismatch | Check the error in the build log; compare with the repo's `gradle-wrapper.properties` |
| Rate limit / cache expiry | Use a full commit SHA instead of a version tag |
| First build failed and cached | Change the version to the latest tag or commit SHA to force a rebuild |

```kotlin
// Force a specific commit SHA (bypasses tag cache)
implementation("com.github.nsk-bci:mindwave-sdk-android:FULL_COMMIT_SHA")
```

**4. Android Studio Offline mode** — uncheck *Offline work* in `File → Settings → Build → Gradle`.

---

## Changelog

### v2.0.1
- `BleTransport` / `BtClassicTransport` — `callbackFlow` → `MutableSharedFlow`: GATT/socket lifetime now fully controlled by `connect()`/`disconnect()`, stopping collection no longer drops the connection
- `ThinkGearParser` — BT Classic `0x83` bounds guard: prevents `IndexOutOfBoundsException` on truncated payloads
- `NeuroSkySdk` KDoc — `deviceAddress` parameter now explicitly states MAC address format

### v2.0.0
- BLE GATT Transport (`BleTransport`) — `connectGatt()` → CCCD subscribe → Handshake(`0x17`) → data stream
- BT Classic SPP Transport (`BtClassicTransport`) — RFCOMM `00001101-...` socket
- `ThinkGearParser` — BLE(`0xEA`/`0xEB`/`0xEC`) + BT Classic(`0xAA 0xAA` header, checksum validation)
- `BrainWaveData.signalQuality` — derived from `poorSignal`: GOOD/FAIR/POOR/NO_SIGNAL
- `SimulatorTransport` — FOCUSED/RELAXED/RANDOM/POOR_SIGNAL modes, emits every 1 second
- Kotlin 1.9, Coroutines 1.7.3, minSdk 23

## License

Apache License 2.0
