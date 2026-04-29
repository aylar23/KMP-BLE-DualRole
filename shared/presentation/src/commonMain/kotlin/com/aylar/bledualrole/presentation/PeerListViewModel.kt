package com.aylar.bledualrole.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aylar.bledualrole.domain.model.Peer
import com.aylar.bledualrole.domain.repository.PeerRepository
import com.aylar.bledualrole.domain.session.BleSessionController
import com.aylar.bledualrole.domain.session.ConnectedPeerInfo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PeerListUiState(
    val peers: List<Peer> = emptyList(),
    val connectedPeers: List<ConnectedPeerInfo> = emptyList(),
    val isScanning: Boolean = false,
)

class PeerListViewModel(
    private val peerRepo: PeerRepository,
    private val session: BleSessionController,
) : ViewModel() {

    val peers: StateFlow<List<Peer>> = peerRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val connectedPeers: StateFlow<List<ConnectedPeerInfo>> = session.connectedPeers

    val isScanning: StateFlow<Boolean> = session.isScanning

    fun startScan() {
        viewModelScope.launch { session.startScan() }
    }

    fun stopScan() {
        viewModelScope.launch { session.stopScan() }
    }

    fun connect(peerId: String) {
        viewModelScope.launch { session.connect(peerId) }
    }

    fun disconnect(peerId: String) {
        viewModelScope.launch { session.disconnect(peerId) }
    }
}