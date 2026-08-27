package com.hermes.companion.transport.auth

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals a gateway's token under a per-gateway AES-GCM key in the Android
 * Keystore (alias `gw/<id>`). The sealed bytes are persisted by the data layer;
 * wiping the alias makes a leaked DB row inert (`security.md`). Device/Robolectric
 * only — the Keystore is not faithfully emulated in plain JVM unit tests.
 */
internal object KeystoreTokenStore {
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    private fun alias(gatewayId: String) = "gw/$gatewayId"

    private fun key(gatewayId: String): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias(gatewayId), null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            alias(gatewayId),
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // Not user-auth-required: the background broker must reconnect without
            // a prompt. Biometric gating is a separate per-use key.
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) runCatching { setIsStrongBoxBacked(true) } }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }.generateKey()
    }

    /** base64(IV ‖ ciphertext‖tag) — stored as TEXT. */
    fun seal(gatewayId: String, token: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(gatewayId)) }
        val ct = c.doFinal(token.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv + ct, Base64.NO_WRAP)
    }

    fun open(gatewayId: String, sealed: String): String {
        val blob = Base64.decode(sealed, Base64.NO_WRAP)
        val iv = blob.copyOfRange(0, IV_LEN)
        val ct = blob.copyOfRange(IV_LEN, blob.size)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
            .apply { init(Cipher.DECRYPT_MODE, key(gatewayId), GCMParameterSpec(TAG_BITS, iv)) }
        return String(c.doFinal(ct), Charsets.UTF_8)
    }

    fun wipe(gatewayId: String) =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias(gatewayId))
}
