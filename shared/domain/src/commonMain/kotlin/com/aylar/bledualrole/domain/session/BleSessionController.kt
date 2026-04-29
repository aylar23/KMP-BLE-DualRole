package com.aylar.bledualrole.domain.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class PeerConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED }

data class ConnectedPeerInfo(
    val id: String,
    val name: String,
    val status: PeerConnectionStatus,
    val mtu: Int,
    val rssiDbm: Int,
)

data class PacketLogEntry(val timestampMs: Long, val peerId: String, val direction: String, val bytes: Int)

/**
 * Platform-agnostic façade over the BLE stack, consumed by shared ViewModels.
 * Implemented in ble-core (or composeApp) using AndroidBleCentral / IosBleCentral.
 */
interface BleSessionController {
    val isScanning: StateFlow<Boolean>
    val connectedPeers: StateFlow<List<ConnectedPeerInfo>>
    val packetLog: Flow<PacketLogEntry>

    suspend fun startScan()
    suspend fun stopScan()
    suspend fun connect(peerId: String)
    suspend fun disconnect(peerId: String)
    suspend fun sendMessage(peerId: String, text: String)
    suspend fun sendFile(peerId: String, transferId: String, fileName: String, data: ByteArray)
    fun connectionStatus(peerId: String): StateFlow<PeerConnectionStatus>
    fun mtu(peerId: String): StateFlow<Int>
    fun rssi(peerId: String): StateFlow<Int>
}