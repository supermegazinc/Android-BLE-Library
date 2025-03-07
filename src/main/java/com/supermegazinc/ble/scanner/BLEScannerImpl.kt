package com.supermegazinc.ble.scanner

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import com.supermegazinc.ble.adapter.BLEAdapterImpl
import com.supermegazinc.ble.scanner.mapper.toBLEScannedDevice
import com.supermegazinc.ble.scanner.model.BLEScannedDevice
import com.supermegazinc.logger.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

@SuppressLint("MissingPermission")
class BLEScannerImpl(
    private val logger: Logger,
    private val adapter: BLEAdapterImpl
) : BLEScanner {

    companion object {
        private const val LOG_KEY = "BLE-SCANNER"
    }

    private val _scannedDevices = MutableStateFlow<List<BLEScannedDevice>>(emptyList())
    override val scannedDevices: StateFlow<List<BLEScannedDevice>>
        get() = _scannedDevices.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.toBLEScannedDevice()?.also {tDevice->
                if(tDevice !in _scannedDevices.value) {
                    logger.d(LOG_KEY,
                        "Nuevo dispositivo: " +
                                "\nNombre: ${tDevice.name.toString()}" +
                                "\nMac: ${tDevice.mac}"
                    )
                    _scannedDevices.update { it + tDevice }
                }
            }
        }
    }

    override fun start(
        serviceUUID: List<UUID>?,
    ) {
        logger.d(LOG_KEY, "Comenzando escaneo..")
        stop()
        clear()

        val filters = serviceUUID?.map { uuid->
            ScanFilter.Builder().setServiceUuid(ParcelUuid(uuid)).build()
        }

        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        val adapterL1 = adapter.adapter
        if(adapterL1==null) {
            logger.e(LOG_KEY, "ERROR: El adaptador no esta disponible")
            return
        }

        val scanner = adapterL1.bluetoothLeScanner
        if(scanner==null) {
            logger.e(LOG_KEY, "ERROR: El escaner no esta disponible")
            return
        }
        scanner.startScan(
            filters,
            settings,
            scanCallback
        )

        logger.d(LOG_KEY, "Escaneo comenzado")

    }

    override fun stop() {
        logger.d(LOG_KEY, "Deteniendo escaneo")

        val adapterL1 = adapter.adapter

        if(adapterL1==null) {
            logger.e(LOG_KEY, "ERROR: El adaptador no esta disponible")
            return
        }

        val scanner = adapterL1.bluetoothLeScanner
        if(scanner==null) {
            logger.e(LOG_KEY, "ERROR: El escaner no esta disponible")
            return
        }
        scanner.stopScan(scanCallback)
        logger.d(LOG_KEY, "Escaneo detenido")
    }

    override fun clear() {
        _scannedDevices.update { emptyList() }
    }
}