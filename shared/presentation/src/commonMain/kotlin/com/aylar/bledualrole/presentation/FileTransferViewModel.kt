package com.aylar.bledualrole.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aylar.bledualrole.domain.model.Transfer
import com.aylar.bledualrole.domain.repository.TransferRepository
import com.aylar.bledualrole.domain.session.BleSessionController
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SendFileRequest(val peerId: String, val transferId: String, val fileName: String, val data: ByteArray)

class FileTransferViewModel(
    val peerId: String,
    private val transferRepo: TransferRepository,
    private val session: BleSessionController,
) : ViewModel() {

    val transfers: StateFlow<List<Transfer>> = transferRepo.observeByPeer(peerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    fun sendFile(fileName: String, data: ByteArray) {
        val transferId = generateTransferId()
        viewModelScope.launch {
            runCatching {
                session.sendFile(peerId, transferId, fileName, data)
            }.onFailure { e ->
                _sendError.value = e.message
            }
        }
    }

    fun clearError() { _sendError.value = null }

    private fun generateTransferId(): String = "tx-${Random.nextLong().toULong()}"
}