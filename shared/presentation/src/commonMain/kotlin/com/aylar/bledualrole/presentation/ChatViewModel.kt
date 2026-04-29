package com.aylar.bledualrole.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aylar.bledualrole.domain.model.Message
import com.aylar.bledualrole.domain.repository.MessageRepository
import com.aylar.bledualrole.domain.session.BleSessionController
import com.aylar.bledualrole.domain.session.PeerConnectionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    val peerId: String,
    private val messageRepo: MessageRepository,
    private val session: BleSessionController,
) : ViewModel() {

    val messages: StateFlow<List<Message>> = messageRepo.observeByPeer(peerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val connectionStatus: StateFlow<PeerConnectionStatus> = session.connectionStatus(peerId)

    val mtu: StateFlow<Int> = session.mtu(peerId)

    private val _draftText = MutableStateFlow("")
    val draftText: StateFlow<String> = _draftText.asStateFlow()

    fun onDraftChanged(text: String) { _draftText.value = text }

    fun sendMessage() {
        val text = _draftText.value.trim()
        if (text.isEmpty()) return
        _draftText.value = ""
        viewModelScope.launch {
            runCatching { session.sendMessage(peerId, text) }
        }
    }
}