package com.aylar.bledualrole.domain.model

data class Peer(
    val id: String,
    val name: String,
    val isBonded: Boolean,
    val lastSeenMs: Long,
    val protocolVersion: Int = 1,
)