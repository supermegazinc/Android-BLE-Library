package com.supermegazinc.ble.scanner.mapper

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import com.supermegazinc.ble.scanner.model.BLEScannedDevice

@SuppressLint("MissingPermission")
fun BluetoothDevice.toBLEScannedDevice(): BLEScannedDevice {
    return BLEScannedDevice(
        name = this.name,
        mac = this.address
    )
}