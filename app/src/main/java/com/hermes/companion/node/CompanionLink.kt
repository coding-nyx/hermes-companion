package com.hermes.companion.node

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Outbound mailbox client. The phone dials the Hermes plugin over Tailscale;
 * nothing inbound is required.
 */
object CompanionLink {
    private val json = Json { ignoreUnknownKeys = true }
    private val media = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private val up = AtomicBoolean(false)

    @Volatile
    var lastError: String? = null

    fun isUp(): Boolean = up.get()

    fun start(ctx: Context, url: String, session: String) {
        stop()
        NodePrefs.setGatewayUrl(ctx, url)
        NodePrefs.setSession(ctx, session)
        up.set(true)
        lastError = null
        val app = ctx.applicationContext
        pollJob = scope.launch {
            while (isActive) {
                try {
                    val batch = poll(url, session)
                    for (msg in batch) handle(app, url, session, msg)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e.message
                    delay(2_000)
                }
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        up.set(false)
    }

    suspend fun pair(ctx: Context, url: String, code: String): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("code", JsonPrimitive(code))
            put(
                "device",
                buildJsonObject {
                    put("name", JsonPrimitive(Build.MODEL))
                    put("model", JsonPrimitive("${Build.MANUFACTURER} ${Build.MODEL}"))
                    put("kind", JsonPrimitive("android"))
                },
            )
        }
        val req = Request.Builder()
            .url("${url.trimEnd('/')}/companion/pair")
            .post(body.toString().toRequestBody(media))
            .build()
        client.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            val parsed = json.parseToJsonElement(text).jsonObject
            if (!res.isSuccessful || parsed["ok"]?.jsonPrimitive?.contentOrNull == "false") {
                throw IllegalStateException(parsed["error"]?.jsonPrimitive?.contentOrNull ?: "pair failed")
            }
            val session = parsed["session"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalStateException("no session")
            start(ctx, url.trimEnd('/'), session)
            session
        }
    }

    private fun poll(url: String, session: String): List<JsonObject> {
        val req = Request.Builder()
            .url("${url.trimEnd('/')}/companion/outbox")
            .header("Authorization", "Bearer $session")
            .get()
            .build()
        client.newCall(req).execute().use { res ->
            if (res.code == 401) {
                up.set(false)
                return emptyList()
            }
            val text = res.body?.string().orEmpty()
            val parsed = json.parseToJsonElement(text).jsonObject
            val messages = parsed["messages"]?.jsonArray ?: return emptyList()
            return messages.map { it.jsonObject }
        }
    }

    private suspend fun handle(ctx: Context, url: String, session: String, msg: JsonObject) {
        val type = msg["type"]?.jsonPrimitive?.contentOrNull ?: return
        if (type == "ping") {
            inbox(url, session, listOf(buildJsonObject {
                put("type", JsonPrimitive("pong"))
                put("t", msg["t"] ?: JsonPrimitive(System.currentTimeMillis()))
            }))
            return
        }
        if (type != "tool_call") return
        val name = msg["name"]?.jsonPrimitive?.contentOrNull ?: return
        val id = msg["id"]?.jsonPrimitive?.contentOrNull ?: return
        val args = msg["args"]?.jsonObject ?: JsonObject(emptyMap())
        val result = NodeTools.execute(ctx, name, args)
        inbox(
            url,
            session,
            listOf(
                buildJsonObject {
                    put("type", JsonPrimitive("tool_result"))
                    put("id", JsonPrimitive(id))
                    put("name", JsonPrimitive(name))
                    put("result", JsonPrimitive(result))
                },
            ),
        )
    }

    private fun inbox(url: String, session: String, messages: List<JsonObject>) {
        val body = buildJsonObject {
            put("messages", kotlinx.serialization.json.JsonArray(messages))
        }
        val req = Request.Builder()
            .url("${url.trimEnd('/')}/companion/inbox")
            .header("Authorization", "Bearer $session")
            .post(body.toString().toRequestBody(media))
            .build()
        client.newCall(req).execute().close()
    }

    fun restore(ctx: Context) {
        val url = NodePrefs.gatewayUrl(ctx)
        val session = NodePrefs.session(ctx)
        if (url.isNotBlank() && !session.isNullOrBlank()) start(ctx, url, session)
    }
}
