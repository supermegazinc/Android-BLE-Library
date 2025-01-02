package com.supermegazinc.ble.gatt

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import com.supermegazinc.ble.gatt.model.BLEGattConnectError
import com.supermegazinc.ble.gatt.model.BLEGattEvent
import com.supermegazinc.escentials.Result
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

interface BLEGattController {
    val events: SharedFlow<BLEGattEvent>
    val instance: StateFlow<BluetoothGatt?>
    val services: StateFlow<List<BluetoothGattService>>
    val characteristics: StateFlow<List<BluetoothGattCharacteristic>>
    fun readCharacteristic(uuid: UUID)
    fun discoverServices()
    suspend fun connect(): Result<Unit, BLEGattConnectError>
    fun disconnect()
}