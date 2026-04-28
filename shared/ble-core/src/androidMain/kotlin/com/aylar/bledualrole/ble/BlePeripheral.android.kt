package com.aylar.bledualrole.ble

import kotlinx.coroutines.flow.Flow

actual class BlePeripheral {
    actual suspend fun startAdvertising(config: AdvertiseConfig): Unit = TODO("Phase 2")
    actual fun incomingConnections(): Flow<Connection> = TODO("Phase 2")
    actual suspend fun stopAdvertising(): Unit = TODO("Phase 2")
}