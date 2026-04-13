package com.neurosky.sdk.simulator

import com.neurosky.sdk.transport.ConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatorTransportTest {

    // ── Mode: FOCUSED ────────────────────────────────────────────────────────

    /**
     * FOCUSED 모드: attention 70~100, meditation 40~60
     * poorSignal = 0 (완전 클린 신호)
     */
    @Test
    fun focused_dataRanges_attentionMeditationPoorSignal() = runTest {
        val simulator = SimulatorTransport()
        simulator.setMode(SimulatorTransport.Mode.FOCUSED)

        val data = simulator.dataFlow.first()

        assertTrue("attention=${data.attention} should be in 70..100", data.attention in 70..100)
        assertTrue("meditation=${data.meditation} should be in 40..60", data.meditation in 40..60)
        assertEquals("poorSignal should be 0 in FOCUSED mode", 0, data.poorSignal)
    }

    /** FOCUSED 모드: 10개 rawEeg 샘플이 생성된다. */
    @Test
    fun focused_dataFlow_rawEegHas10Samples() = runTest {
        val simulator = SimulatorTransport()
        simulator.setMode(SimulatorTransport.Mode.FOCUSED)

        val data = simulator.dataFlow.first()

        assertEquals(10, data.rawEeg.size)
    }

    // ── Mode: RELAXED ────────────────────────────────────────────────────────

    /**
     * RELAXED 모드: attention 20~50, meditation 70~100
     * poorSignal = 0 (완전 클린 신호)
     */
    @Test
    fun relaxed_dataRanges_attentionMeditationPoorSignal() = runTest {
        val simulator = SimulatorTransport()
        simulator.setMode(SimulatorTransport.Mode.RELAXED)

        val data = simulator.dataFlow.first()

        assertTrue("attention=${data.attention} should be in 20..50", data.attention in 20..50)
        assertTrue("meditation=${data.meditation} should be in 70..100", data.meditation in 70..100)
        assertEquals("poorSignal should be 0 in RELAXED mode", 0, data.poorSignal)
    }

    // ── Mode: POOR_SIGNAL ────────────────────────────────────────────────────

    /**
     * POOR_SIGNAL 모드: poorSignal 150~200, attention=0, meditation=0
     * eSense 값은 의미 없으므로 0이어야 한다.
     */
    @Test
    fun poorSignal_dataRanges_poorSignalAndEsense() = runTest {
        val simulator = SimulatorTransport()
        simulator.setMode(SimulatorTransport.Mode.POOR_SIGNAL)

        val data = simulator.dataFlow.first()

        assertTrue("poorSignal=${data.poorSignal} should be in 150..200", data.poorSignal in 150..200)
        assertEquals("attention should be 0 in POOR_SIGNAL mode", 0, data.attention)
        assertEquals("meditation should be 0 in POOR_SIGNAL mode", 0, data.meditation)
    }

    // ── Mode change ──────────────────────────────────────────────────────────

    /** setMode() 변경이 즉시 다음 emit에 반영된다. */
    @Test
    fun setMode_changeFromFocusedToRelaxed_nextEmitReflectsNewMode() = runTest {
        val simulator = SimulatorTransport()
        simulator.setMode(SimulatorTransport.Mode.FOCUSED)
        val focused = simulator.dataFlow.first()
        assertTrue(focused.attention in 70..100)

        simulator.setMode(SimulatorTransport.Mode.RELAXED)
        val relaxed = simulator.dataFlow.first()
        assertTrue(relaxed.attention in 20..50)
    }

    // ── Lifecycle: connect → dataFlow → disconnect ────────────────────────────

    /**
     * connect() 후 dataFlow 수집이 가능하고, disconnect() 이후
     * 상태가 DISCONNECTED로 돌아온다.
     */
    @Test
    fun connect_collectOneData_disconnect_lifecycle() = runTest {
        val simulator = SimulatorTransport()

        // 1. 초기 상태
        assertEquals(ConnectionState.DISCONNECTED, simulator.stateFlow.first())

        // 2. connect → CONNECTED
        simulator.connect("test-device")
        assertEquals(ConnectionState.CONNECTED, simulator.stateFlow.first())

        // 3. dataFlow에서 데이터 1건 수집
        val data = simulator.dataFlow.first()
        assertNotNull(data)
        assertTrue("timestamp must be positive", data.timestamp > 0)

        // 4. disconnect → DISCONNECTED
        simulator.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, simulator.stateFlow.first())
    }

    // ── ConnectionState 전환 순서 ─────────────────────────────────────────────

    /**
     * connect() 호출 시 DISCONNECTED → CONNECTING → CONNECTED 순서 검증.
     *
     * UnconfinedTestDispatcher를 사용해 stateFlow 값 변경이 collector에
     * 즉시(동기적으로) 전달되도록 한다.
     */
    @Test
    fun connect_stateTransitions_disconnectedConnectingConnected() = runTest {
        val simulator = SimulatorTransport()
        val states = mutableListOf<ConnectionState>()

        // backgroundScope: runTest 종료 시 자동 취소 — 명시적 cancel 불필요
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            simulator.stateFlow.collect { states.add(it) }
        }

        simulator.connect("test-device")

        assertEquals("should have exactly 3 state transitions", 3, states.size)
        assertEquals(ConnectionState.DISCONNECTED, states[0])
        assertEquals(ConnectionState.CONNECTING,   states[1])
        assertEquals(ConnectionState.CONNECTED,    states[2])
    }

    /** disconnect() 호출 시 CONNECTED → DISCONNECTED 전환 검증. */
    @Test
    fun disconnect_afterConnect_stateBecomesDisconnected() = runTest {
        val simulator = SimulatorTransport()
        val states = mutableListOf<ConnectionState>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            simulator.stateFlow.collect { states.add(it) }
        }

        simulator.connect("test-device")
        simulator.disconnect()

        // DISCONNECTED → CONNECTING → CONNECTED → DISCONNECTED
        assertEquals(4, states.size)
        assertEquals(ConnectionState.DISCONNECTED, states.last())
    }
}
