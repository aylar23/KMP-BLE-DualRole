package com.aylar.bledualrole.crypto

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256

class BleSessionCryptoImpl(
    private val provider: CryptographyProvider = CryptographyProvider.Default,
) : BleSessionCrypto {

    override suspend fun generateKeyPair(): EphemeralKeyPair {
        val keyPair = provider.get(ECDH).keyPairGenerator(EC.Curve.P256).generateKey()
        val publicBytes = keyPair.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW)
        val privateBytes = keyPair.privateKey.encodeToByteArray(EC.PrivateKey.Format.RAW)
        return EphemeralKeyPair(publicBytes, privateBytes)
    }

    override suspend fun deriveSessionKey(
        localKeyPair: EphemeralKeyPair,
        remotePeerPublicKey: ByteArray,
    ): SessionKey {
        val ecdh = provider.get(ECDH)
        val localPrivate = ecdh.privateKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PrivateKey.Format.RAW, localKeyPair.privateKeyBytes)
        val remotePublic = ecdh.publicKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PublicKey.Format.RAW, remotePeerPublicKey)
        val sharedSecret = localPrivate.sharedSecretGenerator()
            .generateSharedSecretToByteArray(remotePublic)

        val derivedKeyBytes = provider.get(HKDF)
            .secretDerivation(
                digest = SHA256,
                outputSize = SessionKey.KEY_SIZE.bytes,
                salt = null,
                info = "bledualrole-session-key".encodeToByteArray(),
            )
            .deriveSecretToByteArray(sharedSecret)
        return SessionKey(derivedKeyBytes)
    }

    override suspend fun encrypt(key: SessionKey, plaintext: ByteArray): ByteArray {
        val aesKey = provider.get(AES.GCM)
            .keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, key.rawKey)
        return aesKey.cipher().encrypt(plaintext)
    }

    override suspend fun decrypt(key: SessionKey, ciphertext: ByteArray): ByteArray {
        val aesKey = provider.get(AES.GCM)
            .keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, key.rawKey)
        return aesKey.cipher().decrypt(ciphertext)
    }
}