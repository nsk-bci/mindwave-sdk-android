# Changelog

## [2.0.0] — 2026-03-31

### Breaking Changes
- TGC(ThinkGear Connector) 완전 제거 — PC 데몬 불필요
- Android 최소 지원 버전: API 23 (Android 6.0)

### Added
- BLE GATT Transport (`BleTransport`) — MindWave Mobile BLE 직접 연결
- BT Classic SPP Transport (`BtClassicTransport`) — RFCOMM 소켓 연결
- 자동 폴백 전략 — BLE 5초 실패 시 BT Classic으로 전환
- `NeuroSkySdk` 통합 진입점 — 단일 API로 BLE/BT 추상화
- `ThinkGearParser` — BLE 패킷(0xEA/EB/EC) + BT Classic Serial 프로토콜 지원
- `SimulatorTransport` — 실기기 없이 FOCUSED/RELAXED/RANDOM/POOR_SIGNAL 모드 테스트
- `BrainWaveData.signalQuality` — GOOD/FAIR/POOR/NO_SIGNAL 자동 계산
- Kotlin Coroutines Flow 기반 비동기 데이터 스트림
- Maven Central 배포 설정

### Removed
- `ThinkGearConnector` 의존성
- TCP 소켓 통신 레이어
- Java 기반 레거시 API

---

## [1.x] — Legacy (NeuroSky 공식 SDK)
- TGC 기반 TCP 통신
- Java API
