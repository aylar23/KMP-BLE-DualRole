package com.aylar.bledualrole.ble

import com.aylar.bledualrole.protocol.BleUuids

data class AdvertiseConfig(
    val localName: String,
    val serviceUuid: String = BleUuids.SERVICE,
    val includeTxPower: Boolean = false,
)