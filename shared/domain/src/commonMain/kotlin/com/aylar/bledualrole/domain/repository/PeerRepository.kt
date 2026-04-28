package com.aylar.bledualrole.domain.repository

import com.aylar.bledualrole.domain.model.Peer
import kotlinx.coroutines.flow.Flow

interface PeerRepository {
    fun observeAll(): Flow<List<Peer>>
    suspend fun upsert(peer: Peer)
    suspend fun updateBondState(peerId: String, isBonded: Boolean)
    suspend fun delete(peerId: String)
}