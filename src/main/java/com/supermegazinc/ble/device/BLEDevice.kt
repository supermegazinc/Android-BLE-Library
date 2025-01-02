package com.supermegazinc.ble.device

import com.supermegazinc.ble.device.characteristic.BLEDeviceCharacteristic
import com.supermegazinc.ble.device.model.BLEDeviceStatus
import com.supermegazinc.ble.device.service.BLEDeviceService
import com.supermegazinc.ble.gatt.model.BLEGattConnectError
import com.supermegazinc.escentials.Result
import kotlinx.coroutines.flow.StateFlow

interface BLEDevice {
    val name: String?
    val mac: String
    val status: StateFlow<BLEDeviceStatus>
    val services: StateFlow<List<BLEDeviceService>>
    val characteristics: StateFlow<List<BLEDeviceCharacteristic>>
    suspend fun connect(): Result<Unit, BLEGattConnectError>
    suspend fun disconnect()
    fun close()
}