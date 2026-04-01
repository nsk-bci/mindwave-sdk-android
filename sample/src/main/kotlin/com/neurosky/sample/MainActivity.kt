package com.neurosky.sample

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.neurosky.sdk.NeuroSkySdk
import com.neurosky.sdk.NeuroSkyCommand
import com.neurosky.sdk.simulator.SimulatorTransport
import com.neurosky.sdk.transport.ConnectionState
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // 실기기 테스트: NeuroSkySdk(this)
    // 시뮬레이터 테스트: SimulatorTransport 직접 사용
    private val sdk = NeuroSkySdk(this)
    private val simulator = SimulatorTransport()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestBluetoothPermissions()

        val tvStatus    = findViewById<TextView>(R.id.tvStatus)
        val tvAttention = findViewById<TextView>(R.id.tvAttention)
        val tvMeditation= findViewById<TextView>(R.id.tvMeditation)
        val tvSignal    = findViewById<TextView>(R.id.tvSignal)

        // 시뮬레이터로 UI 검증
        simulator.setMode(SimulatorTransport.Mode.FOCUSED)
        lifecycleScope.launch {
            launch {
                simulator.connect("simulator")
            }
            simulator.dataFlow.collect { data ->
                runOnUiThread {
                    tvStatus.text     = "Signal: ${data.signalQuality}"
                    tvAttention.text  = "Attention: ${data.attention}"
                    tvMeditation.text = "Meditation: ${data.meditation}"
                    tvSignal.text     = "PoorSignal: ${data.poorSignal}"
                }
            }
        }

        // 실기기 연결 예시 (주석 해제 후 사용)
        /*
        lifecycleScope.launch {
            sdk.connectionState.collect { state ->
                runOnUiThread { tvStatus.text = "State: $state" }
                if (state == ConnectionState.CONNECTED) {
                    sdk.sendCommand(NeuroSkyCommand.NOTCH_60HZ)
                }
            }
        }
        lifecycleScope.launch {
            sdk.connect("MindWave Mobile")
        }
        lifecycleScope.launch {
            sdk.dataFlow.collect { data ->
                runOnUiThread {
                    tvAttention.text  = "Attention: ${data.attention}"
                    tvMeditation.text = "Meditation: ${data.meditation}"
                    tvSignal.text     = "Signal: ${data.signalQuality}"
                }
            }
        }
        */
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
        lifecycleScope.launch { sdk.disconnect() }
    }
}
