package com.hermes.companion.broker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class PairingResult(
    val nodeId: String,
    val token: String,
    val expiresAt: Long,
    val grantedCaps: List<String>,
    val brokerUrl: String,
)

/**
 * Pairs the phone with a gateway's companion plugin: POST /pair with the node's
 * public key and requested capabilities, receive a long-lived token and the
 * broker URL. (Ed25519 possession-proof + verification phrase land in Phase 10;
 * today the public key is sent and the setup code is burned on first use.)
 */
class NodePairingClient(private val client: OkHttpClient = OkHttpClient()) {

    suspend fun pair(
        baseUrl: String,
        setupCode: String,
        publicKey: String,
        nodeName: String,
        nodeModel: String,
        requestedCaps: List<String>,
    ): Result<PairingResult> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("setupCode", setupCode)
                .put("publicKey", publicKey)
                .put("nodeName", nodeName)
                .put("nodeModel", nodeModel)
                .put("requestedCaps", JSONArray(requestedCaps))
                .toString()
            val req = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/pair")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("pair failed: ${resp.code} $text")
                val o = JSONObject(text)
                val caps = o.optJSONArray("grantedCaps") ?: JSONArray()
                PairingResult(
                    nodeId = o.getString("nodeId"),
                    token = o.getString("token"),
                    expiresAt = o.optLong("expiresAt"),
                    grantedCaps = (0 until caps.length()).map { caps.getString(it) },
                    brokerUrl = o.optString("brokerUrl", baseUrl.trimEnd('/') + "/ws/node"),
                )
            }
        }
    }
}
