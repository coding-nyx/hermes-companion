package com.hermes.companion.transport.auth

import android.os.Build
import androidx.annotation.RequiresApi
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

enum class NodeKeyAlg(val tag: String) { ED25519("ed25519"), P256("p256") }

/** The node's public identity + the id derived from it. */
data class PublicKeyInfo(val tagged: String, val nodeId: String, val alg: NodeKeyAlg)

/**
 * The node identity keypair, generated IN the Android Keystore and never
 * exported (`security.md`). Ed25519 on API 33+, EC P-256 below (still in the
 * Keystore, so the private key never enters app memory — preferred over a
 * bundled Ed25519 lib). Key generation is device/Robolectric only;
 * [deriveNodeId] is pure and unit-tested on the JVM.
 */
object NodeIdentityKey {
    private const val ALIAS = "node/identity"

    /** nodeId derives from the raw public-key bytes, identically on both sides. */
    fun deriveNodeId(taggedPublicKey: String): String {
        val raw = Base64.getUrlDecoder().decode(taggedPublicKey.substringAfter(':'))
        val h = MessageDigest.getInstance("SHA-256").digest(raw)
        return "nd_" + Base64.getUrlEncoder().withoutPadding().encodeToString(h).take(20)
    }

    /** Idempotent: generates on first call, then returns the existing identity. */
    fun ensure(): PublicKeyInfo {
        loadExisting()?.let { return it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { generate(NodeKeyAlg.ED25519) }.getOrNull()?.let { return it }
        }
        return generate(NodeKeyAlg.P256)
    }

    /** Possession proof over the pairing nonce; private key stays in the Keystore. */
    fun sign(challenge: ByteArray): ByteArray {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = ks.getKey(ALIAS, null) as PrivateKey
        val algo = if (currentAlg() == NodeKeyAlg.ED25519) "Ed25519" else "SHA256withECDSA"
        return Signature.getInstance(algo).run { initSign(key); update(challenge); sign() }
    }

    private fun loadExisting(): PublicKeyInfo? {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val cert = ks.getCertificate(ALIAS) ?: return null
        val pub = cert.publicKey
        val alg = if (pub.algorithm.contains("Ed", ignoreCase = true)) NodeKeyAlg.ED25519 else NodeKeyAlg.P256
        val tagged = tag(alg, pub.encoded)
        return PublicKeyInfo(tagged, deriveNodeId(tagged), alg)
    }

    private fun currentAlg(): NodeKeyAlg = loadExisting()?.alg ?: NodeKeyAlg.P256

    private fun generate(alg: NodeKeyAlg): PublicKeyInfo = when (alg) {
        NodeKeyAlg.ED25519 -> generateEd25519()
        NodeKeyAlg.P256 -> generateP256()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun generateEd25519(): PublicKeyInfo {
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            ALIAS, android.security.keystore.KeyProperties.PURPOSE_SIGN,
        )
            .setAlgorithmParameterSpec(java.security.spec.NamedParameterSpec("ed25519"))
            .setDigests(android.security.keystore.KeyProperties.DIGEST_NONE)
            .build()
        val kpg = KeyPairGenerator.getInstance(
            android.security.keystore.KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore",
        )
        kpg.initialize(spec)
        val kp = kpg.generateKeyPair()
        val tagged = tag(NodeKeyAlg.ED25519, kp.public.encoded)
        return PublicKeyInfo(tagged, deriveNodeId(tagged), NodeKeyAlg.ED25519)
    }

    private fun generateP256(): PublicKeyInfo {
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            ALIAS, android.security.keystore.KeyProperties.PURPOSE_SIGN,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
            .build()
        val kpg = KeyPairGenerator.getInstance(
            android.security.keystore.KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore",
        )
        kpg.initialize(spec)
        val kp = kpg.generateKeyPair()
        val tagged = tag(NodeKeyAlg.P256, kp.public.encoded)
        return PublicKeyInfo(tagged, deriveNodeId(tagged), NodeKeyAlg.P256)
    }

    private fun tag(alg: NodeKeyAlg, raw: ByteArray) =
        alg.tag + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
}
