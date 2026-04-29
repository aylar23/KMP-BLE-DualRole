package com.aylar.bledualrole.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aylar.bledualrole.domain.session.BleSessionController
import com.aylar.bledualrole.domain.session.ConnectedPeerInfo
import com.aylar.bledualrole.domain.session.PacketLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DebugViewModel(
    private val session: BleSessionController,
) : ViewModel() {

    val connectedPeers: StateFlow<List<ConnectedPeerInfo>> = session.connectedPeers

    private val _packetLog = MutableStateFlow<List<PacketLogEntry>>(emptyList())
    val packetLog: StateFlow<List<PacketLogEntry>> = _packetLog.asStateFlow()

    init {
        viewModelScope.launch {
            session.packetLog.collect { entry ->
                _packetLog.value = (_packetLog.value + entry).takeLast(MAX_LOG_ENTRIES)
            }
        }
    }

    fun clearLog() { _packetLog.value = emptyList() }

    companion object {
        private const val MAX_LOG_ENTRIES = 500
    }
}