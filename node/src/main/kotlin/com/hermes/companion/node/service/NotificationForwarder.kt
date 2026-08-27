package com.hermes.companion.node.service

import android.util.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * T7 (companion-gateway-routing.md): posts each posted notification to the
 * active gateway's /v1/notifications/incoming endpoint so the Hermes agent
 * (via webhook) can act on it.
 *
 * Pure outbound: no DB, no UI, no side-effects beyond the POST.
 *
 * [activeUrl] + [nodeId] are read from [ActiveGatewayConfig] which the :app
 * modules populate via SettingsViewModel.setActive. When either is null we
 * no-op silently - no active gateway means there's nothing to forward to.
 */
class NotificationForwarder(
    private val activeUrl: String?,
    private val nodeId: String?,
    private val client: OkHttpClient = defaultClient,
) {

    /**
     * POST {package, title, text, posted_at, nodeId} as application/x-www-form-urlencoded
     * to `{activeUrl}/v1/notifications/incoming`. Synchronous; callers run it on
     * a background thread. Failures are logged and swallowed - the NLS must never
     * crash on a forwarded POST failure, even if the gateway is offline.
     */
    fun postIncoming(
        packageName: String,
        title: String,
        text: String,
        postedAt: Long,
    ) {
        val base = activeUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val nd = nodeId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val url = base.trimEnd('/') + "/v1/notifications/incoming"
        val body = FormBody.Builder()
            .add("package", packageName)
            .add("title", title)
            .add("text", text)
            .add("posted_at", postedAt.toString())
            .add("nodeId", nd)
            .build()
        val req = okhttp3.Request.Builder().url(url).post(body).build()
        try {
            client.newCall(req).execute().use { /* drain + close */ }
        } catch (t: Throwable) {
            Log.w(TAG, "postIncoming failed for $packageName: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    companion object {
        private const val TAG = "hermes-notif-fwd"
        // Short timeouts: a hung gateway should never block the NLS thread.
        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .callTimeout(3, TimeUnit.SECONDS)
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .build()
    }
}