package com.supermegazinc.ble.gatt.model

import android.bluetooth.BluetoothGattCharacteristic
import java.util.UUID

data class BLEMessageEvent(
    val characteristicUUID: UUID,
    val message: ByteArray
)
