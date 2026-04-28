package com.aylar.bledualrole.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.aylar.bledualrole.data.db.BleDatabase
import com.aylar.bledualrole.domain.model.Transfer
import com.aylar.bledualrole.domain.model.TransferDirection
import com.aylar.bledualrole.domain.model.TransferStatus
import com.aylar.bledualrole.domain.repository.TransferRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.aylar.bledualrole.data.db.Transfer as DbTransfer

class TransferRepositoryImpl(private val db: BleDatabase) : TransferRepository {

    override fun observeByPeer(peerId: String): Flow<List<Transfer>> =
        db.transfersQueries.selectByPeer(peerId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map(DbTransfer::toDomain) }

    override suspend fun insert(transfer: Transfer) {
        db.transfersQueries.insert(
            id = transfer.id,
            peer_id = transfer.peerId,
            file_name = transfer.fileName,
            file_size_bytes = transfer.fileSizeBytes,
            direction = transfer.direction.name.lowercase(),
            started_at_ms = transfer.startedAtMs,
        )
    }

    override suspend fun updateProgress(id: String, transferredBytes: Long, status: TransferStatus) {
        db.transfersQueries.updateProgress(
            transferred_bytes = transferredBytes,
            status = status.name.lowercase(),
            id = id,
        )
    }

    override suspend fun complete(id: String, completedAtMs: Long) {
        db.transfersQueries.complete(completed_at_ms = completedAtMs, id = id)
    }

    override suspend fun fail(id: String) {
        db.transfersQueries.fail(id)
    }
}

private fun DbTransfer.toDomain() = Transfer(
    id = id,
    peerId = peer_id,
    fileName = file_name,
    fileSizeBytes = file_size_bytes,
    transferredBytes = transferred_bytes,
    direction = if (direction == "send") TransferDirection.SEND else TransferDirection.RECEIVE,
    status = when (status) {
        "in_progress" -> TransferStatus.IN_PROGRESS
        "completed" -> TransferStatus.COMPLETED
        "failed" -> TransferStatus.FAILED
        else -> TransferStatus.PENDING
    },
    startedAtMs = started_at_ms,
    completedAtMs = completed_at_ms,
)
