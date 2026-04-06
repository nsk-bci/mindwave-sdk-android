# NeuroSky MindWave Mobile Android SDK

[![JitPack](https://jitpack.io/v/nsk-bci/mindwave-sdk-android.svg)](https://jitpack.io/#nsk-bci/mindwave-sdk-android)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-API%2023%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

Modern Kotlin SDK for NeuroSky MindWave Mobile EEG headsets — BLE + BT Classic.

---

## Getting Started

> **Developer Guide:** [docs/developer-guide.pdf](docs/developer-guide.pdf)  
> Full architecture walkthrough, connection flow diagrams, signal quality handling, and advanced usage patterns.

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

    sdk.connect(address)
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

## Working with dataFlow

### Packet timing

BLE 모드에서는 두 characteristic이 서로 다른 속도로 패킷을 전송합니다.

| Characteristic | 포함 필드 | 전송 주기 |
|---|---|---|
| eSense `039afff8` | attention, meditation, EEG bands | ~1 Hz |
| RawEEG `039afff4` | `rawEeg` (10샘플) | ~51 Hz (512 Hz ÷ 10) |

`ThinkGearParser`는 상태를 누적합니다. 어느 characteristic이 트리거했든 emit된 `BrainWaveData`는 모든 필드의 **최신 누적값**을 담습니다.

### 주의 — `attention` 기반 필터

```kotlin
// 잘못된 패턴 — rawEEG 전용 세션에서 모든 패킷이 버려짐
sdk.dataFlow
    .filter { it.attention > 0 }  // eSense가 꺼져 있으면 attention은 항상 0
    .collect { ... }
```

`STOP_ESENSE`를 보내거나 `START_ESENSE`를 호출하지 않으면 디바이스는 attention 데이터를 보내지 않습니다. `attention`이 0으로 고정되어 위 필터는 모든 패킷을 무음으로 폐기합니다.

**올바른 패턴:**

```kotlin
// eSense 세션 — 값이 아닌 신호 품질로 필터
sdk.dataFlow
    .filter { it.signalQuality != SignalQuality.NO_SIGNAL }
    .collect { data ->
        println("Attention: ${data.attention}")
    }

// rawEEG 전용 세션
sdk.sendCommand(NeuroSkyCommand.STOP_ESENSE)
sdk.sendCommand(NeuroSkyCommand.START_RAW_EEG)
sdk.dataFlow
    .filter { it.rawEeg.isNotEmpty() }
    .collect { data ->
        data.rawEeg.forEach { sample -> processRawSample(sample) }
    }

// eSense + rawEEG 동시 사용 — 각 패킷에서 채워진 필드만 처리
sdk.sendCommand(NeuroSkyCommand.START_RAW_EEG)  // eSense는 기본 활성
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

## Troubleshooting

### JitPack dependency not resolving

JitPack은 첫 요청 시 빌드를 시작합니다(1–3분 소요). Gradle 싱크가 즉시 실패하면 아래 순서로 확인하세요.

**1. 빌드 로그 확인**

```
https://jitpack.io/com/github/nsk-bci/mindwave-sdk-android/v2.0.1/build.log
```

**2. 빌드 진행 중** — 로그에 "build in progress"가 표시되면 2–3분 후 Gradle 싱크 재시도.

**3. 빌드 실패** — 주요 원인:

| 원인 | 해결 방법 |
|---|---|
| Gradle 버전 불일치 | 로그의 에러 메시지 확인, 저장소 `gradle-wrapper.properties`와 비교 |
| Rate limit / 캐시 만료 | 버전 태그 대신 전체 커밋 SHA 사용 |
| 첫 빌드 실패 후 캐시됨 | 버전을 최신 태그 또는 커밋 SHA로 변경해 강제 재빌드 |

```kotlin
// 커밋 SHA로 강제 지정 (태그 캐시 우회)
implementation("com.github.nsk-bci:mindwave-sdk-android:FULL_COMMIT_SHA")
```

**4. Android Studio Offline 모드** — `File → Settings → Build → Gradle` 에서 *Offline work* 체크 해제.

---

## Changelog

### v2.0.1
- `NeuroSkySdk.findDeviceAddress(name, timeoutMs)` 추가 — BLE 스캔으로 디바이스 이름 → MAC 주소 반환, 결과 캐시 권장
- `sdk/consumer-rules.pro` 추가 — `BluetoothGattCallback` 5개 메서드 + 공개 API 클래스 R8 난독화 방지
- JitPack 배포 설정 — `settings.gradle.kts` `dependencyResolutionManagement` + JitPack 저장소 등록
- Sample 앱 UI 전면 개편 — MaterialCardView 기반 4-카드 레이아웃 (Signal Status / eSense / EEG Bands / Simulator Mode)
- README: Developer Guide 링크 상단 이동, MAC 주소 획득 패턴, ProGuard 섹션 추가

### v2.0.0
- BLE GATT Transport (`BleTransport`) — `connectGatt()` → CCCD 구독 → Handshake(`0x17`) → 데이터 수신
- BT Classic SPP Transport (`BtClassicTransport`) — RFCOMM `00001101-...` 소켓
- 자동 폴백 — BLE 5초 타임아웃 시 BT Classic 전환 (`withTimeoutOrNull(5_000)`)
- `ThinkGearParser` — BLE(`0xEA`/`0xEB`/`0xEC`) + BT Classic(`0xAA 0xAA` 헤더, 체크섬 검증) 동시 지원
- `BrainWaveData.signalQuality` — `poorSignal` 값 기반 GOOD/FAIR/POOR/NO_SIGNAL 자동 판정
- `SimulatorTransport` — FOCUSED/RELAXED/RANDOM/POOR_SIGNAL 모드, 1초 주기 emit
- Kotlin 1.9, Coroutines 1.7.3, minSdk 23

## License

Apache License 2.0
