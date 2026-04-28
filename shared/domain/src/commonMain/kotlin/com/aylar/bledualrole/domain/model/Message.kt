package com.aylar.bledualrole.domain.model

data class Message(
    val id: Long = 0,
    val peerId: String,
    val content: String,
    val isOutgoing: Boolean,
    val timestampMs: Long,
    val status: MessageStatus = MessageStatus.SENT,
)

enum class MessageStatus { SENT, DELIVERED, FAILED }