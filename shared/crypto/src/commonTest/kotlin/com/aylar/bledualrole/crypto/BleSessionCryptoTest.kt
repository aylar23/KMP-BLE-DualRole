package com.aylar.bledualrole.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BleSessionCryptoTest {

    private val crypto = BleSessionCryptoImpl()

    @Test
    fun encryptDecryptRoundtrip() = runTest {
        val kp = crypto.generateKeyPair()
        // Self-exchange: derive key using own public key as "remote" (valid for unit test)
        val sessionKey = crypto.deriveSessionKey(kp, kp.publicKeyBytes)
        val plaintext = "hello BLE world".encodeToByteArray()

        val ciphertext = crypto.encrypt(sessionKey, plaintext)
        assertNotEquals(plaintext.toList(), ciphertext.toList(), "ciphertext must differ from plaintext")

        val decrypted = crypto.decrypt(sessionKey, ciphertext)
        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun encryptProducesRandomCiphertexts() = runTest {
        val kp = crypto.generateKeyPair()
        val sessionKey = crypto.deriveSessionKey(kp, kp.publicKeyBytes)
        val plaintext = "same message".encodeToByteArray()

        val c1 = crypto.encrypt(sessionKey, plaintext)
        val c2 = crypto.encrypt(sessionKey, plaintext)
        assertNotEquals(c1.toList(), c2.toList(), "each encryption must produce a unique ciphertext")
    }

    @Test
    fun mutualKeyExchange() = runTest {
        val aliceKp = crypto.generateKeyPair()
        val bobKp = crypto.generateKeyPair()

        val aliceSession = crypto.deriveSessionKey(aliceKp, bobKp.publicKeyBytes)
        val bobSession = crypto.deriveSessionKey(bobKp, aliceKp.publicKeyBytes)

        assertContentEquals(aliceSession.rawKey, bobSession.rawKey, "both sides must derive the same session key")

        val plaintext = "mutual secret".encodeToByteArray()
        val ciphertext = crypto.encrypt(aliceSession, plaintext)
        val decrypted = crypto.decrypt(bobSession, ciphertext)
        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun decryptWithWrongKeyFails() = runTest {
        val kp = crypto.generateKeyPair()
        val correctKey = crypto.deriveSessionKey(kp, kp.publicKeyBytes)

        val otherKp = crypto.generateKeyPair()
        val wrongKey = crypto.deriveSessionKey(otherKp, otherKp.publicKeyBytes)

        val ciphertext = crypto.encrypt(correctKey, "secret".encodeToByteArray())

        assertFailsWith<Exception> {
            crypto.decrypt(wrongKey, ciphertext)
        }
    }

    @Test
    fun generatedPublicKeyHasCorrectSize() = runTest {
        val kp = crypto.generateKeyPair()
        assertTrue(kp.publicKeyBytes.size == EphemeralKeyPair.PUBLIC_KEY_SIZE)
        assertTrue(kp.privateKeyBytes.size == EphemeralKeyPair.PRIVATE_KEY_SIZE)
    }

    @Test
    fun sessionKeyHasCorrectSize() = runTest {
        val kp = crypto.generateKeyPair()
        val sessionKey = crypto.deriveSessionKey(kp, kp.publicKeyBytes)
        assertTrue(sessionKey.rawKey.size == SessionKey.KEY_SIZE)
    }
}