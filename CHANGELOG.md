# Changelog

All notable changes to the NeuroSky MindWave Mobile Android SDK are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

v7.0.0 continues the MindWave SDK line (legacy 4.x), rebuilt from scratch for the BLE-only MindWave Mobile 2.
Releases before 7.0.0 are documented in the [legacy changelog (v2.0.5)](https://github.com/nsk-bci/mindwave-sdk-android/blob/v2.0.5/CHANGELOG.md).

## [Unreleased]

### Removed
- Bluetooth Classic transport (BLE-only from v7.0.0)

### Added
- eyeBlink parsing

## [7.0.0] - TBD

First release of the renewed MindWave SDK line for Android.

### Added
- `NeuroSkySdk` entry point: `connect(deviceAddress, transport)`, `disconnect()`, `sendCommand(cmd)`, `dataFlow`, `connectionState`
- `findDeviceAddress(deviceName)` to look up a headset's MAC address with a BLE scan
- BLE transport (default) with reliable, serialized GATT writes: `sendCommand()` suspends until the headset acknowledges the write and throws on failure
- Bluetooth Classic (SPP) transport, selected explicitly with `TransportType.BT_CLASSIC` (no automatic fallback)
- `ThinkGearParser` for BLE eSense (`0xEA`/`0xEB`/`0xEC`), Raw EEG, and ThinkGear serial packets
- `BrainWaveData` model with eSense values, eight EEG bands, Raw EEG (512 Hz), and derived `signalQuality`
- `SimulatorTransport` (`RANDOM` / `FOCUSED` / `RELAXED` / `POOR_SIGNAL`) for development without a headset
- `NeuroSkyCommand` constants for eSense, Raw EEG, and 50/60 Hz notch filter control
- Consumer ProGuard/R8 rules shipped with the AAR
- `LICENSE` (Apache License 2.0) and `NOTICE`
- `jitpack.yml` pinning the JitPack build to JDK 17

### Changed
- Version scheme realigned with the MindWave SDK line (legacy 4.x)
- The published version now comes from the Git tag instead of a hard-coded value

### Removed
- `publish.yml` ("Publish to Maven Central"): the SDK is distributed via JitPack, which builds on demand from tags
- Developer guide PDFs: superseded by [`docs/developer-guide.md`](docs/developer-guide.md)
