package com.supermegazinc.ble.gatt.service

import android.bluetooth.BluetoothGattService
import com.supermegazinc.ble.gatt.service.BLEGattService
import java.util.UUID

class BLEGattServiceImpl(
    private val service: BluetoothGattService,
) : BLEGattService {
    override val uuid: UUID
        get() = service.uuid
}