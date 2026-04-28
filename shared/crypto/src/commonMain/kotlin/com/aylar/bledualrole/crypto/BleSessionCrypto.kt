package com.aylar.bledualrole.crypto

/**
 * Handles the app-layer crypto for a BLE session:
 *
 *  1. Generate ephemeral P-256 key pair ([generateKeyPair])
 *  2. Exchange public keys in HELLO / HELLO_ACK frames
 *  3. Derive shared session key ([deriveSessionKey]) via ECDH + HKDF-SHA256
 *  4. Encrypt outgoing payload ([encrypt]) and decrypt incoming payload ([decrypt])
 *
 * Uses AES-256-GCM; the library prepends a random 12-byte IV to each ciphertext:
 * [12B IV][N bytes ciphertext+tag].
 *
 * Why both OS pairing AND app-layer crypto?
 * OS pairing protects the link but is vulnerable to MITM during the initial pairing
 * ceremony if the user doesn't verify the passkey. App-layer ECDH provides independent
 * forward secrecy and prevents a compromised OS-level key from exposing past sessions.
 */
interface BleSessionCrypto {
    suspend fun generateKeyPair(): EphemeralKeyPair
    suspend fun deriveSessionKey(localKeyPair: EphemeralKeyPair, remotePeerPublicKey: ByteArray): SessionKey
    suspend fun encrypt(key: SessionKey, plaintext: ByteArray): ByteArray
    suspend fun decrypt(key: SessionKey, ciphertext: ByteArray): ByteArray
}
