package com.aylar.bledualrole.ble

import kotlinx.coroutines.flow.Flow

class IosBlecentral : BleCentral {
    override fun scan(filter: ScanFilter): Flow<DiscoveredPeer> = TODO("Phase 3")
    override suspend fun stopScan(): Unit = TODO("Phase 3")
    override suspend fun connect(peer: DiscoveredPeer): Connection = TODO("Phase 3")
}