# NeuroSky MindWave Mobile Android SDK

[![JitPack](https://jitpack.io/v/nsk-bci/mindwave-sdk-android.svg)](https://jitpack.io/#nsk-bci/mindwave-sdk-android)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-API%2023%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

Modern Kotlin SDK for NeuroSky MindWave Mobile EEG headsets — BLE + BT Classic, no TGC dependency.

---

## Getting Started

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
    implementation("com.github.nsk-bci:mindwave-sdk-android:v2.0.1")
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

### Step 4 — Connect and stream

```kotlin
import com.neurosky.sdk.NeuroSkySdk
import com.neurosky.sdk.NeuroSkyCommand

val sdk = NeuroSkySdk(context)

lifecycleScope.launch {
    sdk.connect("MindWave Mobile")  // BLE first, falls back to BT Classic automatically

    // Set notch filter for your region (removes power-line noise)
    sdk.sendCommand(NeuroSkyCommand.NOTCH_60HZ)  // Korea/USA
    // sdk.sendCommand(NeuroSkyCommand.NOTCH_50HZ)  // Europe/China

    sdk.dataFlow.collect { data ->
        println("Attention  : ${data.attention}")
        println("Meditation : ${data.meditation}")
        println("Signal     : ${data.signalQuality}")
    }
}
```

That's it — four steps from zero to streaming EEG data.

> **Need more detail?** See the full [Developer Guide](docs/developer-guide.pdf) for architecture, all connection modes, signal quality handling, advanced patterns, and the complete API reference.

---

## Requirements

| | Minimum |
|---|---|
| Android | API 23 (Android 6.0) |
| Kotlin | 1.9+ |
| Bluetooth | BLE adapter (BLE mode) or Classic BT adapter (BT Classic mode) |
| Device pairing | Not required for BLE; required for BT Classic |

## Connection Modes

| Mode | Behavior | Pairing required? |
|---|---|---|
| Auto (default) | BLE first; auto-falls back to BT Classic after 5 sec | No |
| BLE only | Fastest, no pairing needed | No |
| BT Classic only | More stable in noisy RF environments | Yes |

```kotlin
// Auto (default) — BLE first, BT Classic fallback
sdk.connect("MindWave Mobile")

// BLE only
sdk.connect("MindWave Mobile", TransportMode.BLE)

// BT Classic only — pair the device first in Android Settings
sdk.connect("MindWave Mobile", TransportMode.BT_CLASSIC)
```

## Simulator (without a real device)

```kotlin
import com.neurosky.sdk.simulator.SimulatorTransport

val simulator = SimulatorTransport()
simulator.setMode(SimulatorTransport.Mode.FOCUSED)

lifecycleScope.launch {
    simulator.connect("simulator")
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

## Project Structure

```
sdk/src/main/kotlin/com/neurosky/sdk/
├── NeuroSkySdk.kt              Entry point (BLE first + BT Classic fallback)
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

## Changelog

### v2.0.1
- Published to JitPack

### v2.0.0
- Removed TGC (ThinkGear Connector) dependency entirely
- Android BLE GATT implementation
- Android RFCOMM SPP implementation
- Auto-fallback: BLE → BT Classic
- `Flow<BrainWaveData>` stream API
- Simulator modes: RANDOM / FOCUSED / RELAXED / POOR_SIGNAL
- Kotlin 1.9, Coroutines 1.7

## License

Apache License 2.0
