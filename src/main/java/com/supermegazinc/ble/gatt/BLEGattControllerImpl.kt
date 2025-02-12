package com.supermegazinc.ble.gatt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.supermegazinc.ble.gatt.characteristic.BLEGattCharacteristic
import com.supermegazinc.ble.gatt.service.BLEGattService
import com.supermegazinc.ble.gatt.model.BLEGattEvent
import com.supermegazinc.ble.gatt.model.BLEMessageEvent
import com.supermegazinc.ble.gatt.model.BLESessionConnectionEvent
import com.supermegazinc.ble.gatt.model.BLESessionServiceEvent
import com.supermegazinc.ble.mappers.toBLEDeviceCharacteristic
import com.supermegazinc.ble.mappers.toBLEDeviceService
import com.supermegazinc.escentials.waitForNextWithTimeout
import com.supermegazinc.logger.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@SuppressLint("MissingPermission")
class BLEGattControllerImpl(
    private val device: BluetoothDevice,
    private val context: Context,
    private val logger: Logger,
    private val coroutineScope: CoroutineScope
) : BLEGattController {

    companion object {
        const val LOG_KEY = "BLE-GATT"
    }

    private val _instance = MutableStateFlow<BluetoothGatt?>(null)
    override val instance: StateFlow<BluetoothGatt?>
        get() = _instance.asStateFlow()

    private val _services = MutableStateFlow<List<BLEGattService>>(emptyList())
    override val services: StateFlow<List<BLEGattService>>
        get() = _services.asStateFlow()

    private var gattCharacteristics: List<BluetoothGattCharacteristic> = emptyList()

    private val _characteristics = MutableStateFlow<List<BLEGattCharacteristic>>(emptyList())
    override val characteristics: StateFlow<List<BLEGattCharacteristic>>
        get() = _characteristics.asStateFlow()

    private val _connectionEvents = MutableSharedFlow<BLESessionConnectionEvent>()
    override val connectionEvents: SharedFlow<BLESessionConnectionEvent>
        get() = _connectionEvents.asSharedFlow()

    private val _messageEvents = MutableSharedFlow<BLEMessageEvent>(extraBufferCapacity = 1000)
    override val messageEvents: SharedFlow<BLEMessageEvent>
        get() = _messageEvents.asSharedFlow()

    private val _serviceEvents = MutableSharedFlow<BLESessionServiceEvent>()
    override val serviceEvents: SharedFlow<BLESessionServiceEvent>
        get() = _serviceEvents.asSharedFlow()

    private fun <T> SendChannel<T>.trySendWrapper(data: T): ChannelResult<Unit> {
        val sendResult = trySend(data)
        if(sendResult.isFailure) {
            logger.e(LOG_KEY, "[CRITIC] - No se pudo agregar el mensaje al canal")
        }
        return sendResult
    }

    private fun clearServicesAndCharacteristics() {
        logger.d(LOG_KEY, "Limpiando servicios y caracteristicas")
        _characteristics.update { previousCharacteristics ->
            previousCharacteristics.forEach { it.close() }
            emptyList()
        }
        _services.update { emptyList() }
    }

    private fun updateServices(newServices: List<BluetoothGattService>) {
        _services.update { currentServices->
            val newServicesUUIDs = newServices.map { it.uuid!! }.toSet()
            val currentServicesUUIDs = currentServices.map { it.uuid }.toSet()
            if(newServicesUUIDs == currentServicesUUIDs) return@update currentServices

            logger.d(LOG_KEY, "Servicios actualizados: ${newServicesUUIDs.toList()}")

            return@update newServices.map { it.toBLEDeviceService() }
        }
    }

    private fun updateCharacteristics(newServices: List<BluetoothGattService>) {
        val newCharacteristics = newServices.flatMap { it.characteristics }
        gattCharacteristics = newCharacteristics
        _characteristics.update { currentCharacteristics->
            val newCharacteristicsUUIDs = newCharacteristics.map { it.uuid!! }.toSet()
            val currentCharacteristicsUUIDs = currentCharacteristics.map { it.uuid }.toSet()
            if(newCharacteristicsUUIDs == currentCharacteristicsUUIDs) return@update currentCharacteristics

            logger.d(LOG_KEY, "Caracteristicas actualizadas: ${newCharacteristicsUUIDs.toList()}")
            val noLongerExist = currentCharacteristics.filter { it.uuid !in newCharacteristicsUUIDs }
            noLongerExist.forEach { it.close() }
            val keepUntouched = currentCharacteristics.filter { it.uuid in newCharacteristicsUUIDs }
            val newOnes = newCharacteristics.filter { it.uuid !in currentCharacteristicsUUIDs }.map { it.toBLEDeviceCharacteristic(this, logger, coroutineScope) }

            return@update keepUntouched + newOnes
        }
    }

    private var sessionJob: Job? = null
    @SuppressLint("MissingPermission")
    private fun runSession() {
        sessionJob?.cancel()
        sessionJob = coroutineScope.launch {
            callbackFlow {
                val gattCallback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(
                        gatt: BluetoothGatt?,
                        status: Int,
                        newState: Int,
                    ) {
                        super.onConnectionStateChange(gatt, status, newState)
                        trySendWrapper(
                            when (newState) {
                                BluetoothProfile.STATE_CONNECTED -> {
                                    logger.d(LOG_KEY, "ConnectionStateChange: $newState")
                                    BLEGattEvent.Connection(
                                        BLESessionConnectionEvent.CONNECTED
                                    )
                                }
                                else -> {
                                    logger.e(LOG_KEY, "ConnectionStateChange: $newState")
                                    BLEGattEvent.Connection(BLESessionConnectionEvent.CONNECTION_LOST)
                                }
                            }
                        )
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                        super.onServicesDiscovered(gatt, status)
                        logger.d(LOG_KEY, "ServicesDiscovered: $status")
                        trySendWrapper(
                            when (status) {
                                BluetoothGatt.GATT_SUCCESS -> {
                                    gatt?.services?.let {
                                        updateServices(it)
                                        updateCharacteristics(it)
                                    }
                                    BLEGattEvent.Service(BLESessionServiceEvent.SUCCESS)
                                }
                                else -> {
                                    clearServicesAndCharacteristics()
                                    BLEGattEvent.Service(BLESessionServiceEvent.ERROR)
                                }
                            }
                        )
                    }

                    override fun onCharacteristicChanged(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic
                    ) {
                        super.onCharacteristicChanged(gatt, characteristic)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            logger.d(LOG_KEY, "${characteristic.uuid}: Mensaje recibido")
                            trySendWrapper(BLEGattEvent.Message(BLEMessageEvent(characteristic.uuid, characteristic.value)))
                        }
                    }

                    override fun onCharacteristicChanged(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray
                    ) {
                        super.onCharacteristicChanged(gatt, characteristic, value)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            logger.d(LOG_KEY, "${characteristic.uuid}: Mensaje recibido")
                            trySendWrapper(BLEGattEvent.Message(BLEMessageEvent(characteristic.uuid, value)))
                        }
                    }

                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray,
                        status: Int
                    ) {
                        super.onCharacteristicRead(gatt, characteristic, value, status)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            logger.d(LOG_KEY, "${characteristic.uuid}: Mensaje recibido")
                            trySendWrapper(BLEGattEvent.Message(BLEMessageEvent(characteristic.uuid, value)))
                        }
                    }

                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt?,
                        characteristic: BluetoothGattCharacteristic?,
                        status: Int
                    ) {
                        super.onCharacteristicRead(gatt, characteristic, status)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            if(characteristic!=null) {
                                logger.d(LOG_KEY, "${characteristic.uuid}: Mensaje recibido")
                                trySendWrapper(BLEGattEvent.Message(BLEMessageEvent(characteristic.uuid, characteristic.value)))
                            }
                        }
                    }
                }

                delay(500)

                logger.d(LOG_KEY, "Iniciando sesion GATT")
                val currentInstance = device.connectGatt(context, false, gattCallback)
                _instance.update { currentInstance }

                awaitClose {
                    logger.d(LOG_KEY, "Terminando sesion GATT")
                    currentInstance?.abortReliableWrite()
                    currentInstance?.close()
                    currentInstance?.disconnect()
                    _instance.update { null }
                }
            }.collect { tEvent ->
                when(tEvent) {
                    is BLEGattEvent.Connection -> _connectionEvents.emit(tEvent.event)
                    is BLEGattEvent.Message -> _messageEvents.emit(tEvent.event)
                    is BLEGattEvent.Service -> _serviceEvents.emit(tEvent.event)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun readCharacteristic(uuid: UUID) {
        logger.d(LOG_KEY, "Forzando lectura caracteristica: $uuid")
        val characteristic = gattCharacteristics.firstOrNull { it.uuid == uuid }
        val inst = _instance.value
        if(characteristic==null || inst==null) return
        inst.readCharacteristic(characteristic)
    }

    override fun discoverServices() {
        logger.d(LOG_KEY, "Descubriendo servicios")
        val tInstance = _instance.value ?: run {
            logger.e(LOG_KEY, "Error al descubrir servicios: No hay una instancia corriendo")
            return
        }
        tInstance.discoverServices()
    }

    override fun requestMtu(mtu: Int) {
        logger.d(LOG_KEY, "Solicitando mtu: $mtu")
        val tInstance = _instance.value ?: run {
            logger.e(LOG_KEY, "Error al solicitar mtu: No hay una instancia corriendo")
            return
        }
        tInstance.requestMtu(mtu)
    }

    private var connectJob: Job? = null
    @SuppressLint("MissingPermission")
    override suspend fun startSession(): Boolean {
        connectJob?.cancel()
        connectJob = Job()
        return try {
            withContext(coroutineScope.coroutineContext + connectJob!!) {
                logger.d(LOG_KEY, "Iniciando sesion..")
                runSession()
                val connectionResult = try {
                    _connectionEvents.waitForNextWithTimeout(20000)
                } catch (e: CancellationException) {
                    logger.e(LOG_KEY, "No se pudo iniciar la sesion: Timeout")
                    return@withContext false
                }

                if (connectionResult == BLESessionConnectionEvent.CONNECTED) {
                    logger.d(LOG_KEY, "Sesion iniciada correctamente")
                    return@withContext true
                } else {
                    logger.e(LOG_KEY, "No se pudo iniciar la sesion")
                    return@withContext false
                }
            }
        } catch (e: CancellationException) {
            logger.e(LOG_KEY, "No se pudo iniciar la sesion: Cancelado")
            false
        }
    }

    override fun endSession() {
        logger.d(LOG_KEY, "Terminando sesion")
        connectJob?.cancel()
        clearServicesAndCharacteristics()
        _instance.value?.also { tInstance->
            gattCharacteristics.forEach { characteristic->
                tInstance.setCharacteristicNotification(characteristic, false)
            }
        }
        sessionJob?.cancel()
    }

    override fun close() {
        logger.d(LOG_KEY, "Cerrando GATT")
        endSession()
    }

}