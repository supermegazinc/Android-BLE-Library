package com.supermegazinc.ble.device.service

import android.bluetooth.BluetoothGattService
import java.util.UUID

class BLEDeviceServiceImpl(
    private val service: BluetoothGattService,
) : BLEDeviceService {
    override val uuid: UUID
        get() = service.uuid
}