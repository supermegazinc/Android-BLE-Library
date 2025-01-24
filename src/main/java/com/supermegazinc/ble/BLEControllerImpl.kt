package com.supermegazinc.ble

import com.supermegazinc.ble.adapter.BLEAdapter
import com.supermegazinc.ble.device.BLEDevice
import com.supermegazinc.ble.scanner.BLEScanner
import com.supermegazinc.logger.Logger
import com.supermegazinc.logger.LoggerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.CoroutineContext

class BLEControllerImpl(
    bleAdapterFactory: (scope: CoroutineScope) -> BLEAdapter,
    bleScannerFactory: (adapter: BLEAdapter) -> BLEScanner,
    logger: Logger = LoggerImpl(),
    coroutineContext: CoroutineContext
) : BLEController {

    private companion object {
        const val LOG_KEY = "BLE"
    }

    private val bleJob = Job().apply {
        invokeOnCompletion {
            logger.e(LOG_KEY, "[CRITIC] - Scope cancelado")
        }
    }
    private val bleScope = CoroutineScope(coroutineContext + bleJob)

    override val adapter = bleAdapterFactory(bleScope)

    private val _device = MutableStateFlow<BLEDevice?>(null)
    override val device: StateFlow<BLEDevice?>
        get() = _device.asStateFlow()

    override fun setDevice(mac: String, mtu: Int): BLEDevice?  {
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

    override val scanner: BLEScanner = bleScannerFactory(adapter)

}