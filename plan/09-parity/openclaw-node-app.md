# Parity Record: OpenClaw Android Node App

Source: comparison against OpenClaw's shipped Android companion node app, reviewed 2026-08-23 against <https://docs.openclaw.ai/platforms/android>. OpenClaw released iOS and Android companion node apps in June 2026; this plan predates them. Nothing here is a source for our contracts — it is a record of what a shipped app in the same shape already does, and what we decided in response.

## Adopted

- **Gateway discovery** — mDNS/NSD on the local network, wide-area DNS-SD across a tailnet, manual host/port as the fallback. See [04-connection/gateway-registry.md](../04-connection/gateway-registry.md).
- **Cleartext as a privilege tier** — plaintext `ws://` off the loopback earns limited access, never node capabilities. Same doc.
- **Rate ceiling on forwarded events** — a per-device events/minute cap, so one chatty package cannot flood a gateway. See [03-android/notification-correctness.md](../03-android/notification-correctness.md).
- **Never-forwarded packages** — Companion's own package, and any app Hermes already reaches you through. For us that is Telegram; OpenClaw's list is longer because it has native channel sessions for WhatsApp, Telegram, Discord and Signal. Ours must not grow past what Hermes actually serves natively — §6.2 requires WhatsApp and Cliq to be forwarded.
- **Outbound durable sending** — an outbox for what the operator sends, not only for what the device observes. See [05-reliability/offline-behavior.md](../05-reliability/offline-behavior.md).
- **Agent questions distinct from approvals** — a question picks a branch; an approval grants a capability. Single/multi-select with option descriptions, a free-text alternative, an expiry, and first-answer-wins locking across devices.
- **Read-only workspace browsing** — the agent writes, the phone looks. Directory listing, text and image previews, export through the system share sheet, previews capped by the gateway.
- **Four input paths** — typing, on-device dictation, a voice note carried as audio, and realtime Talk holding the microphone through the foreground service. Mutually exclusive; Talk requires promoting the foreground service before capture. Plus playback of an assistant message.
- **Per-gateway isolation surfaced as an action** — a Forget that removes credentials, TLS trust and cached history for one gateway, and node revocation scoped to one gateway.

## Decided differently

- **Exclusive-capability arbitration.** OpenClaw designates one *focused* gateway that exclusively owns the node session and every device capability. We keep all gateways connected with their own grants (§3, §11) and lease exclusive hardware — camera, microphone, screen capture — to one holder at a time with a short expiry and a named holder. Read-only observation fans out to every granted route; only exclusive hardware is leased. See [02-contracts/capability-groups.md](../02-contracts/capability-groups.md).
- **Capability surface.** We keep the mutating families OpenClaw does not expose (`notifications.reply`, `calls.answer/reject/dial`, `apps.launch`, `clipboard.*`, `media.session.*`, `screen.input`) because Full Node Mode is the point of this project. We adopt the read families we lacked.

## Deferred

- **Wear OS companion.** OpenClaw ships one (select agent and session, bounded transcript, dictated reply, abort a run, start Talk, credentials never leaving the phone). Revisit after Slice C; not added to [../SCOPE.md](../SCOPE.md) as either in or out until then.
- **App Actions / assistant entry** — "ask Companion `<prompt>`" prefilling the composer without auto-send. Cheap, but it needs a manifest capability and a decision about which route an assistant-launched prompt lands in.

## Rejected

- **`canvas.*`** — an OpenClaw primitive with no Hermes equivalent. Revisit only if Hermes grows a canvas surface.
- **scrcpy remote control** — an operator-side workflow over ADB, outside the app's trust model.

## See also

- [02-contracts/capability-groups.md](../02-contracts/capability-groups.md)
- [03-android/notification-correctness.md](../03-android/notification-correctness.md)
- [04-connection/gateway-registry.md](../04-connection/gateway-registry.md)
- [05-reliability/offline-behavior.md](../05-reliability/offline-behavior.md)
- [08-delivery/production-slices.md](../08-delivery/production-slices.md)
