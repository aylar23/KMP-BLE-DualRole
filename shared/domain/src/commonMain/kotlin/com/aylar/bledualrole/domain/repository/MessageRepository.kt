package com.aylar.bledualrole.domain.repository

import com.aylar.bledualrole.domain.model.Message
import com.aylar.bledualrole.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun observeByPeer(peerId: String): Flow<List<Message>>
    suspend fun insert(message: Message): Long
    suspend fun updateStatus(id: Long, status: MessageStatus)
}