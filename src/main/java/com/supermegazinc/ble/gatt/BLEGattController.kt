package com.supermegazinc.ble.gatt

import android.bluetooth.BluetoothGatt
import com.supermegazinc.ble.gatt.characteristic.BLEGattCharacteristic
import com.supermegazinc.ble.gatt.service.BLEGattService
import com.supermegazinc.ble.gatt.model.BLEMessageEvent
import com.supermegazinc.ble.gatt.model.BLESessionConnectionEvent
import com.supermegazinc.ble.gatt.model.BLESessionServiceEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

interface BLEGattController {
    val connectionEvents: SharedFlow<BLESessionConnectionEvent>
    val serviceEvents: SharedFlow<BLESessionServiceEvent>
    val messageEvents: SharedFlow<BLEMessageEvent>
    val instance: StateFlow<BluetoothGatt?>
    val services: StateFlow<List<BLEGattService>>
    val characteristics: StateFlow<List<BLEGattCharacteristic>>
    fun readCharacteristic(uuid: UUID)
    fun discoverServices()
    fun requestMtu(mtu: Int)
    suspend fun startSession(): Boolean
    fun endSession()
    fun close()
}