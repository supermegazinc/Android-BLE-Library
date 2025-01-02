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
import com.supermegazinc.ble.gatt.model.BLEGattConnectError
import com.supermegazinc.ble.gatt.model.BLEGattEvent
import com.supermegazinc.ble.gatt.model.BLESessionConnectionEvent
import com.supermegazinc.ble.gatt.model.BLESessionServiceEvent
import com.supermegazinc.escentials.Result
import com.supermegazinc.escentials.waitForNextInstanceWithTimeout
import com.supermegazinc.logger.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@SuppressLint("MissingPermission")
class BLEGattControllerImpl(
    private val device: BluetoothDevice,
    private val mtu: Int,
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

    private val _services = MutableStateFlow<List<BluetoothGattService>>(emptyList())
    override val services: StateFlow<List<BluetoothGattService>>
        get() = _services.asStateFlow()

    private val _characteristics = MutableStateFlow<List<BluetoothGattCharacteristic>>(emptyList())
    override val characteristics: StateFlow<List<BluetoothGattCharacteristic>>
        get() = _characteristics.asStateFlow()

    private val _events = MutableSharedFlow<BLEGattEvent>()
    override val events: SharedFlow<BLEGattEvent>
        get() = _events.asSharedFlow()

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
                        trySend(
                            when (newState) {
                                BluetoothProfile.STATE_CONNECTED -> {
                                    logger.i(LOG_KEY, "ConnectionStateChange: $newState")
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
                        logger.i(LOG_KEY, "ServicesDiscovered: $status")
                        trySend(
                            when (status) {
                                BluetoothGatt.GATT_SUCCESS -> {
                                    val currentServices = gatt?.services
                                    logger.i(LOG_KEY, "Servicios: ${currentServices?.map { it.uuid }}")
                                    _services.update { currentServices ?: emptyList()  }

                                    _characteristics.update {
                                        val currentCharacteristics = currentServices?.flatMap { it.characteristics } ?: emptyList()
                                        logger.i(LOG_KEY, "Caracteristicas: ${currentCharacteristics.map { it.uuid }}")
                                        currentCharacteristics
                                    }

                                    BLEGattEvent.Service(BLESessionServiceEvent.SUCCESS)
                                }
                                else -> {
                                    _services.update { emptyList() }
                                    _characteristics.update { emptyList() }
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
                            logger.i(LOG_KEY, "${characteristic.uuid}: Mensaje recibido")
                            trySend(BLEGattEvent.Message(characteristic, characteristic.value))
                        }
                    }

                    override fun onCharacteristicChanged(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray
                    ) {
                        super.onCharacteristicChanged(gatt, characteristic, value)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            logger.i(LOG_KEY, "${characteristic.uuid}: Mensaje recibido")
                            trySend(BLEGattEvent.Message(characteristic, value))
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
                            logger.i(LOG_KEY, "${characteristic.uuid}: Mensaje recibido")
                            trySend(BLEGattEvent.Message(characteristic, value))
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
                                logger.i(LOG_KEY, "${characteristic.uuid}: Mensaje recibido")
                                trySend(BLEGattEvent.Message(characteristic, characteristic.value))
                            }
                        }
                    }
                }

                delay(2000)

                logger.i(LOG_KEY, "Iniciando sesion GATT")
                val currentInstance = device.connectGatt(context, false, gattCallback)
                _instance.update { currentInstance }

                awaitClose {
                    logger.i(LOG_KEY, "Terminando sesion GATT")
                    currentInstance?.abortReliableWrite()
                    currentInstance?.close()
                    currentInstance?.disconnect()
                }
            }.collect { tEvent ->
                _events.emit(tEvent)
                if(tEvent is BLEGattEvent.Connection && tEvent.event == BLESessionConnectionEvent.CONNECTION_LOST) {
                    logger.e(LOG_KEY, "Conexion perdida, cancelando sesion..")
                    sessionJob?.cancel()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun readCharacteristic(uuid: UUID) {
        val characteristic = _characteristics.value.firstOrNull { it.uuid == uuid }
        val inst = _instance.value
        if(characteristic==null || inst==null) return
        inst.readCharacteristic(characteristic)
    }

    override fun discoverServices() {
        _instance.value?.discoverServices()
    }

    private var connectJob: Job? = null
    @SuppressLint("MissingPermission")
    override suspend fun connect(): Result<Unit, BLEGattConnectError> {
        connectJob?.cancel()
        connectJob = Job()
        return try {
             withContext(coroutineScope.coroutineContext + connectJob!!) {
                 logger.i(LOG_KEY, "Conectando")
                 runSession()
                 _events.waitForNextInstanceWithTimeout<BLEGattEvent.Connection>(10000)?.let { tResult->
                     if(tResult.event == BLESessionConnectionEvent.CONNECTED) {
                         logger.i(LOG_KEY, tResult.event.toString())
                         delay(1000)
                         _instance.value!!.requestMtu(mtu)
                         Result.Success(Unit)
                     }
                     else {
                         logger.e(LOG_KEY, tResult.event.toString())
                         disconnect()
                         Result.Fail(BLEGattConnectError.CANT_CONNECT)
                     }
                 } ?: run {
                     logger.e(LOG_KEY, BLEGattConnectError.TIMEOUT.toString())
                     disconnect()
                     Result.Fail(BLEGattConnectError.TIMEOUT)
                 }
             }
        } catch (e: CancellationException) {
            logger.e(LOG_KEY, "Cancelado. Desconectando")
            disconnect()
            Result.Fail(BLEGattConnectError.CANCELED)
        }
    }

    override fun disconnect() {
        logger.i(LOG_KEY, "Desconectando")
        connectJob?.cancel()
        sessionJob?.cancel()
    }

}