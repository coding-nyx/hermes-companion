package com.hermes.companion.transport.auth

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The only way out of this module. Callers describe the request they want; the
 * credential, if any, is attached here.
 *
 * There is deliberately no accessor that returns a token, a header value, or
 * an envelope — see `plan/10-architecture/security.md`. Adding authentication
 * later changes this module and nothing else.
 */
interface SignedRequestFactory {
    fun get(path: String): Request
    fun post(path: String, json: String): Request
}

/** How a gateway is authenticated. Resolved only inside this module. */
sealed interface GatewayCredentials {
    /** No credential at all — the only kind the PoC gateways use. */
    data object None : GatewayCredentials

    /** A Keystore-sealed envelope, referenced by alias. */
    data class SealedRef(val ref: String) : GatewayCredentials
}

object RequestFactory {

    fun create(baseUrl: String, credentials: GatewayCredentials): SignedRequestFactory =
        when (credentials) {
            GatewayCredentials.None -> Unauthenticated(baseUrl.trimEnd('/'))
            // Fails loudly rather than quietly sending an unauthenticated
            // request to a gateway that expects one. Implemented at migration
            // step 10, with the Keystore.
            is GatewayCredentials.SealedRef -> throw NotImplementedError(
                "sealed token envelopes are not implemented yet (migration step 10); " +
                    "gateway referenced '${credentials.ref}'"
            )
        }

    /** Maps a gateway registry entry's `authRef` onto a credential kind. */
    fun credentialsFor(authRef: String): GatewayCredentials =
        if (authRef.isBlank() || authRef == "none") GatewayCredentials.None
        else GatewayCredentials.SealedRef(authRef)
}

private class Unauthenticated(private val baseUrl: String) : SignedRequestFactory {

    private val jsonMedia = "application/json".toMediaType()

    override fun get(path: String): Request =
        Request.Builder().url(baseUrl + path).get().build()

    override fun post(path: String, json: String): Request =
        Request.Builder().url(baseUrl + path).post(json.toRequestBody(jsonMedia)).build()
}
