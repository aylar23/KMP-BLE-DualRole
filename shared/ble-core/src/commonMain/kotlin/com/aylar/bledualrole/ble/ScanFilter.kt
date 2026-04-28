package com.aylar.bledualrole.ble

import com.aylar.bledualrole.protocol.BleUuids

data class ScanFilter(
    val serviceUuid: String = BleUuids.SERVICE,
)