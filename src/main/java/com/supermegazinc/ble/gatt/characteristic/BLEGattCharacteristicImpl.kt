package com.supermegazinc.ble.gatt.characteristic

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Build
import com.supermegazinc.ble.gatt.BLEGattController
import com.supermegazinc.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.UUID

@SuppressLint("MissingPermission")
class BLEGattCharacteristicImpl(
    private val bleGattController: BLEGattController,
    private val characteristic: BluetoothGattCharacteristic,
    private val logger: Logger,
    coroutineScope: CoroutineScope
) : BLEGattCharacteristic {

    companion object {
        const val LOG_KEY = "BLE-CHARACTERISTIC"
    }

    override val uuid: UUID
        get() = characteristic.uuid

    private val _message = Channel<ByteArray?>(capacity = 5, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val message: ReceiveChannel<ByteArray?>
        get() = _message

    override fun setNotification(state: Boolean) {
        logger.d(LOG_KEY, "$uuid[$this] : Setear notification: $state")
        bleGattController.instance.value!!.setCharacteristicNotification(characteristic, state)
    }

    override fun forceRead() {
        logger.d(LOG_KEY, "$uuid[$this] : Forzar lectura")
        bleGattController.readCharacteristic(this@BLEGattCharacteristicImpl.uuid)
    }

    @SuppressLint("MissingPermission")
    override suspend fun send(message: ByteArray) {
        val messageList = message.toList()
        logger.d(
            LOG_KEY,
            "$uuid[$this] : Enviando mensaje[BYT]: [${messageList.size}][${messageList.joinToString(",")}]"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bleGattController.instance.value?.writeCharacteristic(characteristic, message, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            characteristic.setValue(message)
            bleGattController.instance.value?.writeCharacteristic(characteristic)
        }
    }

    private var receiveMessagesJob: Job = coroutineScope.launch {
        bleGattController.messageEvents
            .filter { it.characteristicUUID == characteristic.uuid }
            .collect { tMessage->
                val messageList = tMessage.message.toList()
                logger.d(LOG_KEY,"$uuid[$this] : Mensaje recibido[BYT]: [${messageList.size}][${messageList.joinToString(",")}]")
                _message.send(tMessage.message)
            }
    }

    override fun close() {
        logger.d(LOG_KEY, "$uuid[$this] : Cerrando")
        receiveMessagesJob.cancel()
        _message.close()
        bleGattController.instance.value?.setCharacteristicNotification(characteristic,false)
    }

}