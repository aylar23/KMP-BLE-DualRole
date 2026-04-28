package com.aylar.bledualrole.ble

import kotlinx.coroutines.flow.Flow

class IosBlePeripheral : BlePeripheral {
    override suspend fun startAdvertising(config: AdvertiseConfig): Unit = TODO("Phase 3")
    override fun incomingConnections(): Flow<Connection> = TODO("Phase 3")
    override suspend fun stopAdvertising(): Unit = TODO("Phase 3")
}
