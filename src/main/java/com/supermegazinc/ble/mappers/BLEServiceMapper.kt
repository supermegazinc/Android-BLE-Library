package com.supermegazinc.ble.mappers

import android.bluetooth.BluetoothGattService
import com.supermegazinc.ble.gatt.service.BLEGattService
import com.supermegazinc.ble.gatt.service.BLEGattServiceImpl

fun BluetoothGattService.toBLEDeviceService(): BLEGattService {
    return BLEGattServiceImpl(
        service = this
    )
}