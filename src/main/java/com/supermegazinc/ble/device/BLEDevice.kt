package com.supermegazinc.ble.device

import com.supermegazinc.ble.gatt.characteristic.BLEGattCharacteristic
import com.supermegazinc.ble.device.model.BLEDeviceStatus
import com.supermegazinc.ble.gatt.service.BLEGattService
import kotlinx.coroutines.flow.StateFlow

interface BLEDevice {
    val name: String?
    val mac: String
    val mtu: Int
    val status: StateFlow<BLEDeviceStatus>
    val services: StateFlow<List<BLEGattService>>
    val characteristics: StateFlow<List<BLEGattCharacteristic>>
    suspend fun connect(): Boolean
    suspend fun disconnect()
    fun discoverServices()
    fun close()
}