package com.neurosky.sdk

import android.bluetooth.BluetoothManager
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** 연결에 사용할 트랜스포트 종류. */
enum class TransportType { BLE, BT_CLASSIC }

/**
 * NeuroSky MindWave SDK 진입점.
 *
 * 기본 트랜스포트는 BLE이며, BT Classic은 [TransportType.BT_CLASSIC]으로 명시해야 한다.
 *
 * ```kotlin
 * val sdk = NeuroSkySdk(context)
 *
 * lifecycleScope.launch {
 *     // BLE 연결 (기본) — MAC 주소 필요. findDeviceAddress()로 조회 가능
 *     sdk.connect("AA:BB:CC:DD:EE:FF")
 *
 *     // BT Classic 연결 (명시적 선택)
 *     // sdk.connect("AA:BB:CC:DD:EE:FF", TransportType.BT_CLASSIC)
 *
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

    /** BLE 내부 로그를 UI로 전달하는 콜백 설정 */
    fun setLogger(log: (String) -> Unit) {
        bleTransport.logger = log
    }

    private var activeTransport: Transport = bleTransport

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)

    /**
     * 현재 연결 상태를 나타내는 [StateFlow].
     *
     * [NeuroSkySdk] 전용 래퍼로, 내부 트랜스포트의 [Transport.stateFlow]를 구독해
     * 상태 변화를 이 프로퍼티에 미러링한다.
     *
     * **직접 Transport를 사용할 때는 이 프로퍼티 대신 [Transport.stateFlow]를 구독할 것.**
     * [SimulatorTransport] 등 [Transport] 구현체는 `connectionState`를 노출하지 않는다.
     *
     * ```kotlin
     * // NeuroSkySdk 사용 시
     * sdk.connectionState.collect { state -> ... }
     *
     * // Transport 직접 사용 시
     * simulatorTransport.stateFlow.collect { state -> ... }
     * ```
     */
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    val dataFlow: Flow<BrainWaveData> get() = activeTransport.dataFlow

    /**
     * 디바이스에 연결한다.
     *
     * @param deviceAddress Bluetooth MAC 주소 (예: "AA:BB:CC:DD:EE:FF"). [findDeviceAddress]로 이름→주소 변환 가능
     * @param transport     사용할 트랜스포트. 기본값은 [TransportType.BLE]
     */
    suspend fun connect(deviceAddress: String, transport: TransportType = TransportType.BLE) {
        activeTransport = when (transport) {
            TransportType.BLE        -> bleTransport
            TransportType.BT_CLASSIC -> btTransport
        }
        activeTransport.connect(deviceAddress)

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
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return null
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

}
