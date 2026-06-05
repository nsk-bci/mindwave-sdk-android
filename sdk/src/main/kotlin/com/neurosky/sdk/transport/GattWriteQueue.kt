package com.neurosky.sdk.transport

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** GATT write가 GATT_SUCCESS 이외로 끝났음을 나타낸다. */
internal class GattWriteException(val status: Int) :
    Exception("GATT write failed (status=$status)")

/**
 * GATT write를 직렬화한다.
 *
 * Android 스택은 연결당 한 번에 단 하나의 ATT 연산만 허용한다. 이전 write의 콜백이 오기 전에
 * 다음 write를 쏘면 조용히 실패한다(`writeCharacteristic()` 반환이 false / != SUCCESS).
 * 이 큐는 각 write를
 *   - 차례가 올 때까지 대기시키고(Mutex — 논리적 write 1개씩),
 *   - 동기 제출 결과를 확인하고(busy면 재시도, 콜백이 오지 않으므로),
 *   - 대응하는 onCharacteristicWrite/onDescriptorWrite의 status까지 suspend로 기다리고,
 *   - GATT_SUCCESS만 성공으로 처리하며 제한적으로 재시도하고, 끝내 실패면 throw 한다.
 *
 * 배선: gattCallback이 결과를 이 큐로 전달해야 한다.
 *   onCharacteristicWrite(g, ch, status) -> onResult(status)
 *   onDescriptorWrite(g, dsc, status)    -> onResult(status)
 *
 * 한계: 타임아웃 후 늦게 도착한 콜백이 다음 write의 deferred를 조기 완료시킬 수 있다(드묾,
 * 3s 타임아웃 + 단일 in-flight 정책으로 영향 최소). 필요 시 op-id 태깅으로 강화 가능.
 */
internal class GattWriteQueue(private val log: (String) -> Unit = {}) {

    private val mutex = Mutex()
    @Volatile private var pending: CompletableDeferred<Unit>? = null

    /** gattCallback.onCharacteristicWrite / onDescriptorWrite에서 호출. */
    fun onResult(status: Int) {
        val d = pending ?: return
        pending = null
        if (status == BluetoothGatt.GATT_SUCCESS) d.complete(Unit)
        else d.completeExceptionally(GattWriteException(status))
    }

    /** 진행 중 write 대기를 해제(연결 종료 등)해 awaiter가 멈추지 않게 한다. */
    fun cancelAll(cause: Throwable = IllegalStateException("gatt disconnected")) {
        pending?.completeExceptionally(cause)
        pending = null
    }

    suspend fun write(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        maxRetries: Int = DEFAULT_MAX_RETRIES,
    ) = runSerialized(timeoutMs, maxRetries) {
        submitCharacteristic(gatt, characteristic, value, writeType)
    }

    suspend fun write(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        maxRetries: Int = DEFAULT_MAX_RETRIES,
    ) = runSerialized(timeoutMs, maxRetries) {
        submitDescriptor(gatt, descriptor, value)
    }

    private suspend fun runSerialized(
        timeoutMs: Long,
        maxRetries: Int,
        submit: () -> Boolean,
    ) = mutex.withLock {
        var attempt = 0
        while (true) {
            attempt++
            val deferred = CompletableDeferred<Unit>()
            pending = deferred
            if (!submit()) {
                // 스택이 동기적으로 거부(busy) — 콜백이 오지 않으므로 await 하지 않고 재시도.
                pending = null
                if (attempt > maxRetries) throw GattWriteException(BUSY_STATUS)
                log("gatt submit rejected, retry $attempt/$maxRetries")
                delay(RETRY_DELAY_MS)
                continue
            }
            try {
                withTimeout(timeoutMs) { deferred.await() } // onResult(status)가 깨움
                return@withLock
            } catch (t: Throwable) {
                pending = null
                if (attempt > maxRetries) throw t
                log("gatt write failed (${t.message}), retry $attempt/$maxRetries")
                delay(RETRY_DELAY_MS)
            }
        }
    }

    private fun submitCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, value, writeType) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                characteristic.writeType = writeType
                characteristic.value = value
                gatt.writeCharacteristic(characteristic)
            }
        }

    private fun submitDescriptor(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                descriptor.value = value
                gatt.writeDescriptor(descriptor)
            }
        }

    companion object {
        private const val BUSY_STATUS = -1
        private const val DEFAULT_TIMEOUT_MS = 3_000L
        private const val DEFAULT_MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 60L
    }
}
