# Security Architecture

Implements §6's token boundary, §7's pairing, §11's privacy model, and the transport tiers in `../04-connection/gateway-registry.md`.

## The token boundary, precisely

Decided: one process, boundary enforced by module visibility. `:transport:auth` holds credentials; its public API cannot express one.

```kotlin
// public
interface SignedRequestFactory {
    fun get(gateway: GatewayId, path: String): Request
    fun post(gateway: GatewayId, path: String, body: RequestBody): Request
    fun webSocket(gateway: GatewayId, path: String): Request
}
interface TokenAdmin {                       // used only by pairing and settings
    suspend fun store(gateway: GatewayId, envelope: SealedEnvelope): Result<Unit>
    suspend fun wipe(gateway: GatewayId)
    fun tier(gateway: GatewayId): TransportTier
}

// internal to :transport:auth
internal class TokenEnvelope(...)
internal class KeystoreTokenStore(...)
```

**What this buys:** no UI or feature module can name a type that carries a token, so a credential cannot reach a Compose tree, a log statement, a crash report or a saved-state bundle by accident. Enforced at compile time and by the dependency-check task in [modules.md](./modules.md).

**What it does not buy:** the tokens are in the same process, so anything with arbitrary code execution in the app — or root on the device — can read them. The stronger version was considered and rejected for IPC cost; see `../09-parity/openclaw-node-app.md`. Say "compile-time boundary", not "isolation", in any security claim we make.

## Keystore

- One AES key per gateway, alias `gw/<gateway_id>`, sealing the token envelope. Wiping a gateway deletes the alias, so a leaked database row is inert.
- Keys backing biometric-gated actions are created with `setUserAuthenticationRequired(true)` and a short validity window, so the gate is cryptographic rather than a UI check that can be skipped.
- StrongBox where the device offers it; the node identity key prefers it.

## Node identity and pairing

Per §7, with the crypto made explicit:

1. Ed25519 keypair generated **in** the Keystore, never exported. `node_id` derives from the public key.
2. The gateway shows a QR or a typed setup code carrying gateway id, a short-lived nonce, the broker URL and the server certificate fingerprint. No bearer token is ever in the code.
3. The app signs the nonce, proving possession.
4. The gateway returns a certificate bound to that public key and gateway.
5. **Both sides derive the verification phrase independently** — a truncated hash over `(server cert fingerprint ‖ nonce ‖ node public key)` mapped to a wordlist. Neither side transmits it, so a matching phrase rules out a party in the middle rather than merely echoing one.
6. Capabilities are granted afterwards, per profile, separately.

TLS even on a tailnet. Certificate pinning is opt-in and rotation-safe: pin a set, not a single certificate, and accept a signed rotation.

## Transport privilege tiers

Computed on every connect, stored on the gateway row, and treated as a cap on what may be granted:

- TLS, or cleartext to loopback / `.local` / emulator → eligible for operator access and a node session.
- Cleartext to anything else → chat and read-only inspection only. No node session, no capability grant, no write-scoped token.
- A tier drop ends the node session before the connection is otherwise used.

## Biometric gates

Four gates, from `../07-privacy/privacy-model.md`, applied at the **repository boundary** so a screen cannot forget one:

| Gate | Guards |
| --- | --- |
| App launch | the whole UI |
| Approval decision | `ConversationRepository.decide` |
| Node capability change | `NodeRepository.setGrant` |
| Secret-bearing view | anything rendering a digest, fingerprint or raw event body |

## Revocation

Per gateway, never global — §11. Revoking a node on one gateway deletes that gateway's grants, leases, receipts and Keystore alias, ends its broker session, and leaves every other gateway paired. Subsequent commands from the revoked gateway fail closed with a receipt, which is an acceptance criterion.

## Logging

A redacting interceptor in `:core:common` is the only log path. It strips bearer tokens, notification bodies, contact identifiers and digests. §14 requires that no token, secret or cross-profile event appears in logs or crash reports; that is a test, not a convention.

## See also

- [modules.md](./modules.md)
- [../04-connection/node-pairing.md](../04-connection/node-pairing.md)
- [../07-privacy/privacy-model.md](../07-privacy/privacy-model.md)
