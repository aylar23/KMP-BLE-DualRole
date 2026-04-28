package com.aylar.bledualrole.ble

import kotlinx.coroutines.flow.Flow

interface BlePeripheral {
    suspend fun startAdvertising(config: AdvertiseConfig)
    fun incomingConnections(): Flow<Connection>
    suspend fun stopAdvertising()
}