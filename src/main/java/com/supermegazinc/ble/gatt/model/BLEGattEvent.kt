package com.supermegazinc.ble.gatt.model

sealed interface BLEGattEvent {
    data class Connection(val event: BLESessionConnectionEvent): BLEGattEvent
    data class Service(val event: BLESessionServiceEvent): BLEGattEvent
    data class Message(val event: BLEMessageEvent): BLEGattEvent
}