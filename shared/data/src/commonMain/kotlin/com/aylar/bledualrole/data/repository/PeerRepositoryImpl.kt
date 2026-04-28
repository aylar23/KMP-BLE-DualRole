package com.aylar.bledualrole.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.aylar.bledualrole.data.db.BleDatabase
import com.aylar.bledualrole.domain.model.Peer
import com.aylar.bledualrole.domain.repository.PeerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.aylar.bledualrole.data.db.Peer as DbPeer

class PeerRepositoryImpl(private val db: BleDatabase) : PeerRepository {

    override fun observeAll(): Flow<List<Peer>> =
        db.peersQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map(DbPeer::toDomain) }

    override suspend fun upsert(peer: Peer) {
        db.peersQueries.upsert(
            id = peer.id,
            name = peer.name,
            is_bonded = if (peer.isBonded) 1L else 0L,
            last_seen_ms = peer.lastSeenMs,
            protocol_version = peer.protocolVersion.toLong(),
        )
    }

    override suspend fun updateBondState(peerId: String, isBonded: Boolean) {
        db.peersQueries.updateBondState(
            is_bonded = if (isBonded) 1L else 0L,
            id = peerId,
        )
    }

    override suspend fun delete(peerId: String) {
        db.peersQueries.delete(peerId)
    }
}

private fun DbPeer.toDomain() = Peer(
    id = id,
    name = name,
    isBonded = is_bonded != 0L,
    lastSeenMs = last_seen_ms,
    protocolVersion = protocol_version.toInt(),
)
