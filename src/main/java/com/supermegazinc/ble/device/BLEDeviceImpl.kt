package com.supermegazinc.ble.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.supermegazinc.ble.adapter.BLEAdapter
import com.supermegazinc.ble.adapter.model.BLEAdapterState
import com.supermegazinc.ble.device.characteristic.BLEDeviceCharacteristic
import com.supermegazinc.ble.device.model.BLEDeviceStatus
import com.supermegazinc.ble.device.service.BLEDeviceService
import com.supermegazinc.ble.gatt.BLEGattController
import com.supermegazinc.ble.gatt.BLEGattControllerImpl
import com.supermegazinc.ble.gatt.model.BLEDisconnectionReason
import com.supermegazinc.ble.gatt.model.BLEGattConnectError
import com.supermegazinc.ble.gatt.model.BLEGattEvent
import com.supermegazinc.ble.gatt.model.BLESessionConnectionEvent
import com.supermegazinc.ble.mappers.toBLEDeviceCharacteristic
import com.supermegazinc.ble.mappers.toBLEDeviceService
import com.supermegazinc.escentials.Result
import com.supermegazinc.escentials.Status
import com.supermegazinc.logger.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

@SuppressLint("MissingPermission")
class BLEDeviceImpl(
    private val device: BluetoothDevice,
    mtu: Int,
    context: Context,
    private val logger: Logger,
    private val adapter: BLEAdapter,
    private val coroutineScope: CoroutineScope
) : BLEDevice {

    companion object {
        const val LOG_KEY = "BLE-DEVICE"
    }

    private val bleGattController: BLEGattController = BLEGattControllerImpl(
        device = device,
        context = context,
        mtu = mtu,
        logger = logger,
        coroutineScope = coroutineScope
    )

    private val _status = MutableStateFlow<BLEDeviceStatus>(BLEDeviceStatus.Disconnected(BLEDisconnectionReason.DISCONNECTED))

    override val name: String?
        get() = device.name

    override val mac: String
        get() = device.address!!

    override val status: StateFlow<BLEDeviceStatus>
        get() = _status.asStateFlow()

    private val _services = MutableStateFlow<List<BLEDeviceService>>(emptyList())
    override val services: StateFlow<List<BLEDeviceService>>
        get() = _services.asStateFlow()

    private val _characteristics = MutableStateFlow<List<BLEDeviceCharacteristic>>(emptyList())
    override val characteristics: StateFlow<List<BLEDeviceCharacteristic>>
        get() = _characteristics.asStateFlow()

    private var connectJob: Job? = null
    override suspend fun connect(): Result<Unit, BLEGattConnectError> {
        connectJob?.cancel()
        connectJob = Job(coroutineScope.coroutineContext.job)

        return try {
            withContext(coroutineScope.coroutineContext + connectJob!!) {

                logger.i(LOG_KEY, "Conectando dispositivo..")

                val adapterState = adapter.state.value
                if (adapterState !is Status.Ready || adapterState.data != BLEAdapterState.ON) return@withContext Result.Fail<Unit, BLEGattConnectError>(
                    BLEGattConnectError.CANT_CONNECT
                )

                bleGattController.connect().also { result ->
                    if (result is Result.Fail) {
                        logger.e(LOG_KEY, "No se pudo conectar")
                        val error = when (result.error) {
                            BLEGattConnectError.CANT_CONNECT -> BLEDisconnectionReason.CANT_CONNECT
                            BLEGattConnectError.TIMEOUT -> BLEDisconnectionReason.TIMEOUT
                            BLEGattConnectError.CANCELED -> BLEDisconnectionReason.CANT_CONNECT
                        }
                        _status.update { BLEDeviceStatus.Disconnected(error) }
                        Result.Fail<Unit, BLEGattConnectError>(result.error)
                    } else {
                        logger.i(LOG_KEY, "Conectado")
                        bleGattController.discoverServices()
                        _status.update { BLEDeviceStatus.Connected }
                        Result.Success<Unit, BLEGattConnectError>(Unit)
                    }
                }
            }
        } catch (e: CancellationException) {
            logger.e(LOG_KEY, "Conexion cancelada")
            return Result.Fail(BLEGattConnectError.CANCELED)
        }
    }

    private fun clear() {
        connectJob?.cancel()
        bleGattController.disconnect()
        _characteristics.update { previousCharacteristics ->
            previousCharacteristics.forEach { it.close() }
            emptyList()
        }
        _services.update { emptyList() }
    }

    override suspend fun disconnect() {
        logger.i(LOG_KEY, "Desconectando dispositivo..")
        clear()
        _status.update {
            BLEDeviceStatus.Disconnected(BLEDisconnectionReason.DISCONNECTED)
        }
        logger.i(LOG_KEY, "Desconectado")
    }

    override fun close() {
        clear()
        job.cancel()
    }

    private var job: Job
    init {
        job = coroutineScope.launch {
            coroutineScope {
                launch { observeServices() }
                launch { observeCharacteristics() }
                launch { observeConnectionEvents() }
                launch { observeAdapter() }
            }
        }
    }

    private suspend fun observeServices() {
        bleGattController.services
            .map { services -> services.map { it.toBLEDeviceService() } }
            .collect { mappedServices->
                logger.i(LOG_KEY, "Servicios actualizados")
                _services.update {
                    mappedServices
                }
            }
    }

    private suspend fun observeCharacteristics() {
        bleGattController.characteristics
            .map { characteristics -> characteristics.map { it.toBLEDeviceCharacteristic(bleGattController, logger, coroutineScope) } }
            .collect { mappedCharacteristics->
                logger.i(LOG_KEY, "Caracteristicas actualizadas")
                _characteristics.update { previousCharacteristics ->
                    previousCharacteristics.forEach { it.close() }
                    mappedCharacteristics
                }
            }
    }

    private suspend fun observeConnectionEvents() {
        bleGattController.events
            .filterIsInstance<BLEGattEvent.Connection>()
            .collectLatest { event ->
                _status.update {
                    when (val tEvent = event.event) {
                        BLESessionConnectionEvent.CONNECTED -> {
                            logger.i(LOG_KEY, tEvent.toString())
                            BLEDeviceStatus.Connected
                        }
                        BLESessionConnectionEvent.CONNECTION_LOST -> {
                            logger.e(LOG_KEY, tEvent.toString())
                            clear()
                            BLEDeviceStatus.Disconnected(BLEDisconnectionReason.CONNECTION_LOST)
                        }
                    }
                }
            }
    }

    private suspend fun observeAdapter() {
        coroutineScope {
            _status.collectLatest { tStatus ->
                if (tStatus !is BLEDeviceStatus.Disconnected) {
                    adapter.state
                        .filterIsInstance<Status.Ready<BLEAdapterState>>()
                        .filter { it.data != BLEAdapterState.ON }
                        .collect {
                            logger.e(LOG_KEY, "Adaptador desconectado")
                            clear()
                            _status.update { BLEDeviceStatus.Disconnected(BLEDisconnectionReason.CONNECTION_LOST) }
                        }
                }
            }
        }
    }
}