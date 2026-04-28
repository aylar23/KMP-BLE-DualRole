package com.aylar.bledualrole.domain.repository

import com.aylar.bledualrole.domain.model.Transfer
import kotlinx.coroutines.flow.Flow

interface TransferRepository {
    fun observeByPeer(peerId: String): Flow<List<Transfer>>
    suspend fun insert(transfer: Transfer)
    suspend fun updateProgress(id: String, transferredBytes: Long, status: com.aylar.bledualrole.domain.model.TransferStatus)
    suspend fun complete(id: String, completedAtMs: Long)
    suspend fun fail(id: String)
}