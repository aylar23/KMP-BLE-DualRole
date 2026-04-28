package com.aylar.bledualrole.domain.model

data class Transfer(
    val id: String,
    val peerId: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val transferredBytes: Long = 0,
    val direction: TransferDirection,
    val status: TransferStatus = TransferStatus.PENDING,
    val startedAtMs: Long,
    val completedAtMs: Long? = null,
) {
    val progressFraction: Float
        get() = if (fileSizeBytes == 0L) 0f else transferredBytes.toFloat() / fileSizeBytes
}

enum class TransferDirection { SEND, RECEIVE }

enum class TransferStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED }