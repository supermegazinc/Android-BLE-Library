package com.supermegazinc.ble.adapter

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import com.supermegazinc.ble.adapter.model.BLEAdapterState
import com.supermegazinc.ble.device.BLEDevice
import com.supermegazinc.ble.device.BLEDeviceImpl
import com.supermegazinc.ble.scanner.BLEScannerImpl
import com.supermegazinc.ble.scanner.BLEScannerImpl.Companion
import com.supermegazinc.escentials.Status
import com.supermegazinc.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BLEAdapterImpl(
    private val context: Context,
    private val logger: Logger,
    private val coroutineScope: CoroutineScope
) : BLEAdapter {

    companion object {
        const val LOG_KEY = "BLE-ADAPTER"
    }

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val _state = MutableStateFlow<Status<BLEAdapterState>>(Status.Loading())
    override val state: StateFlow<Status<BLEAdapterState>>
        get() = _state.asStateFlow()

    override fun bluetoothLauncher(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>) {
        logger.i(LOG_KEY, "Solicitando encender Bluetooth")
        launcher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    override fun onBluetoothLauncherResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) logger.i(LOG_KEY, "OK")
        else {
            logger.e(LOG_KEY, "ERROR")
        }
    }

    override fun getDevice(address: String, mtu: Int): BLEDevice? {
        logger.d(LOG_KEY, "Obteniendo dispositivo..")
        return if(adapter==null) {
            logger.e(LOG_KEY, "ERROR: El adaptador no esta disponible")
            null
        }
        else {
            val device = adapter!!.getRemoteDevice(address)
            if (device == null) {
                logger.e(LOG_KEY, "ERROR: No se encontro el dispositivo")
                null
            } else {
                BLEDeviceImpl(
                    device = device,
                    mtu = mtu,
                    context = context,
                    adapter = this,
                    coroutineScope = coroutineScope,
                    logger = logger
                )
            }
        }
    }

    init {
        coroutineScope.launch {
            while(isActive) {
                adapter?.isEnabled.let {
                    when(it) {
                        true -> BLEAdapterState.ON
                        false -> BLEAdapterState.OFF
                        null -> BLEAdapterState.UNAVAILABLE
                    }
                }.also { tState->
                    _state.value.also { tActualState->
                        if(tActualState !is Status.Ready || tActualState.data != tState) {
                            when(tState) {
                                BLEAdapterState.ON -> logger.i(LOG_KEY, "Adaptador encendido")
                                BLEAdapterState.OFF -> logger.e(LOG_KEY, "Adaptador apagado")
                                BLEAdapterState.UNAVAILABLE -> logger.e(LOG_KEY, "Adaptador no disponible")
                            }
                            _state.update {Status.Ready(tState)}
                        }
                    }
                }
            }
        }
    }

}