package com.neurosky.sample

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
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
    private val simulator = SimulatorTransport()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestBluetoothPermissions()
        setupModeButtons()

        // Start simulator with Focused mode as default
        simulator.setMode(SimulatorTransport.Mode.FOCUSED)
        lifecycleScope.launch { simulator.connect("simulator") }
        lifecycleScope.launch {
            simulator.dataFlow.collect { data -> updateUI(data) }
        }

        highlightActiveMode(binding.btnFocused)
    }

    private fun setupModeButtons() {
        val modeMap = mapOf(
            binding.btnRandom    to SimulatorTransport.Mode.RANDOM,
            binding.btnFocused   to SimulatorTransport.Mode.FOCUSED,
            binding.btnRelaxed   to SimulatorTransport.Mode.RELAXED,
            binding.btnPoorSignal to SimulatorTransport.Mode.POOR_SIGNAL
        )
        modeMap.forEach { (btn, mode) ->
            btn.setOnClickListener {
                simulator.setMode(mode)
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
        binding.progressDelta.progress    = data.delta.coerceAtMost(100_000)
        binding.tvDelta.text              = "%,d".format(data.delta)
        binding.progressTheta.progress    = data.theta.coerceAtMost(100_000)
        binding.tvTheta.text              = "%,d".format(data.theta)
        binding.progressLowAlpha.progress = data.lowAlpha.coerceAtMost(100_000)
        binding.tvLowAlpha.text           = "%,d".format(data.lowAlpha)
        binding.progressHighAlpha.progress = data.highAlpha.coerceAtMost(100_000)
        binding.tvHighAlpha.text           = "%,d".format(data.highAlpha)
        binding.progressLowBeta.progress  = data.lowBeta.coerceAtMost(100_000)
        binding.tvLowBeta.text            = "%,d".format(data.lowBeta)
        binding.progressHighBeta.progress = data.highBeta.coerceAtMost(100_000)
        binding.tvHighBeta.text           = "%,d".format(data.highBeta)
        binding.progressLowGamma.progress = data.lowGamma.coerceAtMost(100_000)
        binding.tvLowGamma.text           = "%,d".format(data.lowGamma)
        binding.progressMidGamma.progress = data.midGamma.coerceAtMost(100_000)
        binding.tvMidGamma.text           = "%,d".format(data.midGamma)

        // Timestamp
        binding.tvUpdated.text = "Updated: ${timeFormat.format(Date(data.timestamp))}"
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
                1
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch { simulator.disconnect() }
    }
}
