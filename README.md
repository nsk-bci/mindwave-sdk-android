# NeuroSky MindWave SDK

Modern Kotlin SDK for NeuroSky MindWave EEG headsets — BLE + BT Classic, no TGC dependency.

## Installation

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
implementation("com.github.nsk-bci:mindwave-sdk-android:v2.0.1")
```

## Quick Start

```kotlin
val sdk = NeuroSkySdk(context)

lifecycleScope.launch {
    sdk.connect("MindWave Mobile")  // BLE first, falls back to BT Classic automatically

    sdk.dataFlow.collect { data ->
        println("Attention:  ${data.attention}")
        println("Meditation: ${data.meditation}")
        println("Signal:     ${data.signalQuality}")
    }
}
```

## Simulator (without a real device)

```kotlin
val simulator = SimulatorTransport()
simulator.setMode(SimulatorTransport.Mode.FOCUSED)

lifecycleScope.launch {
    simulator.connect("simulator")
    simulator.dataFlow.collect { data -> /* ... */ }
}
```

## Transport

| Transport | Method | Requirement |
|---|---|---|
| BleTransport | BLE GATT | Android 5.0+ |
| BtClassicTransport | RFCOMM SPP | Paired device required |
| SimulatorTransport | Virtual data | For development/testing |

## BrainWaveData

| Field | Range | Description |
|---|---|---|
| `poorSignal` | 0~200 | 0=perfect, 200=no signal |
| `attention` | 0~100 | Attention level |
| `meditation` | 0~100 | Meditation level |
| `delta` ~ `midGamma` | 0~... | EEG frequency power |
| `rawEeg` | List<Int> | 10 samples/packet (512Hz) |
| `signalQuality` | enum | GOOD/FAIR/POOR/NO_SIGNAL |

## Permissions

```xml
<!-- Android 12+ -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Android 11 and below -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

## License

Apache License 2.0
