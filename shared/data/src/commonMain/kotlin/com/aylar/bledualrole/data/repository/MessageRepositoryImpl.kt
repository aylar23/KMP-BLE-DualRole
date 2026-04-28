package com.aylar.bledualrole.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.aylar.bledualrole.data.db.BleDatabase
import com.aylar.bledualrole.domain.model.Message
import com.aylar.bledualrole.domain.model.MessageStatus
import com.aylar.bledualrole.domain.repository.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.aylar.bledualrole.data.db.Message as DbMessage

class MessageRepositoryImpl(private val db: BleDatabase) : MessageRepository {

    override fun observeByPeer(peerId: String): Flow<List<Message>> =
        db.messagesQueries.selectByPeer(peerId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map(DbMessage::toDomain) }

    override suspend fun insert(message: Message): Long {
        db.messagesQueries.insert(
            peer_id = message.peerId,
            content = message.content,
            is_outgoing = if (message.isOutgoing) 1L else 0L,
            timestamp_ms = message.timestampMs,
            status = message.status.name.lowercase(),
        )
        return db.messagesQueries.selectAll().executeAsList().lastOrNull()?.id ?: -1L
    }

    override suspend fun updateStatus(id: Long, status: MessageStatus) {
        db.messagesQueries.updateStatus(status = status.name.lowercase(), id = id)
    }
}

private fun DbMessage.toDomain() = Message(
    id = id,
    peerId = peer_id,
    content = content,
    isOutgoing = is_outgoing != 0L,
    timestampMs = timestamp_ms,
    status = when (status) {
        "delivered" -> MessageStatus.DELIVERED
        "failed" -> MessageStatus.FAILED
        else -> MessageStatus.SENT
    },
)
