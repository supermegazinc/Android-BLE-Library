package com.supermegazinc.ble.scanner

import com.supermegazinc.ble.scanner.model.BLEScannedDevice
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

interface BLEScanner {
    val scannedDevices: StateFlow<List<BLEScannedDevice>>
    fun start(serviceUUID: List<UUID>? = null)
    fun stop()
    fun clear()
}