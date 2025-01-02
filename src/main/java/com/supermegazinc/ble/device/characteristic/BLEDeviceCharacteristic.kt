package com.supermegazinc.ble.device.characteristic

import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

interface BLEDeviceCharacteristic {
    val uuid: UUID
    val message: StateFlow<ByteArray?>
    fun setNotification(state: Boolean)
    fun forceRead()
    suspend fun send(message: ByteArray)
    fun close()
}