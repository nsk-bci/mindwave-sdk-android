# ── NeuroSky MindWave SDK — consumer ProGuard rules ─────────────────────────
# Automatically applied to apps that depend on this SDK via consumerProguardFiles.

# ── BLE GATT callbacks ────────────────────────────────────────────────────────
# The Android BLE stack invokes these methods by name via reflection.
# R8 will rename them in release builds, causing BLE data to stop flowing silently.
-keep class * extends android.bluetooth.BluetoothGattCallback {
    public void onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int);
    public void onServicesDiscovered(android.bluetooth.BluetoothGatt, int);
    public void onDescriptorWrite(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattDescriptor, int);
    public void onCharacteristicChanged(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic);
    public void onCharacteristicChanged(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, byte[]);
}

# ── BLE scan callbacks ────────────────────────────────────────────────────────
-keep class * extends android.bluetooth.le.ScanCallback {
    public void onScanResult(int, android.bluetooth.le.ScanResult);
    public void onScanFailed(int);
}

# ── Public SDK API ────────────────────────────────────────────────────────────
-keep class com.neurosky.sdk.NeuroSkySdk { *; }
-keep class com.neurosky.sdk.NeuroSkyUUID { *; }
-keep class com.neurosky.sdk.NeuroSkyCommand { *; }
-keep interface com.neurosky.sdk.transport.Transport { *; }
-keep class com.neurosky.sdk.transport.ConnectionState { *; }

# ── Data model ────────────────────────────────────────────────────────────────
-keep class com.neurosky.sdk.model.BrainWaveData { *; }
-keep class com.neurosky.sdk.model.SignalQuality { *; }

# ── Parser ────────────────────────────────────────────────────────────────────
-keep class com.neurosky.sdk.parser.ThinkGearParser { *; }

# ── Simulator ─────────────────────────────────────────────────────────────────
-keep class com.neurosky.sdk.simulator.SimulatorTransport { *; }
-keep class com.neurosky.sdk.simulator.SimulatorTransport$Mode { *; }
