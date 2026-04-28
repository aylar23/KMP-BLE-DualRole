package com.aylar.bledualrole.crypto

/**
 * A session key derived via X25519 ECDH and used for ChaCha20-Poly1305 encryption.
 * Immutable; create a new one for each connection.
 */
class SessionKey internal constructor(internal val rawKey: ByteArray) {
    init {
        require(rawKey.size == KEY_SIZE) { "Session key must be $KEY_SIZE bytes" }
    }

    companion object {
        const val KEY_SIZE = 32
    }
}
