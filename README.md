# NeuroSky MindWave SDK

Modern Kotlin SDK for NeuroSky MindWave EEG headsets — BLE + BT Classic, no TGC dependency.

## Installation

```kotlin
// build.gradle.kts
implementation("com.neurosky:mindwave-sdk:2.0.0")
```

## Quick Start

```kotlin
val sdk = NeuroSkySdk(context)

lifecycleScope.launch {
    sdk.connect("MindWave Mobile")  // BLE 우선, 실패 시 BT Classic 자동 폴백

    sdk.dataFlow.collect { data ->
        println("Attention:  ${data.attention}")
        println("Meditation: ${data.meditation}")
        println("Signal:     ${data.signalQuality}")
    }
}
```

## Simulator (실기기 없이 개발)

```kotlin
val simulator = SimulatorTransport()
simulator.setMode(SimulatorTransport.Mode.FOCUSED)

lifecycleScope.launch {
    simulator.connect("simulator")
    simulator.dataFlow.collect { data -> /* ... */ }
}
```

## Transport

| Transport | 연결 방식 | 조건 |
|---|---|---|
| BleTransport | BLE GATT | Android 5.0+ |
| BtClassicTransport | RFCOMM SPP | 페어링 필요 |
| SimulatorTransport | 가상 데이터 | 개발/테스트용 |

## BrainWaveData

| 필드 | 범위 | 설명 |
|---|---|---|
| `poorSignal` | 0~200 | 0=완벽, 200=무신호 |
| `attention` | 0~100 | 집중도 |
| `meditation` | 0~100 | 명상 |
| `delta` ~ `midGamma` | 0~... | EEG 주파수 파워 |
| `rawEeg` | List<Int> | 10샘플/패킷 (512Hz) |
| `signalQuality` | enum | GOOD/FAIR/POOR/NO_SIGNAL |

## Permissions

```xml
<!-- Android 12+ -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Android 11 이하 -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

## License

Apache License 2.0
