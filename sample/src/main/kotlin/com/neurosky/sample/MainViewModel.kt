package com.neurosky.sample

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neurosky.sdk.model.BrainWaveData
import com.neurosky.sdk.simulator.SimulatorTransport
import com.neurosky.sdk.transport.ConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel : ViewModel() {

    companion object {
        const val MAX_ATTEMPTS = 5
        private const val MAX_DELAY_SECONDS = 30L
    }

    private val simulator = SimulatorTransport()

    /** 수집 시작 후 1초마다 BrainWaveData를 emit한다. */
    val brainWaveData: Flow<BrainWaveData> = simulator.dataFlow

    sealed class ReconnectState {
        object Idle : ReconnectState()
        object Connected : ReconnectState()
        /** @param attempt 현재 시도 횟수 (1-based), [delaySeconds] 후 connect() 호출 예정 */
        data class Reconnecting(val attempt: Int, val delaySeconds: Long) : ReconnectState()
        object Failed : ReconnectState()
        object Cancelled : ReconnectState()
    }

    private val _reconnectState = MutableStateFlow<ReconnectState>(ReconnectState.Idle)
    val reconnectState: StateFlow<ReconnectState> = _reconnectState.asStateFlow()

    private var reconnectJob: Job? = null

    /**
     * 백그라운드 전환 시 중단된 시도 번호.
     * 0이면 저장된 시도 없음 (포그라운드 복귀 시 아무 동작 안 함).
     */
    private var savedAttempt = 0

    /**
     * startSession() 호출 전 DISCONNECTED 방출을 무시하기 위한 플래그.
     * startSession() 이전에 stateFlow가 DISCONNECTED를 replay하므로 가드 필요.
     */
    private var sessionStarted = false

    init {
        viewModelScope.launch {
            simulator.stateFlow.collect { state ->
                if (!sessionStarted) return@collect
                when (state) {
                    ConnectionState.CONNECTED -> {
                        reconnectJob?.cancel()
                        savedAttempt = 0
                        _reconnectState.value = ReconnectState.Connected
                    }
                    ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                        val current = _reconnectState.value
                        if (reconnectJob?.isActive != true
                            && current !is ReconnectState.Reconnecting
                            && current !is ReconnectState.Cancelled
                            && current !is ReconnectState.Failed) {
                            launchReconnectLoop(fromAttempt = 1)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    /** 초기 연결 시작. 권한 승인 후 Activity가 호출한다. */
    fun startSession() {
        sessionStarted = true
        viewModelScope.launch {
            runCatching { simulator.connect("simulator") }
                .onFailure { e ->
                    if (e !is CancellationException) launchReconnectLoop(fromAttempt = 1)
                }
        }
    }

    /**
     * 지수 백오프 재연결 루프.
     *
     * 각 시도의 대기 시간: `min(2^(attempt-1), 30)` 초 → 1, 2, 4, 8, 16, 30 …
     * 성공 시 stateFlow → CONNECTED → observer가 job을 취소하고 상태를 업데이트한다.
     */
    private fun launchReconnectLoop(fromAttempt: Int) {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            for (attempt in fromAttempt..MAX_ATTEMPTS) {
                val delaySeconds = minOf(1L shl (attempt - 1), MAX_DELAY_SECONDS)
                _reconnectState.value = ReconnectState.Reconnecting(attempt, delaySeconds)
                delay(delaySeconds * 1_000L)
                if (!isActive) return@launch

                runCatching { simulator.connect("simulator") }
                // 성공: stateFlow → CONNECTED → init observer가 상태 갱신 & 이 job 취소
                // 실패: 다음 attempt로 진행
            }
            if (isActive) _reconnectState.value = ReconnectState.Failed
        }
    }

    /** 사용자가 재연결 중단 버튼을 누를 때 호출. */
    fun cancelReconnect() {
        reconnectJob?.cancel()
        savedAttempt = 0
        sessionStarted = false
        _reconnectState.value = ReconnectState.Cancelled
    }

    /**
     * Activity.onStart() 에서 호출.
     * 백그라운드에서 중단된 재연결이 있으면 이어서 재시도한다.
     */
    fun onForeground() {
        val attempt = savedAttempt
        if (attempt > 0 && _reconnectState.value is ReconnectState.Reconnecting) {
            savedAttempt = 0
            launchReconnectLoop(fromAttempt = attempt)
        }
    }

    /**
     * Activity.onStop() 에서 호출.
     * 진행 중인 재연결 job을 취소하고 현재 시도 번호를 저장한다.
     * 상태는 Reconnecting으로 유지해 포그라운드 복귀 시 이어받는다.
     */
    fun onBackground() {
        val state = _reconnectState.value
        if (state is ReconnectState.Reconnecting) {
            savedAttempt = state.attempt
        }
        reconnectJob?.cancel()
    }

    fun setSimulatorMode(mode: SimulatorTransport.Mode) {
        simulator.setMode(mode)
    }

    // ── CSV Recording ─────────────────────────────────────────────

    sealed class RecordingState {
        object Idle : RecordingState()
        data class Recording(val filePath: String, val rowCount: Int) : RecordingState()
        data class Saved(val filePath: String) : RecordingState()
    }

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private var recordingJob: Job? = null
    private var csvRecorder: CsvRecorder? = null

    fun startRecording() {
        if (_recordingState.value is RecordingState.Recording) return
        val fileName = "neurosky_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        dir.mkdirs()
        val file = File(dir, fileName)
        val recorder = CsvRecorder(file)
        csvRecorder = recorder
        _recordingState.value = RecordingState.Recording(file.absolutePath, 0)
        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            var rowCount = 0
            brainWaveData.collect { data ->
                recorder.appendRow(data)
                rowCount++
                _recordingState.value = RecordingState.Recording(file.absolutePath, rowCount)
            }
        }
    }

    fun stopRecording() {
        val filePath = (_recordingState.value as? RecordingState.Recording)?.filePath
        recordingJob?.cancel()
        recordingJob = null
        csvRecorder?.close()
        csvRecorder = null
        _recordingState.value = if (filePath != null) RecordingState.Saved(filePath) else RecordingState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        csvRecorder?.close()
    }
}
