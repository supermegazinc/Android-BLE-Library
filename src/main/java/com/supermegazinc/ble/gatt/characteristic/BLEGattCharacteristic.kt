package com.supermegazinc.ble.gatt.characteristic

import kotlinx.coroutines.channels.ReceiveChannel
import java.util.UUID

interface BLEGattCharacteristic {
    val uuid: UUID
    val message: ReceiveChannel<ByteArray?>
    fun setNotification(state: Boolean)
    fun forceRead()
    suspend fun send(message: ByteArray)
    fun close()
}