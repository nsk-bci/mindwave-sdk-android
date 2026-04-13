package com.neurosky.sample

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.neurosky.sample.MainViewModel.RecordingState
import com.neurosky.sample.MainViewModel.ReconnectState
import com.neurosky.sample.databinding.ActivityMainBinding
import com.neurosky.sdk.model.BrainWaveData
import com.neurosky.sdk.model.SignalQuality
import com.neurosky.sdk.simulator.SimulatorTransport
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.all { it }) {
                startConnection()
            } else {
                showPermissionDeniedDialog()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupModeButtons()
        viewModel.setSimulatorMode(SimulatorTransport.Mode.FOCUSED)
        highlightActiveMode(binding.btnFocused)

        binding.btnCancelReconnect.setOnClickListener { viewModel.cancelReconnect() }
        binding.btnStartRecording.setOnClickListener { viewModel.startRecording() }
        binding.btnStopRecording.setOnClickListener { viewModel.stopRecording() }

        observeReconnectState()
        observeRecordingState()

        checkAndRequestPermissions()
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onBackground()
    }

    // ── Permissions ───────────────────────────────────────────────

    private fun checkAndRequestPermissions() {
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) startConnection() else permissionLauncher.launch(requiredPermissions)
    }

    private fun startConnection() {
        viewModel.startSession()
        lifecycleScope.launch {
            viewModel.brainWaveData.collect { data -> updateUI(data) }
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Bluetooth 권한 필요")
            .setMessage(
                "MindWave 기기에 연결하려면 Bluetooth 권한이 필요합니다.\n" +
                "설정 → 앱 → 권한에서 직접 허용해주세요."
            )
            .setPositiveButton("확인", null)
            .show()
    }

    // ── ReconnectState observer ───────────────────────────────────

    private fun observeReconnectState() {
        lifecycleScope.launch {
            viewModel.reconnectState.collect { state ->
                when (state) {
                    ReconnectState.Idle,
                    ReconnectState.Connected -> hideBanner()

                    is ReconnectState.Reconnecting -> showReconnectingBanner(state)

                    ReconnectState.Failed -> showFailedBanner()

                    ReconnectState.Cancelled -> hideBanner()
                }
            }
        }
    }

    private fun hideBanner() {
        binding.layoutReconnect.visibility = View.GONE
    }

    private fun showReconnectingBanner(state: ReconnectState.Reconnecting) {
        binding.layoutReconnect.visibility = View.VISIBLE
        binding.layoutReconnect.setBackgroundColor(0xFFFFF3E0.toInt())  // amber 50
        binding.tvReconnectStatus.setTextColor(0xFFE65100.toInt())      // deep orange 900
        binding.tvReconnectStatus.text =
            "재연결 중... (${state.attempt}/${MainViewModel.MAX_ATTEMPTS}회) · ${state.delaySeconds}초 후 재시도"
        binding.btnCancelReconnect.visibility = View.VISIBLE
        binding.btnCancelReconnect.setTextColor(0xFFE65100.toInt())
    }

    private fun showFailedBanner() {
        binding.layoutReconnect.visibility = View.VISIBLE
        binding.layoutReconnect.setBackgroundColor(0xFFFFEBEE.toInt())  // red 50
        binding.tvReconnectStatus.setTextColor(0xFFB71C1C.toInt())      // red 900
        binding.tvReconnectStatus.text = "연결 실패 — 수동으로 다시 시도해주세요"
        binding.btnCancelReconnect.visibility = View.GONE
    }

    // ── RecordingState observer ───────────────────────────────────

    private fun observeRecordingState() {
        lifecycleScope.launch {
            viewModel.recordingState.collect { state ->
                when (state) {
                    RecordingState.Idle -> {
                        binding.btnStartRecording.isEnabled = true
                        binding.btnStopRecording.isEnabled = false
                        binding.tvRecordingStatus.text = "대기 중"
                        binding.tvRecordingStatus.setTextColor(0xFF888888.toInt())
                    }
                    is RecordingState.Recording -> {
                        binding.btnStartRecording.isEnabled = false
                        binding.btnStopRecording.isEnabled = true
                        binding.tvRecordingStatus.text = "기록 중 · ${state.rowCount}행"
                        binding.tvRecordingStatus.setTextColor(0xFF2E7D32.toInt())  // green 800
                    }
                    is RecordingState.Saved -> {
                        binding.btnStartRecording.isEnabled = true
                        binding.btnStopRecording.isEnabled = false
                        val name = state.filePath.substringAfterLast('/')
                        binding.tvRecordingStatus.text = "저장됨: $name"
                        binding.tvRecordingStatus.setTextColor(0xFF1565C0.toInt())  // blue 800
                    }
                }
            }
        }
    }

    // ── Simulator mode buttons ────────────────────────────────────

    private fun setupModeButtons() {
        val modeMap = mapOf(
            binding.btnRandom     to SimulatorTransport.Mode.RANDOM,
            binding.btnFocused    to SimulatorTransport.Mode.FOCUSED,
            binding.btnRelaxed    to SimulatorTransport.Mode.RELAXED,
            binding.btnPoorSignal to SimulatorTransport.Mode.POOR_SIGNAL
        )
        modeMap.forEach { (btn, mode) ->
            btn.setOnClickListener {
                viewModel.setSimulatorMode(mode)
                highlightActiveMode(btn)
            }
        }
    }

    private fun highlightActiveMode(active: MaterialButton) {
        val allButtons = listOf(
            binding.btnRandom, binding.btnFocused,
            binding.btnRelaxed, binding.btnPoorSignal
        )
        val primaryColor = getColor(R.color.colorPrimary)
        allButtons.forEach { btn ->
            if (btn == active) {
                btn.setBackgroundColor(primaryColor)
                btn.setTextColor(Color.WHITE)
                btn.strokeWidth = 0
            } else {
                btn.setBackgroundColor(Color.TRANSPARENT)
                btn.setTextColor(primaryColor)
                btn.strokeColor = android.content.res.ColorStateList.valueOf(primaryColor)
                btn.strokeWidth = 2
            }
        }
    }

    // ── UI update ─────────────────────────────────────────────────

    private fun updateUI(data: BrainWaveData) {
        // Signal quality
        val (dotColorRes, qualityText) = when (data.signalQuality) {
            SignalQuality.GOOD      -> Pair(R.color.signalGood, "GOOD")
            SignalQuality.FAIR      -> Pair(R.color.signalFair, "FAIR")
            SignalQuality.POOR      -> Pair(R.color.signalPoor, "POOR")
            SignalQuality.NO_SIGNAL -> Pair(R.color.signalNone, "NO SIGNAL")
        }
        val dotColor = getColor(dotColorRes)
        binding.viewDot.background.setTint(dotColor)
        binding.tvQuality.text = qualityText
        binding.tvQuality.setTextColor(dotColor)
        binding.tvPoorSignal.text = "Poor Signal: ${data.poorSignal}"

        // eSense
        binding.tvAttention.text = "${data.attention}"
        binding.progressAttention.progress = data.attention
        binding.tvMeditation.text = "${data.meditation}"
        binding.progressMeditation.progress = data.meditation

        // EEG bands (raw values up to ~100k)
        binding.progressDelta.progress     = data.delta.coerceAtMost(100_000)
        binding.tvDelta.text               = "%,d".format(data.delta)
        binding.progressTheta.progress     = data.theta.coerceAtMost(100_000)
        binding.tvTheta.text               = "%,d".format(data.theta)
        binding.progressLowAlpha.progress  = data.lowAlpha.coerceAtMost(100_000)
        binding.tvLowAlpha.text            = "%,d".format(data.lowAlpha)
        binding.progressHighAlpha.progress = data.highAlpha.coerceAtMost(100_000)
        binding.tvHighAlpha.text           = "%,d".format(data.highAlpha)
        binding.progressLowBeta.progress   = data.lowBeta.coerceAtMost(100_000)
        binding.tvLowBeta.text             = "%,d".format(data.lowBeta)
        binding.progressHighBeta.progress  = data.highBeta.coerceAtMost(100_000)
        binding.tvHighBeta.text            = "%,d".format(data.highBeta)
        binding.progressLowGamma.progress  = data.lowGamma.coerceAtMost(100_000)
        binding.tvLowGamma.text            = "%,d".format(data.lowGamma)
        binding.progressMidGamma.progress  = data.midGamma.coerceAtMost(100_000)
        binding.tvMidGamma.text            = "%,d".format(data.midGamma)

        // Timestamp
        binding.tvUpdated.text = "Updated: ${timeFormat.format(Date(data.timestamp))}"
    }
}
