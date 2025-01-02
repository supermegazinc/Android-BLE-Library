package com.supermegazinc.ble.adapter

import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import com.supermegazinc.ble.adapter.model.BLEAdapterState
import com.supermegazinc.ble.device.BLEDevice
import com.supermegazinc.escentials.Status
import kotlinx.coroutines.flow.StateFlow

interface BLEAdapter {
    val state: StateFlow<Status<BLEAdapterState>>
    fun bluetoothLauncher(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>)
    fun onBluetoothLauncherResult(result: ActivityResult)
    fun getDevice(address: String, mtu: Int): BLEDevice?
}