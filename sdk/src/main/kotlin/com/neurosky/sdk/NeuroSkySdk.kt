package com.neurosky.sdk

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import com.neurosky.sdk.model.BrainWaveData
import com.neurosky.sdk.transport.BleTransport
import com.neurosky.sdk.transport.BtClassicTransport
import com.neurosky.sdk.transport.ConnectionState
import com.neurosky.sdk.transport.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * NeuroSky MindWave SDK 진입점.
 *
 * BLE를 우선 시도하고, 5초 내 연결 실패 시 BT Classic으로 자동 폴백한다.
 *
 * ```kotlin
 * val sdk = NeuroSkySdk(context)
 *
 * lifecycleScope.launch {
 *     sdk.connect("MindWave Mobile")
 *     sdk.dataFlow.collect { data ->
 *         println("Attention: ${data.attention}")
 *     }
 * }
 * ```
 */
class NeuroSkySdk(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val bleTransport = BleTransport(context)
    private val btTransport  = BtClassicTransport()

    private var activeTransport: Transport = bleTransport

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    val dataFlow: Flow<BrainWaveData> get() = activeTransport.dataFlow

    /**
     * 디바이스에 연결한다.
     * @param deviceAddress BLE MAC 주소 또는 "MindWave Mobile" 디바이스 이름
     */
    suspend fun connect(deviceAddress: String) {
        activeTransport = bleTransport
        bleTransport.connect(deviceAddress)

        // BLE 연결 타임아웃 — 5초 내 CONNECTED 미달성 시 BT Classic 폴백
        val bleConnected = waitForConnected(bleTransport, timeoutMs = 5_000L)
        if (!bleConnected) {
            bleTransport.disconnect()
            activeTransport = btTransport
            btTransport.connect(deviceAddress)
        }

        scope.launch {
            activeTransport.stateFlow.collect { state ->
                _connectionState.value = state
            }
        }
    }

    suspend fun disconnect() {
        activeTransport.disconnect()
    }

    suspend fun sendCommand(cmd: Byte) {
        activeTransport.sendCommand(cmd)
    }

    /**
     * BLE 스캔으로 [deviceName]을 포함하는 기기의 MAC 주소를 반환한다.
     *
     * 처음 한 번만 호출해 결과를 SharedPreferences 등에 캐시하면
     * 이후 [connect]가 스캔 없이 즉시 연결된다.
     *
     * @param deviceName 검색할 이름 (기본값: "MindWave Mobile"), 대소문자 구분 없음
     * @param timeoutMs  스캔 최대 대기시간 (기본 10초)
     * @return MAC 주소 ("F4:0E:11:22:33:44") 또는 타임아웃 시 null
     */
    suspend fun findDeviceAddress(
        deviceName: String = "MindWave Mobile",
        timeoutMs: Long = 10_000L
    ): String? {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        val scanner = adapter.bluetoothLeScanner ?: return null

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        val name = result.device.name ?: return
                        if (name.contains(deviceName, ignoreCase = true) && cont.isActive) {
                            scanner.stopScan(this)
                            cont.resume(result.device.address) { scanner.stopScan(this) }
                        }
                    }
                }
                scanner.startScan(callback)
                cont.invokeOnCancellation { scanner.stopScan(callback) }
            }
        }
    }

    private suspend fun waitForConnected(transport: Transport, timeoutMs: Long): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            transport.stateFlow.first { it == ConnectionState.CONNECTED }
        } != null
    }
}
