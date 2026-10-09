# API Reference

## NeuroSkySdk

진입점 클래스. MindWave Mobile 2와의 BLE 연결을 관리한다.

```kotlin
class NeuroSkySdk(context: Context)
```

| 멤버 | 타입 | 설명 |
|---|---|---|
| `connectionState` | `StateFlow<ConnectionState>` | 현재 연결 상태 (NeuroSkySdk 전용; Transport 직접 사용 시에는 `stateFlow` 사용) |
| `dataFlow` | `Flow<BrainWaveData>` | 실시간 뇌파 데이터 스트림 — `connect()` 이후에 수집할 것 |
| `connect(deviceAddress)` | `suspend fun` | BLE로 연결 |
| `disconnect()` | `suspend fun` | 연결 해제 |
| `sendCommand(cmd: Byte)` | `suspend fun` | 헤드셋에 명령 전송 |
| `findDeviceAddress(deviceName, timeoutMs)` | `suspend fun` | BLE 스캔으로 MAC 주소 반환. 타임아웃 시 null |

> **dataFlow 타이밍 주의:** `sdk.dataFlow`는 호출 시점의 activeTransport 를 반환하는 getter입니다.  
> `connect()` 호출 전에 캡처하면 idle 상태의 Transport flow를 구독하게 됩니다.  
> 반드시 `connect()` 완료 후 같은 coroutine 안에서 collect하세요.

---

## BrainWaveData

```kotlin
data class BrainWaveData(...)
```

| 필드 | 타입 | 범위 | 설명 |
|---|---|---|---|
| `timestamp` | `Long` | — | 수신 시각 (ms) |
| `poorSignal` | `Int` | 0~200 | 0=완벽, 200=무신호 |
| `attention` | `Int` | 0~100 | 집중도 eSense |
| `meditation` | `Int` | 0~100 | 명상 eSense |
| `delta` | `Int` | 0~... | 0.5~2.75 Hz 파워 |
| `theta` | `Int` | 0~... | 3.5~6.75 Hz 파워 |
| `lowAlpha` | `Int` | 0~... | 7.5~9.25 Hz 파워 (`alphaLow` 아님) |
| `highAlpha` | `Int` | 0~... | 10~11.75 Hz 파워 (`alphaHigh` 아님) |
| `lowBeta` | `Int` | 0~... | 13~16.75 Hz 파워 (`betaLow` 아님) |
| `highBeta` | `Int` | 0~... | 18~29.75 Hz 파워 (`betaHigh` 아님) |
| `lowGamma` | `Int` | 0~... | 31~39.75 Hz 파워 |
| `midGamma` | `Int` | 0~... | 41~49.75 Hz 파워 (`highGamma` 아님) |
| `rawEeg` | `List<Int>` | — | 10샘플/패킷 (512 Hz), 부호 있는 정수 |
| `eyeBlink` | `Int` | 0~255 | 눈 깜빡임 강도 |
| `signalQuality` | `SignalQuality` | enum | poorSignal 기반 자동 계산 |

> **필드명 패턴:** `low`/`high`/`mid` 가 **앞**에 옵니다 — `lowAlpha`, `highAlpha`, `lowBeta`, `highBeta`, `lowGamma`, `midGamma`.  
> `alphaLow`, `betaHigh` 등의 형태는 컴파일 오류입니다.

### SignalQuality

| 값 | poorSignal 조건 | 설명 |
|---|---|---|
| `GOOD` | == 0 | 양호 |
| `FAIR` | 1~50 | 보통 |
| `POOR` | 51~199 | 불량 |
| `NO_SIGNAL` | == 200 | 신호 없음 |

---

## ConnectionState

```kotlin
enum class ConnectionState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED, ERROR }
```

---

## Transport (interface)

```kotlin
interface Transport {
    val dataFlow: Flow<BrainWaveData>
    val stateFlow: Flow<ConnectionState>
    suspend fun connect(deviceAddress: String)
    suspend fun disconnect()
    suspend fun sendCommand(cmd: Byte)
}
```

구현체: `BleTransport`

---

## NeuroSkyUUID

```kotlin
object NeuroSkyUUID
```

| 상수 | UUID | 설명 |
|---|---|---|
| `ESENSE` | 039afff8-... | eSense 특성 (Attention/Meditation) |
| `HANDSHAKE` | 039affa0-... | 핸드셰이크/명령 특성 |
| `RAW_EEG` | 039afff4-... | Raw EEG 특성 |
| `CCCD` | 00002902-... | Client Characteristic Config Descriptor |

---

## NeuroSkyCommand

```kotlin
object NeuroSkyCommand
```

| 상수 | 값 | 설명 |
|---|---|---|
| `START_RAW_EEG` | 0x15 | Raw EEG 스트리밍 시작 |
| `STOP_RAW_EEG` | 0x16 | Raw EEG 스트리밍 중지 |
| `START_ESENSE` | 0x17 | eSense 데이터 시작 |
| `STOP_ESENSE` | 0x18 | eSense 데이터 중지 |
| `NOTCH_50HZ` | 0x1B | 50Hz 노치 필터 (중국/유럽) |
| `NOTCH_60HZ` | 0x1C | 60Hz 노치 필터 (한국/미국) |
