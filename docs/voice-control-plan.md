# Voice Control + Wake-Word — Hermes Companion

**Status:** Design v1 — August 2026
**Module:** `:node` (services) + `:app` (UI) + `hermes-companion-gateway` (proxy) + upstream Hermes (skill)
**Integrates with:** T7 (NotificationListenerService pattern), T8 (gateway pairing), OTP webhook pipeline

---

## 1. Goal

Add always-listening wake-word ("hey hermes") and push-to-talk dictation to the Hermes Companion Android app. Voice input is routed through the existing gateway proxy into the upstream Hermes agent session. Replies come back via TTS.

Two hard rules:

- Wake-word detection must be **on-device** (no cloud round-trip). Privacy + latency.
- The voice path must reuse the same plumbing as T7/T8: NLS for capability discovery, `ActiveGatewayConfig` file pattern for `:app`/`:node` boundary, gateway proxy for upstream coordination, Hermes webhook for skill routing.

---

## 2. Architecture

```
[Android Mic]
    │ 16 kHz mono PCM
    ▼
[HermesVoiceWakeWordService]  ◄── on-device Porcupine (or openwakeword fallback)
    │ detection event
    ▼
[HermesVoiceCaptureService]  ◄── AudioRecord, partial wake-lock, STT
    │ partial / final transcript
    ▼
[VoiceForwarder] (analogous to NotificationForwarder)
    │ POST /v1/voice/transcript  (or WS /v1/voice/stream)
    ▼
[companion-gateway :9120]  ──── /v1/voice/synthesize ◄── upstream TTS (minimax/grok/elevenlabs) or android
    │
    ▼
[upstream Hermes: hermes webhook subscribe companion-voice --events voice.transcript]
    │
    ▼
[Hermes agent session] ──► reply text
    │
    ▼
[gateway POST /v1/voice/synthesize back to companion]
    │
    ▼
[HermesTtsPlayer] (Android TextToSpeech or streamed audio)
    │
    ▼
[Speaker]
```

The wake-word path is fully local. The transcript → agent → reply path is the same gateway round-trip already used by notifications.

---

## 3. New components

### 3.1 `:node` module (the workhorse)

**Files to add:**

| Path | Purpose |
|------|---------|
| `:node/src/main/kotlin/.../voice/HermesVoiceWakeWordService.kt` | Foreground service. Owns the embedded Porcupine model. Reads mic via AudioRecord. Emits detection events. |
| `:node/src/main/kotlin/.../voice/HermesVoiceCaptureService.kt` | Foreground service. Captures user utterance after wake-word fires. Runs Android SpeechRecognizer or Whisper-tiny. Hands transcript to VoiceForwarder. |
| `:node/src/main/kotlin/.../voice/VoiceForwarder.kt` | POSTs finalized transcript to gateway, manages WebSocket for streaming mode. Analogous to NotificationForwarder. |
| `:node/src/main/kotlin/.../voice/HermesTtsPlayer.kt` | Wraps Android TextToSpeech. Falls back to gateway-streamed MP3 if remote voice is configured. |
| `:node/src/main/kotlin/.../voice/AudioFocusManager.kt` | Handles `AUDIOFOCUS_GAIN_TRANSIENT`, ducks music, yields to phone calls and nav. |
| `:node/src/main/kotlin/.../voice/VoiceConfig.kt` | Reads `voice.json` from the same files dir as `active_gateway.json`. Holds: wake-word model path, wake-word phrase, STT engine, TTS engine, TTS voice id, push-to-talk hotkey. |
| `:node/src/main/kotlin/.../voice/WakeWordModel.kt` | Interface; two impls: `PorcupineWakeWord` (prod) and `OpenWakeWordWakeWord` (free fallback). |

**Code stub — HermesVoiceWakeWordService.kt:**

```kotlin
@AndroidEntryPoint
class HermesVoiceWakeWordService : LifecycleService() {

    @Inject lateinit var wakeWord: WakeWordModel
    @Inject lateinit var config: VoiceConfig
    @Inject lateinit var audioFocus: AudioFocusManager
    @Inject lateinit var captureStarter: CaptureStarter

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT

    override fun onCreate() {
        super.onCreate()
        startForeground(WW_NOTIF_ID, buildNotification())
        wakeWord.load(modelPath = config.wakeWordModelPath, phrase = config.phrase)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        launchAudioLoop()
        return START_STICKY
    }

    private fun launchAudioLoop() = lifecycleScope.launch(Dispatchers.IO) {
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, // bypasses AEC/AutoGain
            sampleRate, channelConfig, encoding, minBuf * 4
        )
        record.startRecording()
        val buf = ShortArray(minBuf)
        try {
            while (isActive) {
                val read = record.read(buf, 0, buf.size)
                if (read > 0 && wakeWord.process(buf, read)) {
                    audioFocus.requestTransient()
                    captureStarter.startCapture(reason = CaptureReason.WAKE_WORD)
                    break // let capture service own the mic
                }
            }
        } finally {
            record.stop(); record.release()
        }
    }
}
```

**Code stub — HermesVoiceCaptureService.kt:**

```kotlin
@AndroidEntryPoint
class HermesVoiceCaptureService : LifecycleService() {

    @Inject lateinit var stt: SttEngine
    @Inject lateinit var forwarder: VoiceForwarder
    @Inject lateinit var config: VoiceConfig
    @Inject lateinit var wakeLock: VoiceWakeLock

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(CAPTURE_NOTIF_ID, buildNotification())
        wakeLock.acquire(maxMs = config.maxCaptureMs + 5_000L)
        lifecycleScope.launch {
            stt.streamPartial { partial ->
                forwarder.sendPartial(partial)
            }.onSuccess { finalText ->
                forwarder.sendFinal(finalText)
            }.onFailure { err ->
                forwarder.sendError(err)
            }
        }
        return START_NOT_STICKY
    }
}
```

### 3.2 Gateway (Python aiohttp, port 9120)

**Files to add under `hermes-companion-gateway/`:**

| Path | Purpose |
|------|---------|
| `server/routes/voice.py` | `/v1/voice/transcript`, `/v1/voice/synthesize`, `/v1/voice/stream` (WS) |
| `server/services/voice_bus.py` | Binds an open transcript to the active Hermes webhook subscription, fans the reply back to the right companion session |
| `server/services/tts_proxy.py` | Proxies TTS to upstream (MiniMax, Grok, or ElevenLabs) when remote voice is configured. MiniMax is the default because it is already pooled in the agent auth; Grok via the xAI provider slot is the fallback when MiniMax quota exhausts. Local Android TextToSpeech is handled entirely on-device, no gateway call. |

**Code stub — `/v1/voice/transcript`:**

```python
@router.post("/v1/voice/transcript")
async def post_transcript(req: Request, body: VoiceTranscript) -> Response:
    session = req.app["voice_bus"].bind(body.session_id)
    await session.publish({
        "type": "voice.transcript",
        "text": body.text,
        "is_final": body.is_final,
        "captured_at": body.captured_at,
        "wake_word": body.wake_word,
    })
    if not body.is_final:
        return Response(status=204)
    # Block until upstream replies (or timeout)
    reply = await session.await_reply(timeout=body.timeout_ms / 1000.0)
    return {"reply_text": reply.text, "reply_audio_url": reply.audio_url}
```

**Code stub — `/v1/voice/synthesize`:**

```python
@router.post("/v1/voice/synthesize")
async def post_synthesize(body: VoiceSynthesize) -> Response:
    # Used when upstream Hermes already produced audio (MiniMax/Grok/ElevenLabs reply)
    # Companion just streams it back through HermesTtsPlayer.
    audio_url = await req.app["tts_proxy"].ensure_cached(
        text=body.text, voice=body.voice
    )
    return {"audio_url": audio_url, "content_type": "audio/mpeg"}
```

### 3.3 Upstream Hermes skill

**File:** `~/.hermes/skills/companion-voice/SKILL.md`

Mirrors `notification-otp-reply`. Subscribes to `companion-voice` webhook:

```bash
hermes webhook subscribe companion-voice \
  --events voice.transcript \
  --url https://<gateway>/v1/voice/voice-webhook
```

The skill routes the transcript to the active Hermes agent session and emits a `voice.reply` event back through the gateway. The gateway binds reply → companion session via `voice_bus`.

### 3.4 `:data:repo` and `:core:common`

| Path | Purpose |
|------|---------|
| `:core:common/src/main/kotlin/.../voice/VoiceConfig.kt` | Data class (mirrors `ActiveGatewayConfig`) |
| `:core:common/src/main/kotlin/.../voice/VoiceEvent.kt` | Sealed: WakeDetected, TranscriptPartial, TranscriptFinal, ReplyReady, TtsFailed |
| `:data:repo/src/main/kotlin/.../VoicePreferencesRepository.kt` | Hilt module, reads `voice.json`, exposes Flow<VoiceConfig> |
| `:data:db/.../migrations/...` | New Room migration if we store per-session wake-word enabled flag |

### 3.5 `:app`

| Path | Purpose |
|------|---------|
| `:app/.../voice/VoiceSettingsScreen.kt` | Compose: toggle wake-word, pick wake-word phrase, pick STT engine, pick TTS engine (Android / MiniMax / Grok / ElevenLabs), pick TTS voice, test mic button |
| `:app/.../voice/PushToTalkButton.kt` | Hold-to-record FAB on the main screen |

---

## 4. Decision matrix

### Wake-word

| Option | Pros | Cons | Verdict |
|--------|------|------|---------|
| Picovoice Porcupine | Tiny (<1 MB), <5% CPU idle, built-in phrases + custom train, well-maintained Android SDK | Commercial license (~$0.10/device/year for custom wake-word) | **Primary** |
| openwakeword | Open-source, free, decent accuracy on common phrases | Larger model (~5 MB), harder to integrate, more tuning | Fallback for cost-sensitive builds |
| Vosk | Open-source, full STT bundled | Overkill for wake-word; ~50 MB; higher idle CPU | Rejected — wrong tool |

**Recommendation: Porcupine primary, openwakeword as the free build flavor.** Pluggable behind `WakeWordModel` interface so we can swap without touching call sites.

### STT

| Option | Pros | Cons | Verdict |
|--------|------|------|---------|
| Android SpeechRecognizer | Free, low-latency for short utterances, on-device on modern Android | Quality varies by OEM; cloud fallback for long audio | **Primary** |
| Whisper-tiny (whisper.cpp) | Excellent quality, fully on-device, no cloud | ~40 MB model, ~2-3s per 5s audio on mid-range devices, battery cost | Opt-in for users who want privacy-grade STT |
| Stream upstream via gateway | Zero on-device work | Latency round-trip, always hits upstream | Rejected for default; used only as failover |

**Recommendation: Android SpeechRecognizer by default.** Settings exposes "high-accuracy (Whisper-tiny)" toggle that downloads the model on demand.

### TTS

| Option | Pros | Cons | Verdict |
|--------|------|------|---------|
| Android TextToSpeech | Free, on-device, sub-100ms first byte for short replies | Robotic on cheaper phones, OEM-dependent voices | **Default** |
| **MiniMax TTS** (via gateway) | Neural, multilingual, ~30 voices via `mmx_speech_voices`; **already pooled** in `~/.hermes/profiles/coder/config.yaml` (no new auth) | Per-character billing, ~1.2 s round-trip | **Opt-in #1** (recommended cloud tier) |
| **Grok TTS** (xAI, via gateway) | Neural, OpenAI-compatible API; usually pooled since Grok is also the chat provider | Per-character billing, ~1.4 s round-trip | **Opt-in #2** (fallback when MiniMax quota exhausted) |
| ElevenLabs (via gateway) | Premium quality, voice cloning | Separate API key, per-character cost, ~1.5 s round-trip | **Premium** (only if user explicitly asks for it) |
| Piper (local) | Open-source, runs on-device, decent | ~50 MB per voice, model download | Future: research mode |

**Recommendation:** Android TextToSpeech by default; user can opt into a cloud tier in Settings -> Voice -> "Cloud TTS provider". The opt-in defaults to **MiniMax** because it is the only neural provider that ships with auth already pooled in the agent config — zero new billing setup. Grok TTS is the fallback when MiniMax quota exhausts (single `hermes auth add xai` if not pooled). ElevenLabs is offered as a separate "premium voice" toggle for users who want ElevenLabs' voice cloning specifically. Piper deferred to v0.4.

---

## 5. Integration with existing T7/T8

**Reuse ActiveGatewayConfig pattern.** The wake-word service reads the same `active_gateway.json` that NLS already writes, so we never need a new permission surface. Add `voice.json` alongside it:

```
/data/data/<pkg>/files/
  active_gateway.json   (existing — from T7/T8)
  voice.json            (new — wake-word enabled, phrase, engines)
```

`:app` writes `voice.json` when the user toggles settings. `:node` reads it on service start and on `FileObserver` change.

**Reuse gateway proxy.** The gateway already speaks `POST /v1/notifications/incoming` upstream and round-trips replies. We mirror that with `/v1/voice/transcript`. Same webhook plumbing. Same auth token.

**Reuse Hermes webhook pattern.** Same `hermes webhook subscribe` flow the OTP skill uses — just a different topic (`voice.transcript`) and a different skill file.

**No new permissions beyond what T7 already requires**, except:

- `RECORD_AUDIO` (already implicit in NLS path? No — separate permission)
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` (Android 14+)
- `POST_NOTIFICATIONS` (Android 13+) for the wake-word persistent notification
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (user-toggled, recommended)

**No NLS involvement.** NLS is for incoming notifications, not outbound mic capture. Voice is its own service lifecycle.

---

## 6. Permission flow

| Permission | When asked | Hard-block? |
|------------|-----------|-------------|
| RECORD_AUDIO | First time wake-word toggle is enabled | Yes |
| POST_NOTIFICATIONS | Same time, with rationale | Yes (Android 13+) |
| FOREGROUND_SERVICE_MICROPHONE | Manifest; auto-granted | No |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | After first successful wake-word detection | No (degrades gracefully) |
| Accessibility service (BT button remap) | Optional, only if user enables "headset button" | No |

Wake-word cannot start until RECORD_AUDIO + POST_NOTIFICATIONS are granted. The settings screen shows the rationale explicitly: "Hermes needs the mic to hear 'hey hermes' even when the screen is off."

---

## 7. Audio focus

`AudioFocusManager` wraps `AudioManager.requestAudioFocus`:

- **Phone call ringing/in-progress:** yield, no wake-word capture. Audio focus loss → service sleeps.
- **Music playing:** duck to 20% during TTS reply, restore after. Capture still works (uses VOICE_RECOGNITION source).
- **Navigation prompt (Google Maps):** TTS waits 1.5s, then ducks over. Capture still works.
- **Other voice assistant active (Google Assistant):** yield mic. If user invokes "hey hermes" while GA is hot-worded, our detector should still fire but GA will also fire — accept the ambiguity.

Wake-word source uses `MediaRecorder.AudioSource.VOICE_RECOGNITION` (not `MIC`) so Android's automatic gain control + echo cancellation are bypassed — we want raw audio for STT.

---

## 8. Battery budget

**Target: <5% extra drain per hour of always-listening.** Porcupine on a Pixel 6 measured at ~3 mA idle at 16 kHz.

| Phase | Current draw | Time/hour | mAh/hour |
|-------|-------------|-----------|----------|
| Wake-word idle (Porcupine) | 3 mA | 60 min | 3.0 |
| Active capture (5s after wake) | 80 mA | 30 s | 0.7 |
| TTS playback (Android TTS) | 120 mA | 60 s | 2.0 |
| TTS playback (MiniMax neural) | 0 mA (audio streamed) | per reply | ~0 mAh idle, network-bound |
| Network (gateway round-trip) | 200 mA | 30 s | 1.7 |
| **Total estimated** | | | **~7.4 mAh/hour** |

Pixel 6 battery = 4500 mAh. **~600 hours standby** in always-listen mode if no other activity. Real-world with OS overhead + radio: ~24 hours of standby with wake-word on.

Comparison: Google Assistant always-listening ~10-15 mAh/hour; Alexa app ~12 mAh/hour. We're competitive.

---

## 9. Failure modes + recovery

| Failure | Detection | Recovery |
|---------|-----------|----------|
| Porcupine model fails to load | `WakeWordModel.load()` throws | Restart service; if 3 fails, fall back to openwakeword; if still failing, disable wake-word, notify user |
| Mic permission revoked | `SecurityException` on AudioRecord.start | Stop service, show settings deep-link |
| STT timeout (5s no speech) | `SttEngine.streamPartial` emits no final | Send empty final; upstream replies with "did you want to say something?" |
| Gateway offline | HTTP request fails or times out 2s | Queue transcript locally, retry with exponential backoff; surface "offline — saved" toast |
| TTS engine crashes | `TextToSpeech` onError | Walk provider chain: Android -> MiniMax -> Grok -> ElevenLabs. If all fail, show reply as on-screen text. |
| Wake-word false positive storm | 5+ detections in 10s | Pause detector for 60s, log, alert user |
| Battery critical (<15%) | BatteryManager broadcast | Disable wake-word automatically, notify user, restore when charged |

---

## 10. Privacy posture

| Data | Stays on device | Via gateway | Reaches upstream Hermes |
|------|:--:|:--:|:--:|
| Raw mic PCM during wake-word detection | yes | no | no |
| Raw mic PCM during utterance capture | deleted after STT | no | no |
| Recognized transcript text | no | yes | yes |
| Wake-word detection events (count, no audio) | yes (local log) | optional metrics | no |
| Hermes reply text | yes | yes | yes (originates there) |
| TTS audio (MiniMax / Grok / ElevenLabs) | cached | yes | yes (originates there) |
| Voice config (engines, phrase) | yes | no | no |

Hard rule: raw audio never leaves the device. Only transcripts.

---

## 11. Implementation phases

### Phase 1: STT + TTS via Android APIs — **1 week**

Push-to-talk only. No wake-word yet.

- `:node/.../voice/HermesVoiceCaptureService.kt`
- `:node/.../voice/HermesTtsPlayer.kt`
- `:node/.../voice/VoiceForwarder.kt`
- `gateway/server/routes/voice.py` (`/v1/voice/transcript` only)
- `:app/.../voice/PushToTalkButton.kt`
- Upstream skill: `companion-voice` stub

**Acceptance:** Holding the in-app button captures speech, transcribes, sends to Hermes, plays reply through Android TTS. Settings toggle to opt into MiniMax cloud TTS produces neural-voice audio within ~1.2 s. Works with gateway online. No wake-word.

### Phase 2: Wake-word detector — **1 week**

Always-listen service.

- `:node/.../voice/HermesVoiceWakeWordService.kt`
- `:node/.../voice/WakeWordModel.kt` (Porcupine impl)
- `core:common/.../VoiceConfig.kt` + `data:repo/.../VoicePreferencesRepository.kt`
- `:app/.../voice/VoiceSettingsScreen.kt`
- `voice.json` file bridge

**Acceptance:** Saying "hey hermes" with the app installed but in background triggers capture end-to-end. False-positive rate < 1/hour in quiet room. Battery hit matches budget.

### Phase 3: Hermes voice skill — **3 days**

- `~/.hermes/skills/companion-voice/SKILL.md`
- `gateway/server/services/voice_bus.py`
- WebSocket `/v1/voice/stream` for partial transcripts (lower latency back-and-forth)

**Acceptance:** Streaming partial transcripts appear in upstream agent logs. Final reply routing works for both modes.

### Phase 4: Gateway voice endpoints — **2 days**

- `/v1/voice/synthesize` (MiniMax default, Grok fallback, ElevenLabs premium)
- `tts_proxy.py` with caching
- Error codes + offline queue

**Acceptance:** MiniMax voice selected in settings produces neural audio; Grok voice selected when MiniMax quota exhausted produces neural audio with similar quality. ElevenLabs voice cloning is supported for users who specifically request it. Offline queue survives gateway restart.

### Phase 5 (stretch): Whisper-tiny STT — **1 week**

Optional model download from settings. Replaces Android SpeechRecognizer when enabled.

**Total: ~3 weeks core, +1 week stretch.**

---

## 12. Risks + open questions

**Risks that could derail:**

- Porcupine license cost at scale. Mitigation: openwakeword fallback ready behind same interface.
- Battery drain on low-end devices (4 GB RAM, older SoC). Mitigation: disable wake-word by default on devices below threshold, push-to-talk only.
- Android 14 `FOREGROUND_SERVICE_MICROPHONE` type enforcement may break on OEMs that lag. Mitigation: graceful degradation to push-to-talk only.
- Wake-word false positives during music playback. Mitigation: VOICE_RECOGNITION source + energy gate; user can pause wake-word during media.
- Hermes webhook delivery latency. Mitigation: local "thinking…" chime; show partial transcripts as they stream.
- TTS voice consistency across OEMs. Mitigation: MiniMax neural (already pooled) as escape hatch.

**Open questions to resolve before starting:**

1. Porcupine: per-device license, or per-app-volume? (Affects unit economics at scale.)
2. Whisper-tiny model size policy: auto-download over Wi-Fi only, or also cellular? (Affects first-run UX.)
3. Multi-user voice profiles, or single voice per install? (Affects settings UI scope.)
4. Should wake-word detection survive `Doze` mode? Partial wake-lock is enough; full wake-lock would be overkill and battery-hostile.
5. Default TTS voice for the cloud tier: MiniMax has ~30 voices via `mmx_speech_voices`. Ship with a curated 5 or let the user pick on first opt-in? Default recommendation: ship curated list (one per locale + a "neutral" voice).
6. BT headset button: support all headsets, or only the most common profiles (HFP, AVRCP)?

---

## 13. File summary

Created or modified by this design:

```
companion/
  app/
    src/main/kotlin/.../voice/
      VoiceSettingsScreen.kt         (new)
      PushToTalkButton.kt            (new)
  node/
    src/main/kotlin/.../voice/
      HermesVoiceWakeWordService.kt  (new)
      HermesVoiceCaptureService.kt   (new)
      VoiceForwarder.kt              (new)
      HermesTtsPlayer.kt             (new)
      AudioFocusManager.kt           (new)
      VoiceConfig.kt                 (new)
      WakeWordModel.kt               (new)
      PorcupineWakeWord.kt           (new)
      OpenWakeWordWakeWord.kt        (new)
      SttEngine.kt                   (new)
  core/common/src/main/kotlin/.../voice/
    VoiceConfig.kt                   (new)
    VoiceEvent.kt                    (new)
  data/repo/src/main/kotlin/.../
    VoicePreferencesRepository.kt    (new)
  data/db/.../migrations/            (new Room migration)

hermes-companion-gateway/
  server/routes/
    voice.py                         (new)
  server/services/
    voice_bus.py                     (new)
    tts_proxy.py                     (new)

~/.hermes/skills/companion-voice/
  SKILL.md                           (new)
```

No files in T7/T8 are modified. Voice is additive.