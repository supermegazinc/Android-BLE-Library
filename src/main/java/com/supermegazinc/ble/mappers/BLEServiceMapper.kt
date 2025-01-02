package com.supermegazinc.ble.mappers

import android.bluetooth.BluetoothGattService
import com.supermegazinc.ble.device.service.BLEDeviceService
import com.supermegazinc.ble.device.service.BLEDeviceServiceImpl

fun BluetoothGattService.toBLEDeviceService(): BLEDeviceService {
    return BLEDeviceServiceImpl(
        service = this
    )
}