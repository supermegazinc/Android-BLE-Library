package com.supermegazinc.ble

import android.content.Context
import com.supermegazinc.ble.adapter.BLEAdapterImpl
import com.supermegazinc.ble.adapter.model.BLEAdapterState
import com.supermegazinc.ble.device.BLEDevice
import com.supermegazinc.ble.scanner.BLEScanner
import com.supermegazinc.ble.scanner.BLEScannerImpl
import com.supermegazinc.escentials.Status
import com.supermegazinc.logger.Logger
import com.supermegazinc.logger.LoggerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BLEControllerImpl(
    context: Context,
    logger: Logger = LoggerImpl(),
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : BLEController {

    override val adapter: BLEAdapterImpl by lazy {
        BLEAdapterImpl(
            context,
            logger,
            coroutineScope
        )
    }

    private val _device = MutableStateFlow<BLEDevice?>(null)
    override val device: StateFlow<BLEDevice?>
        get() = _device.asStateFlow()

    override fun setDevice(mac: String, mtu: Int): BLEDevice?  {
        clearDevice()
        return adapter
            .getDevice(mac, mtu)
            .also { newDevice ->
                _device.update {
                    it?.close()
                    newDevice
                }
            }
    }

    override fun clearDevice() {
        _device.value?.close()
        _device.update { null }
    }

    override val scanner: BLEScanner by lazy {
        BLEScannerImpl(logger, adapter)
    }

}