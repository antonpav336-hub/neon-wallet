package com.neonwallet.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

internal object WalletDeviceSigner {
    private const val KEY_ALIAS = "neon_wallet_casino_approval"

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun ensureKeyPair() {
        val store = keyStore()
        if (store.containsAlias(KEY_ALIAS)) return
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        generator.generateKeyPair()
    }

    fun publicKeyBase64(): String {
        ensureKeyPair()
        val certificate = keyStore().getCertificate(KEY_ALIAS)
            ?: throw IllegalStateException("Не удалось получить ключ NEON WALLET")
        return Base64.encodeToString(certificate.publicKey.encoded, Base64.NO_WRAP)
    }

    fun signBase64(message: String): String {
        ensureKeyPair()
        val privateKey = keyStore().getKey(KEY_ALIAS, null)
            ?: throw IllegalStateException("Не удалось получить ключ подтверждения")
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey as java.security.PrivateKey)
        signer.update(message.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(signer.sign(), Base64.NO_WRAP)
    }
}
