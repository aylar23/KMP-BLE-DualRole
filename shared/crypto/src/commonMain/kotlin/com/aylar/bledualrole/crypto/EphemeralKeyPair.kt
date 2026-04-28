package com.aylar.bledualrole.crypto

/**
 * A P-256 ephemeral key pair for a single HELLO handshake.
 * [publicKeyBytes] is sent in the HELLO frame; [deriveSessionKey] derives a shared secret
 * from the remote peer's public key.
 */
class EphemeralKeyPair internal constructor(
    val publicKeyBytes: ByteArray,
    internal val privateKeyBytes: ByteArray,
) {
    init {
        require(publicKeyBytes.size == PUBLIC_KEY_SIZE) { "P-256 public key must be $PUBLIC_KEY_SIZE bytes" }
        require(privateKeyBytes.size == PRIVATE_KEY_SIZE) { "P-256 private key must be $PRIVATE_KEY_SIZE bytes" }
    }

    companion object {
        /** Uncompressed P-256 public key: 04 | 32-byte X | 32-byte Y */
        const val PUBLIC_KEY_SIZE = 65
        const val PRIVATE_KEY_SIZE = 32
    }
}
