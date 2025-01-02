package com.supermegazinc.ble.gatt.model

import android.bluetooth.BluetoothGattCharacteristic

sealed interface BLEGattEvent {
    data class Connection(val event: BLESessionConnectionEvent): BLEGattEvent
    data class Service(val event: BLESessionServiceEvent): BLEGattEvent
    data class Message(val characteristic: BluetoothGattCharacteristic, val message: ByteArray): BLEGattEvent
}