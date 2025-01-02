package com.supermegazinc.ble

import com.supermegazinc.ble.adapter.BLEAdapter
import com.supermegazinc.ble.device.BLEDevice
import com.supermegazinc.ble.scanner.BLEScanner
import kotlinx.coroutines.flow.StateFlow

interface BLEController {

    val adapter: BLEAdapter

    val device: StateFlow<BLEDevice?>
    fun setDevice(mac: String, mtu: Int): BLEDevice?
    fun clearDevice()

    val scanner: BLEScanner

}