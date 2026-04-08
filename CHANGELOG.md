# Changelog

## [2.0.0] — 2026-03-31

### Breaking Changes
- TGC(ThinkGear Connector) 완전 제거 — `ThinkGearConnector` 클래스 및 TCP 소켓 레이어 삭제
- Android 최소 지원 버전: API 23 (Android 6.0) — `BluetoothLeScanner` 의존

### Added

#### BLE Transport (`BleTransport.kt`)
- `BluetoothLeScanner`로 `MindWave Mobile` 디바이스 스캔
- `connectGatt()` → `onServicesDiscovered()` 후 eSense(`039afff8`) + RawEEG(`039afff4`) characteristic 알림 구독
- `onDescriptorWrite()` 완료 후 Handshake 전송 → 데이터 수신 시작
- 핸드셰이크 패킷: 20바이트 고정, `[0]=0x77`, `[2]=명령바이트`, `[19]=체크섬`

#### BT Classic Transport (`BtClassicTransport.kt`)
- SPP UUID `00001101-0000-1000-8000-00805f9b34fb`로 `createRfcommSocketToServiceRecord()` 연결
- `InputStream` 백그라운드 스레드에서 1바이트씩 읽어 `ThinkGearParser.parseByte()` 위임

#### ThinkGear Parser (`ThinkGearParser.kt`)
- **BLE 모드** — `parse(uuid, bytes)`: characteristic UUID 기반 분기
  - `0xEA` 패킷: `bytes[6]`=PoorSignal, `bytes[8]`=Attention, `bytes[10]`=Meditation
  - `0xEB` 패킷: `bytes[5~7]`=Delta, `bytes[9~11]`=Theta, `bytes[13~15]`=LowAlpha, `bytes[17~19]`=HighAlpha
  - `0xEC` 패킷: `bytes[5~7]`=LowBeta, `bytes[9~11]`=HighBeta, `bytes[13~15]`=LowGamma, `bytes[17~19]`=MidGamma
  - RawEEG: 20바이트 → 2바이트씩 10샘플, `raw > 32768이면 raw -= 65536`(부호처리)
- **BT Classic 모드** — `parseByte(byte)`: `0xAA 0xAA` 동기 헤더 → PLENGTH → PAYLOAD → 체크섬 검증
  - 코드 `0x02`=PoorSignal, `0x04`=Attention, `0x05`=Meditation, `0x16`=Blink
  - 코드 `0x80`=Raw EEG 2바이트, `0x83`=EEG Power 24바이트(8밴드 × 3바이트 빅엔디언)
  - 체크섬: `(payload 합산 XOR 0xFF) AND 0xFF`

#### SDK Entry Point (`NeuroSkySdk.kt`)
- `connect(deviceAddress, transport)`: 지정한 Transport로 연결. 기본값 BLE. BLE→BT Classic 자동 폴백 없음 — 연결 방식은 호출자가 명시적으로 선택
- `dataFlow: Flow<BrainWaveData>` — 활성 Transport를 통한 단일 데이터 스트림
- `sendCommand(cmd: Byte)` — 활성 Transport에 명령 전달

#### Data Model (`BrainWaveData.kt`)
- `signalQuality: SignalQuality` — `poorSignal == 200`→NO_SIGNAL, `> 50`→POOR, `> 0`→FAIR, `== 0`→GOOD

#### Simulator (`SimulatorTransport.kt`)
- `Mode.FOCUSED`: Attention 70~100, Meditation 40~60, Delta 10k~50k, Beta 15k~40k
- `Mode.RELAXED`: Attention 20~50, Meditation 70~100, Delta 20k~80k, Alpha 10k~30k
- `Mode.POOR_SIGNAL`: PoorSignal 150~200, Attention/Meditation = 0
- `Mode.RANDOM`: 전 필드 랜덤, PoorSignal 0~30
- 1초 주기 emit, `setMode()` 호출 시 다음 emit부터 즉시 반영

#### Constants (`NeuroSkyUUID.kt`, `NeuroSkyCommand.kt`)
- eSense UUID: `039afff8-2c94-11e3-9e06-0002a5d5c51b`
- Handshake UUID: `039affa0-2c94-11e3-9e06-0002a5d5c51b`
- RawEEG UUID: `039afff4-2c94-11e3-9e06-0002a5d5c51b`
- CCCD UUID: `00002902-0000-1000-8000-00805f9b34fb`
- `START_ESENSE=0x17`, `STOP_ESENSE=0x18`, `START_RAW_EEG=0x15`, `STOP_RAW_EEG=0x16`
- `NOTCH_50HZ=0x1B`(중국/유럽), `NOTCH_60HZ=0x1C`(한국/미국)

### Removed
- `ThinkGearConnector` 클래스 (TCP 소켓 기반)
- `TGCService` — PC 데몬 의존 TCP 통신 레이어
- Java 기반 레거시 API 전체

---

## [2.0.1] — 2026-04-06

### Added

#### `NeuroSkySdk.findDeviceAddress()` (`NeuroSkySdk.kt`)
- BLE 스캔으로 디바이스 이름에 `deviceName`을 포함하는 기기의 MAC 주소를 반환하는 suspend 함수 추가
- `withTimeoutOrNull(timeoutMs)` + `suspendCancellableCoroutine` 조합으로 타임아웃·취소 안전 처리
- `ScanCallback.onScanResult`에서 이름 매칭 즉시 `scanner.stopScan()` 호출 — 불필요한 스캔 지속 방지
- 반환값 `String?` — 타임아웃 시 null, 호출자가 SharedPreferences 등에 캐시해 재사용 권장

#### `sdk/consumer-rules.pro` (신규 파일)
- `BluetoothGattCallback` 구현체 5개 메서드 명시적 보호:
  `onConnectionStateChange`, `onServicesDiscovered`, `onDescriptorWrite`,
  `onCharacteristicChanged` (API ≤32 / API 33+ 오버로드 각각)
- `ScanCallback.onScanResult`, `onScanFailed` 보호 — `findDeviceAddress` 내 익명 클래스 대상
- 공개 API 클래스 전체 (`NeuroSkySdk`, `NeuroSkyUUID`, `NeuroSkyCommand`, `Transport`,
  `ConnectionState`, `BrainWaveData`, `SignalQuality`, `ThinkGearParser`, `SimulatorTransport`)
- `sdk/build.gradle.kts`의 `consumerProguardFiles("consumer-rules.pro")`로 소비자 앱에 자동 적용

### Changed

#### JitPack 배포 (`settings.gradle.kts`)
- `dependencyResolutionManagement.repositories`에 `maven { url = uri("https://jitpack.io") }` 추가
- `groupId = "com.github.nsk-bci"`, `artifactId = "mindwave-sdk-android"` 로 Maven 좌표 확정

#### Sample 앱 UI (`sample/`)
- `activity_main.xml` 전면 재작성 — 기존 단순 4× `TextView` → MaterialCardView 기반 4-카드 대시보드
  - **Signal Status 카드**: 신호 품질별 색상 도트 + GOOD/FAIR/POOR/NO SIGNAL 텍스트
  - **eSense 카드**: Attention / Meditation 수치 + 색상별 `ProgressBar` (max=100)
  - **EEG Bands 카드**: δθαβγ 8밴드 각각 색상별 `ProgressBar` (max=100,000) + 실수치
  - **Simulator Mode 카드**: RANDOM / FOCUSED / RELAXED / POOR SIGNAL 버튼, 선택 상태 하이라이트
- `MainActivity.kt` ViewBinding으로 전환, `SimulatorTransport.setMode()` 실시간 전환 지원
- `res/values/themes.xml` 추가 — `Theme.MaterialComponents.Light.NoActionBar` 기반
- `res/values/colors.xml` 추가 — signalGood `#2E7D32`, signalFair `#E65100`, signalPoor `#C62828` 등
- `res/drawable/bg_signal_dot.xml` 추가 — 신호 품질 도트용 oval drawable

#### README
- Developer Guide PDF 링크를 `## Getting Started` 상단으로 이동 (기존: 섹션 하단 footnote)
- Step 4 개편 — `connect(address)` 사용법 + `findDeviceAddress()` 캐시 패턴 예제 추가
- ProGuard / R8 섹션 신규 추가 — 릴리즈 빌드 무음 실패 증상 및 최소 규칙 명시
- Troubleshooting 섹션 신규 추가 — JitPack 빌드 실패 4가지 원인·해결책
- Working with dataFlow 섹션 신규 추가 — 패킷 타이밍 표, `filter { attention > 0 }` 안티패턴 및 올바른 3가지 패턴

---

## [2.0.2] — 2026-04-08

### Fixed (문서 정정 · 이슈 #34 대응)

#### 이슈 1 — `TransportMode` 오타 수정
- README / developer-guide 의 `TransportMode.BT_CLASSIC` → `TransportType.BT_CLASSIC` 수정
- 실제 소스(`NeuroSkySdk.kt`)의 열거형은 처음부터 `TransportType`이었으나 문서가 잘못 기재됨

#### 이슈 2 — `SimulatorTransport` API 명확화
- `connectionState: StateFlow<ConnectionState>` 는 `NeuroSkySdk` 전용 래퍼 프로퍼티
- `SimulatorTransport` (및 모든 `Transport` 구현체)는 `stateFlow: Flow<ConnectionState>` 노출
- README Simulator 예시 코드에 올바른 API 명시 및 패키지 경로(`com.neurosky.sdk.simulator`) 강조

#### 이슈 3 — `BrainWaveData` 필드명 명확화
- EEG 밴드 필드명이 `alphaLow/alphaHigh` 가 아닌 `lowAlpha/highAlpha` 형식임을 API_REFERENCE 에 명시
- developer-guide 에 "주의: 잘못 쓰기 쉬운 필드명" 표 추가

#### 이슈 4 — `dataFlow` 수집 타이밍 가이드 추가
- `sdk.dataFlow`는 호출 시점의 `activeTransport`를 반환하는 getter
- `connect()` 완료 이전에 캡처하면 idle Transport의 flow를 구독할 수 있음
- README "Working with dataFlow" 섹션에 타이밍 규칙 및 잘못된 패턴/올바른 패턴 코드 추가
- developer-guide 에 동일 내용 추가

#### 이슈 5 — BLE→BT Classic 자동 폴백 오해 해소
- CHANGELOG v2.0.0 의 "BLE 5초 시도 후 BT Classic 자동 폴백" 설명이 실제 구현과 달랐음 → 수정
- `NeuroSkySdk.connect()`는 전달한 `TransportType` 하나만 사용; 내부 폴백 없음
- README Connection Modes 섹션에 "No automatic fallback" 명시

---

## [1.x] — Legacy (NeuroSky 공식 SDK)
- TGC 기반 TCP 통신 (`ThinkGearConnector.connect("127.0.0.1", 13854)`)
- Java API, 콜백 방식
