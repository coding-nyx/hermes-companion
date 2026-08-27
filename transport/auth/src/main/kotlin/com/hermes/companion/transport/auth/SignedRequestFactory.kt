package com.hermes.companion.transport.auth

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The only way out of this module. Callers describe the request they want; the
 * credential, if any, is attached here. There is deliberately no accessor that
 * returns a token, a header value, or an envelope — see `security.md`.
 */
interface SignedRequestFactory {
    fun get(path: String): Request
    fun post(path: String, json: String): Request
    /** GET with `Accept: text/event-stream` for an SSE endpoint. */
    fun getEventStream(path: String): Request
}

/** How a gateway is authenticated. Resolved only inside this module. */
sealed interface GatewayCredentials {
    data object None : GatewayCredentials
    /** A Keystore-sealed token: [gatewayId] names the AES alias gw/<id>, [envelope] is the sealed blob. */
    data class SealedRef(val gatewayId: String, val envelope: String) : GatewayCredentials
}

/**
 * Ingest/wipe only — never reveals a token. Used by pairing + settings.
 * (`security.md`: "no accessor ever returns a token".)
 */
interface TokenAdmin {
    /** Seal [token] for [gatewayId]; returns the opaque envelope to persist. */
    fun seal(gatewayId: String, token: String): String
    /** Delete the gateway's AES alias, so a leaked DB row is inert. */
    fun wipe(gatewayId: String)
}

object Tokens : TokenAdmin {
    override fun seal(gatewayId: String, token: String): String = KeystoreTokenStore.seal(gatewayId, token)
    override fun wipe(gatewayId: String) = KeystoreTokenStore.wipe(gatewayId)
}

object RequestFactory {
    fun create(baseUrl: String, credentials: GatewayCredentials): SignedRequestFactory =
        when (credentials) {
            GatewayCredentials.None -> Unauthenticated(baseUrl.trimEnd('/'))
            is GatewayCredentials.SealedRef ->
                Authenticated(baseUrl.trimEnd('/'), KeystoreTokenStore.open(credentials.gatewayId, credentials.envelope))
        }

    fun credentialsFor(authRef: String, envelope: String? = null): GatewayCredentials =
        if (authRef.isBlank() || authRef == "none" || envelope == null) GatewayCredentials.None
        else GatewayCredentials.SealedRef(gatewayId = authRef, envelope = envelope)
}

private val JSON = "application/json".toMediaType()

private class Unauthenticated(private val baseUrl: String) : SignedRequestFactory {
    override fun get(path: String): Request = Request.Builder().url(baseUrl + path).get().build()
    override fun post(path: String, json: String): Request =
        Request.Builder().url(baseUrl + path).post(json.toRequestBody(JSON)).build()
    override fun getEventStream(path: String): Request =
        Request.Builder().url(baseUrl + path).header("Accept", "text/event-stream").get().build()
}

private class Authenticated(private val baseUrl: String, bearer: String) : SignedRequestFactory {
    private val auth = "Bearer $bearer"
    private fun b(path: String) = Request.Builder().url(baseUrl + path).header("Authorization", auth)
    override fun get(path: String): Request = b(path).get().build()
    override fun post(path: String, json: String): Request = b(path).post(json.toRequestBody(JSON)).build()
    override fun getEventStream(path: String): Request = b(path).header("Accept", "text/event-stream").get().build()
}

/**
 * Reveals a sealed token for the node BROKER transport only — the WebSocket must
 * send `Authorization: Bearer`/`?token=`. Deliberately NOT on [TokenAdmin]; the
 * data layer calls this transiently when opening a broker and never persists or
 * surfaces the result.
 */
object BrokerTokens {
    fun reveal(gatewayId: String, envelope: String): String = KeystoreTokenStore.open(gatewayId, envelope)
}
