package com.supermegazinc.ble.mappers

import android.bluetooth.BluetoothGattCharacteristic
import com.supermegazinc.ble.gatt.characteristic.BLEGattCharacteristic
import com.supermegazinc.ble.gatt.characteristic.BLEGattCharacteristicImpl
import com.supermegazinc.ble.gatt.BLEGattController
import com.supermegazinc.logger.Logger
import kotlinx.coroutines.CoroutineScope

fun BluetoothGattCharacteristic.toBLEDeviceCharacteristic(
    bleGattController: BLEGattController,
    logger: Logger,
    coroutineScope: CoroutineScope
): BLEGattCharacteristic {
    return BLEGattCharacteristicImpl(
        bleGattController = bleGattController,
        characteristic = this,
        logger = logger,
        coroutineScope = coroutineScope
    )
}