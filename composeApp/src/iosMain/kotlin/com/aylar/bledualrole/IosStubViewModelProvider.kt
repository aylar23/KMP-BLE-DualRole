package com.aylar.bledualrole

import com.aylar.bledualrole.domain.model.Message
import com.aylar.bledualrole.domain.model.MessageStatus
import com.aylar.bledualrole.domain.model.Peer
import com.aylar.bledualrole.domain.model.Transfer
import com.aylar.bledualrole.domain.repository.MessageRepository
import com.aylar.bledualrole.domain.repository.PeerRepository
import com.aylar.bledualrole.domain.repository.TransferRepository
import com.aylar.bledualrole.domain.session.BleSessionController
import com.aylar.bledualrole.domain.session.ConnectedPeerInfo
import com.aylar.bledualrole.domain.session.PacketLogEntry
import com.aylar.bledualrole.domain.session.PeerConnectionStatus
import com.aylar.bledualrole.presentation.ChatViewModel
import com.aylar.bledualrole.presentation.DebugViewModel
import com.aylar.bledualrole.presentation.FileTransferViewModel
import com.aylar.bledualrole.presentation.PeerListViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Stub provider for iOS. Replace with IosBleSessionController backed by
 * CBCentralManager/CBPeripheralManager when ready.
 */
class IosStubViewModelProvider : AppViewModelProvider {

    private val stubSession = object : BleSessionController {
        override val isScanning = MutableStateFlow(false)
        override val connectedPeers: StateFlow<List<ConnectedPeerInfo>> = MutableStateFlow(emptyList())
        override val packetLog: Flow<PacketLogEntry> = emptyFlow()
        override suspend fun startScan() {}
        override suspend fun stopScan() {}
        override suspend fun connect(peerId: String) {}
        override suspend fun disconnect(peerId: String) {}
        override suspend fun sendMessage(peerId: String, text: String) {}
        override suspend fun sendFile(peerId: String, transferId: String, fileName: String, data: ByteArray) {}
        override fun connectionStatus(peerId: String): StateFlow<PeerConnectionStatus> =
            MutableStateFlow(PeerConnectionStatus.DISCONNECTED)
        override fun mtu(peerId: String): StateFlow<Int> = MutableStateFlow(185)
        override fun rssi(peerId: String): StateFlow<Int> = MutableStateFlow(-65)
    }

    private val stubPeerRepo = object : PeerRepository {
        override fun observeAll() = flowOf(emptyList<Peer>())
        override suspend fun upsert(peer: Peer) {}
        override suspend fun updateBondState(peerId: String, isBonded: Boolean) {}
        override suspend fun delete(peerId: String) {}
    }

    private val stubMessageRepo = object : MessageRepository {
        override fun observeByPeer(peerId: String) = flowOf(emptyList<Message>())
        override suspend fun insert(message: Message): Long = 0L
        override suspend fun updateStatus(id: Long, status: MessageStatus) {}
    }

    private val stubTransferRepo = object : TransferRepository {
        override fun observeByPeer(peerId: String): Flow<List<Transfer>> = flowOf(emptyList())
        override suspend fun insert(transfer: Transfer) {}
        override suspend fun updateProgress(id: String, transferredBytes: Long, status: com.aylar.bledualrole.domain.model.TransferStatus) {}
        override suspend fun complete(id: String, completedAtMs: Long) {}
        override suspend fun fail(id: String) {}
    }

    override fun peerListViewModel() = PeerListViewModel(stubPeerRepo, stubSession)
    override fun chatViewModel(peerId: String) = ChatViewModel(peerId, stubMessageRepo, stubSession)
    override fun fileTransferViewModel(peerId: String) =
        FileTransferViewModel(peerId, stubTransferRepo, stubSession)
    override fun debugViewModel() = DebugViewModel(stubSession)
    override fun pickFile(onPicked: (String, ByteArray) -> Unit) {}
}