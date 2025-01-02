package com.supermegazinc.ble.device.characteristic

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import java.util.UUID

fun Flow<List<BLEDeviceCharacteristic>>.filterByUUID(
    uuid: UUID
): Flow<BLEDeviceCharacteristic> {
    return map { previousList->
        previousList.firstOrNull { it.uuid ==  uuid}
    }
        .filterNotNull()
}