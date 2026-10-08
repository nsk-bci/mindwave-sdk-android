package com.neurosky.sdk.model

/**
 * 눈 깜빡임 1회. [com.neurosky.sdk.NeuroSkySdk.blinkFlow]로 검출될 때마다 방출된다.
 *
 * @property timestampMs 검출 시각 (Unix epoch ms)
 * @property strength    검출 윈도우의 raw EEG peak-to-peak 진폭 (raw EEG 단위, 임계값 이상)
 * @property sequence    연결 이후 누적 깜빡임 횟수 (1부터 시작, 재연결 시 초기화)
 */
data class BlinkEvent(
    val timestampMs: Long,
    val strength: Int,
    val sequence: Int,
)
