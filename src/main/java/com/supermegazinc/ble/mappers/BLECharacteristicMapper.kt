package com.supermegazinc.ble.mappers

import android.bluetooth.BluetoothGattCharacteristic
import com.supermegazinc.ble.device.characteristic.BLEDeviceCharacteristic
import com.supermegazinc.ble.device.characteristic.BLEDeviceCharacteristicImpl
import com.supermegazinc.ble.gatt.BLEGattController
import com.supermegazinc.logger.Logger
import kotlinx.coroutines.CoroutineScope

fun BluetoothGattCharacteristic.toBLEDeviceCharacteristic(
    bleGattController: BLEGattController,
    logger: Logger,
    coroutineScope: CoroutineScope
): BLEDeviceCharacteristic {
    return BLEDeviceCharacteristicImpl(
        bleGattController = bleGattController,
        characteristic = this,
        logger = logger,
        coroutineScope = coroutineScope
    )
}