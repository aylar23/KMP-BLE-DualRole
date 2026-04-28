package com.aylar.bledualrole.ble

import kotlinx.coroutines.flow.Flow

actual class BleCentral {
    actual fun scan(filter: ScanFilter): Flow<DiscoveredPeer> = TODO("Phase 3")
    actual suspend fun stopScan(): Unit = TODO("Phase 3")
    actual suspend fun connect(peer: DiscoveredPeer): Connection = TODO("Phase 3")
}