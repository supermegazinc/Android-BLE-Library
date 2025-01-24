package com.supermegazinc.ble.device

import android.annotation.SuppressLint
import com.supermegazinc.ble.adapter.BLEAdapter
import com.supermegazinc.ble.adapter.model.BLEAdapterState
import com.supermegazinc.ble.gatt.characteristic.BLEGattCharacteristic
import com.supermegazinc.ble.device.model.BLEDeviceStatus
import com.supermegazinc.ble.gatt.service.BLEGattService
import com.supermegazinc.ble.gatt.BLEGattController
import com.supermegazinc.ble.gatt.model.BLEDisconnectionReason
import com.supermegazinc.ble.gatt.model.BLESessionConnectionEvent
import com.supermegazinc.escentials.Status
import com.supermegazinc.logger.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

@SuppressLint("MissingPermission")
class BLEDeviceImpl(
    override val name: String?,
    override val mac: String,
    override val mtu: Int,
    gattControllerFactory: (scope: CoroutineScope) -> BLEGattController,
    private val logger: Logger,
    private val adapter: BLEAdapter,
    coroutineContext: CoroutineContext
) : BLEDevice {

    companion object {
        const val LOG_KEY = "BLE-DEVICE"
    }

    private val deviceJob = Job()
    private val deviceCoroutineScope = CoroutineScope(coroutineContext + deviceJob)

    private val bleGattController = gattControllerFactory(deviceCoroutineScope)

    private val _status = MutableStateFlow<BLEDeviceStatus>(BLEDeviceStatus.Disconnected(BLEDisconnectionReason.DISCONNECTED))
    override val status: StateFlow<BLEDeviceStatus>
        get() = _status.asStateFlow()

    override val services: StateFlow<List<BLEGattService>>
        get() = bleGattController.services

    override val characteristics: StateFlow<List<BLEGattCharacteristic>>
        get() = bleGattController.characteristics

    private var connectJob: Job? = null
    override suspend fun connect(): Boolean {
        connectJob?.cancel()
        connectJob = Job()

        return try {
            withContext(deviceCoroutineScope.coroutineContext + connectJob!!) {
                run {
                    logger.d(LOG_KEY, "Conectando dispositivo..")

                    bleGattController.endSession()
                    _status.update { BLEDeviceStatus.Connecting }

                    val adapterState = adapter.state.value
                    if (adapterState !is Status.Ready || adapterState.data != BLEAdapterState.ON) {
                        logger.e(LOG_KEY, "El adaptador no esta disponible")
                        return@run false
                    }

                    if (!bleGattController.startSession()) {
                        logger.e(LOG_KEY, "No se pudo iniciar la sesion GATT")
                        return@run false
                    }

                    return@run true
                }.let { result->
                    if(!result) {
                        logger.e(LOG_KEY, "No se pudo conectar el dispositivo")
                        bleGattController.endSession()
                        _status.update { BLEDeviceStatus.Disconnected(BLEDisconnectionReason.CANT_CONNECT) }
                    } else {
                        delay(500)
                        bleGattController.requestMtu(mtu)
                        bleGattController.discoverServices()
                        delay(500)
                        logger.d(LOG_KEY, "Dispositivo conectado")
                        _status.update { BLEDeviceStatus.Connected }
                    }
                    result
                }
            }
        } catch (e: CancellationException) {
            logger.e(LOG_KEY, "Conexion cancelada")
            false
        }

    }

    private fun onConnectionLost() {
        connectJob?.cancel()
        bleGattController.endSession()
        _status.update { BLEDeviceStatus.Disconnected(BLEDisconnectionReason.CONNECTION_LOST) }
    }

    override suspend fun disconnect() {
        logger.d(LOG_KEY, "Desconectando dispositivo..")
        connectJob?.cancel()
        bleGattController.endSession()
        _status.update { BLEDeviceStatus.Disconnected(BLEDisconnectionReason.DISCONNECTED) }
        logger.d(LOG_KEY, "Dispositivo desconectado")
    }

    override fun discoverServices() {
        logger.d(LOG_KEY, "Descubriendo servicios")
        bleGattController.discoverServices()
    }

    override fun close() {
        logger.d(LOG_KEY, "Cerrando dispositivo..")
        connectJob?.cancel()
        bleGattController.close()
        observeJob.cancel()
        deviceCoroutineScope.cancel()
        _status.update { BLEDeviceStatus.Disconnected(BLEDisconnectionReason.DISCONNECTED) }
        logger.d(LOG_KEY, "Dispositivo cerrado..")
    }

    private val observeJob: Job
    init {
        observeJob = deviceCoroutineScope.launch {
            launch { observeConnectionEvents() }
            launch { observeAdapter() }
        }
    }

    private suspend fun observeConnectionEvents() {
        bleGattController.connectionEvents
            .collect { event ->
                when (event) {
                    BLESessionConnectionEvent.CONNECTED -> {}
                    BLESessionConnectionEvent.CONNECTION_LOST -> {
                        logger.e(LOG_KEY, "Se perdio la conexion con el dispositivo")
                        onConnectionLost()
                    }
                }
            }
    }

    private suspend fun observeAdapter() {
        _status.collectLatest { tStatus ->
            if (tStatus !is BLEDeviceStatus.Disconnected) {
                adapter.state
                    .filterIsInstance<Status.Ready<BLEAdapterState>>()
                    .filter { it.data != BLEAdapterState.ON }
                    .collect {
                        logger.e(LOG_KEY, "Adaptador desconectado")
                        onConnectionLost()
                    }
            }
        }
    }
}