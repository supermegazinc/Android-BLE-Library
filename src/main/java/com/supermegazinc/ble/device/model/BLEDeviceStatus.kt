package com.supermegazinc.ble.device.model

import com.supermegazinc.ble.gatt.model.BLEDisconnectionReason

sealed interface BLEDeviceStatus {
    data object Connected: BLEDeviceStatus
    data class Disconnected(val reason: BLEDisconnectionReason): BLEDeviceStatus
    data object Connecting: BLEDeviceStatus
}