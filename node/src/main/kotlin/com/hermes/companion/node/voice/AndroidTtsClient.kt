package com.hermes.companion.node.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.concurrent.atomic.AtomicReference

/**
 * Phase 1: thin wrapper around Android's [TextToSpeech] engine so the rest
 * of the :node voice stack talks to a stable Kotlin interface rather than
 * the framework callback soup.
 *
 * Cloud TTS (MiniMax / Grok / ElevenLabs) arrives via
 * [com.hermes.companion.node.voice.CloudTtsClient] — this class is the
 * always-available fallback path.
 *
 * Lifecycle:
 *  - construct via [new] which kicks off async init (Android TTS requires it)
 *  - call [speak] with text; the utterance is queued and spoken in order
 *  - on [shutdown] release native resources
 *
 * Initialization failure (missing TTS engine, no internet for cloud voices,
 * etc.) is surfaced via [status] and via the onError path. Callers should
 * walk to the next TTS provider on persistent failure.
 */
class AndroidTtsClient internal constructor(
    private val tts: TextToSpeech,
) {

    /** What the engine reported during init. */
    enum class Status { Uninitialized, Ready, Failed }

    private val _status = AtomicReference(Status.Uninitialized)
    fun status(): Status = _status.get()

    /** Optional listener for utterance start / done / error callbacks. */
    interface Listener {
        fun onStart(utteranceId: String)
        fun onDone(utteranceId: String)
        fun onError(utteranceId: String, code: Int)
    }

    private val listener = AtomicReference<Listener?>(null)
    fun setListener(l: Listener?) { listener.set(l) }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                utteranceId?.let { listener.get()?.onStart(it) }
            }

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { listener.get()?.onDone(it) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { listener.get()?.onError(it, -1) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { listener.get()?.onError(it, errorCode) }
            }
        })
    }

    /** Queue [text] for spoken output. Returns the utterance id (also passed to listeners). */
    fun speak(text: String, utteranceId: String = "u-${System.nanoTime()}"): Int {
        if (_status.get() != Status.Ready) {
            Log.w(TAG, "speak called before Ready; dropping '$utteranceId'")
            return -1
        }
        return tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    /** Stop any queued or in-progress speech. */
    fun stop() {
        tts.stop()
    }

    /** Release native resources. Safe to call multiple times. */
    fun shutdown() {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
        _status.set(Status.Uninitialized)
    }

    internal fun markReady() = _status.set(Status.Ready)
    internal fun markFailed() = _status.set(Status.Failed)

    companion object {
        private const val TAG = "hermes-android-tts"

        /**
         * Async-init factory. The returned [AndroidTtsClient] is not yet
         * usable; callers should await [status] == Ready (or listen via
         * [setListener] before speak()).
         */
        fun new(
            context: Context,
            voiceId: String = "",
            onReady: ((AndroidTtsClient) -> Unit)? = null,
        ): AndroidTtsClient {
            var captured: AndroidTtsClient? = null
            lateinit var ttsRef: TextToSpeech
            ttsRef = TextToSpeech(context.applicationContext) { status ->
                val client = captured ?: return@TextToSpeech
                val engine = ttsRef
                if (status == TextToSpeech.SUCCESS) {
                    // Voice selection: pick from the engine's installed
                    // voice list if voiceId is set; otherwise the system
                    // default. Phase 1 surfaces curated voice ids via
                    // Settings -> Voice, future versions.
                    if (voiceId.isNotBlank()) {
                        runCatching {
                            val match = engine.voices
                                .firstOrNull { it.name == voiceId }
                            if (match != null) engine.voice = match
                        }
                    }
                    client.markReady()
                    onReady?.invoke(client)
                } else {
                    client.markFailed()
                    Log.w(TAG, "TextToSpeech init failed: $status")
                    onReady?.invoke(client)
                }
            }
            val client = AndroidTtsClient(ttsRef)
            captured = client
            return client
        }

    }
}
