package com.aylar.bledualrole.ble

import kotlinx.coroutines.flow.Flow

expect class BleCentral {
    fun scan(filter: ScanFilter): Flow<DiscoveredPeer>
    suspend fun stopScan()
    suspend fun connect(peer: DiscoveredPeer): Connection
}