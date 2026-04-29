package com.aylar.bledualrole.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface Connection {
    val peerId: PeerId
    val state: StateFlow<ConnectionState>
    val mtu: StateFlow<Int>
    val incoming: Flow<ByteArray>

    suspend fun send(bytes: ByteArray)
    suspend fun sendNoAck(bytes: ByteArray) = send(bytes)
    suspend fun requestMtu(size: Int): Int
    suspend fun bond(): BondResult
    suspend fun close()
}