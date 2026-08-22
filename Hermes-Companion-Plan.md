# Hermes Companion — Product and Architecture

Status: proposed product architecture and phased PoC plan; no implementation yet

## 1. Product thesis

Hermes Companion is not another chat transport. It is a first-party mobile surface for a fleet of Hermes agents and an optional Android node that gives explicitly-granted device capabilities to selected profiles.

Telegram remains useful as a fallback delivery channel. Companion removes its structural limits:

- first-class streaming tool/run UI instead of text-flattened progress;
- request-bound approval controls;
- persistent gateway → profile → session navigation;
- media/files/voice without bot-format compromises;
- structured jobs, node events, device state, and actions;
- reliable background delivery with acknowledgements and an offline outbox;
- explicit, inspectable device capability grants;
- many gateways and many profiles without sharing state.

## 2. Product object model

The app never addresses “Hermes” as one global singleton.

```text
GatewayConnection
  id, label, kind(local|remote|ssh|cloud), base_url, auth_ref, health
  └─ AgentProfile
       gateway_id, canonical_profile, display_name, handle, capabilities
       └─ Session
            session_id, title, model_lock, run_state, unread_count

AndroidNode
  node_id, device_name, key_id, state, capabilities
  └─ CapabilityGrant
       gateway_id, profile, capability, mode, expiry, policy
```

The routing key for conversation data is always:

```text
(gateway_id, profile_id, session_id)
```

The routing key for a node command adds the node and grant:

```text
(gateway_id, profile_id, node_id, capability, request_id)
```

A profile is not a workspace and not a sandbox. It is the `HERMES_HOME` state boundary. The Companion must preserve that boundary even when several profiles share one OS user or one gateway listener.

## 3. Fleet hierarchy

The UI follows the same hierarchy Hermes Desktop already documents:

```text
gateway → profile → session
```

- A gateway is a machine or hosted backend.
- A profile is an isolated Hermes agent on that gateway.
- A session is one transcript/run lineage under that profile.
- Same-name profiles across gateways receive a disambiguated handle such as `@ash-home`.
- Each `(gateway, profile)` has its own API client, run subscriptions, session cache, node grants, and unread count.
- Switching gateways cannot leave another gateway’s sessions, jobs, approvals, files, or capabilities visible.
- Cross-gateway delegation is explicit. It is never inferred from selecting two agents in the same app.

## 4. Existing Hermes contracts to reuse

The current Hermes API already supplies most of the chat/control plane:

| Need | Existing Hermes contract |
|---|---|
| Feature discovery | `GET /v1/capabilities` |
| Health/readiness | `GET /health`, `GET /health/detailed` |
| Session list/history | `/api/sessions`, `/api/sessions/{id}/messages` |
| Session chat | `/api/sessions/{id}/chat/stream` (SSE) |
| New agent run | `POST /v1/runs` |
| Run lifecycle | `GET /v1/runs/{id}/events` (SSE) |
| Stop/steer | `/v1/runs/{id}/stop`, `/steer` |
| Human approval | `/v1/runs/{id}/approval` |
| Stateful responses | `/v1/responses` + `previous_response_id` |
| Jobs | `/api/jobs` CRUD + pause/resume/run |
| Models | `/api/model/options` |
| Skills/toolsets | `/v1/skills`, `/v1/toolsets` |
| Multiple profiles on one gateway | `/p/<profile>/…` when `gateway.multiplex_profiles` is enabled |

A mobile client should capability-detect every connection. Do not assume all gateways run the same Hermes version.

Profile inventory can use the same remote dashboard/desktop RPC (`profiles.list`) that Hermes Desktop uses. If a standalone HTTP-only gateway does not expose inventory, Companion stores user-pinned profile names and probes each `/p/<profile>/v1/capabilities` path. A small companion plugin endpoint can later expose a bounded profile manifest without exposing profile secrets or filesystem paths.

## 5. New edge contract: Companion platform + Node RPC

Chat can use the API server directly. Proactive delivery and Android capabilities need one edge plugin, not core model tools.

### 5.1 Hermes plugin responsibilities

A user/profile plugin named `hermes-companion` should register:

1. a **platform adapter** named `companion` for proactive messages and cron delivery;
2. an **approval transport** for request-bound approve/deny UI;
3. a bounded **node broker** WebSocket/HTTP service;
4. optional commands (`/devices`, `/node`) and a bundled skill;
5. no broad core tool unless capability use genuinely needs model access.

The platform adapter normalizes app messages into ordinary Hermes `MessageEvent`s, preserving the gateway/profile/session routing key. Cron delivery can target `companion:<device>/<profile>/<session>`.

### 5.2 Node broker envelope

All frames are versioned and idempotent:

```json
{
  "v": 1,
  "type": "node.event",
  "event_id": "evt_01J…",
  "gateway_id": "gw_home",
  "profile": "ash",
  "node_id": "node_s22",
  "sequence": 8841,
  "sent_at": "2026-08-22T12:00:00+05:30",
  "capability": "notifications.read",
  "payload": {"package": "com.whatsapp", "title": "…", "preview": "…"}
}
```

Every mutating command carries `request_id`, `grant_id`, and an expiry. The node returns an acknowledgement and a terminal receipt:

```text
command.accepted → command.progress* → command.completed|failed
```

The broker deduplicates by `(node_id, event_id)` and `(node_id, request_id)`.

### 5.3 Capability groups

Read-only capabilities:

- notifications.read / notifications.active
- calls.observe / calls.log
- contacts.lookup
- messages.sms.read (only when Android role/permission permits)
- device.status (battery/network/thermal/storage)
- app.usage
- location.read
- screen.capture
- clipboard.read
- media.session.read

Mutating capabilities:

- notifications.dismiss/action/reply
- calls.answer/reject/dial
- messages.sms.send (only when Android role/permission permits)
- apps.launch
- intents.send
- clipboard.write
- media.session.control
- screen.input/accessibility
- files.read/write scoped through Android SAF grants
- camera.capture / microphone.record

Grants are per `(gateway, profile, node, capability)`, not global. High-impact capabilities can be `ask-every-time`, `allow-while-unlocked`, `allow-until`, or `deny`. Hermes approval policy remains authoritative; the app is a transport, not a policy bypass.

## 6. Android application

Recommended production stack:

- Kotlin + Jetpack Compose
- OkHttp/Ktor for HTTP, SSE, and WebSocket
- Room for gateway registry, session cache, **event outbox**, delivery receipts, and audit state
- WorkManager for bounded reconciliation/retry
- Android Keystore for device keys and encrypted token envelopes
- a user-visible foreground Node service for the persistent broker connection
- `NotificationListenerService` as the notification source of truth
- Telecom APIs, `CallScreeningService`, and `InCallService` with the required Android roles for complete call handling
- `ContactsContract` + `CallLog` providers under explicit permissions
- Storage Access Framework for explicit file grants
- MediaProjection for user-consented screen capture
- AccessibilityService only for the separately-enabled full-control capability
- FCM or UnifiedPush only as a wake hint; payloads remain encrypted and are fetched from the gateway

The renderer/UI process never receives raw gateway tokens. A connection service owns credentials and signs requests, mirroring Hermes Desktop’s main-process token boundary.

### 6.1 Full Node Mode

The app offers an explicit **Full Node Mode** onboarding path. “Full” means every capability Android exposes to an ordinary installed app after Nyx grants the required permissions, roles, and system settings; it does not pretend app sandboxing disappears.

The setup checklist is live and testable:

- Notification access enabled
- battery optimization set to unrestricted and Samsung background/autostart guidance completed
- contacts and call-log permissions granted
- call-screening role granted; default dialer role offered when answer/reject/full in-call control is wanted
- SMS role offered only when SMS read/send is wanted and the install channel permits it
- accessibility enabled only for screen/input automation
- MediaProjection consent present for each capture session unless Android supplies a durable grant
- microphone, camera, location, nearby-device, and file grants shown separately
- Tailscale/network reachability and TLS identity verified
- per-gateway and per-profile capability grants reviewed

The Node page displays a coverage matrix (`working`, `permission missing`, `OS-limited`, `temporarily unavailable`) rather than one misleading “connected” badge.

Optional advanced adapters are separate trust tiers:

- **Standard** — public Android APIs only
- **Accessibility** — UI inspection/input where Android permits
- **Shizuku/ADB** — elevated shell APIs with explicit setup
- **Device owner/root** — only on deliberately managed/rooted devices

No advanced tier is silently assumed or required for chat/notification reliability.

### 6.2 Notification correctness contract

ADB/logcat is not used for production notification ingestion. The Companion receives every app notification through `NotificationListenerService.onNotificationPosted()` and `onNotificationRemoved()`, including WhatsApp and Cliq, regardless of which Samsung log tag an app happens to emit.

On `onListenerConnected()` and every service restart, the app calls `getActiveNotifications()` and reconciles them against durable receipts. This closes the restart/race gap without waiting 30 minutes.

For each notification the Node records, before any model decision:

- stable event ID derived from user/profile, package, notification key, post time, and content revision;
- package, channel, category, conversation/shortcut ID, `UserHandle`, group/summary state;
- title, redacted preview, actions, remote-input/reply affordance, and post time;
- local policy result and content sensitivity class;
- outbox sequence and delivery status.

Importance judgment happens **after** the event is durably stored and acknowledged by the gateway. “Not important” may suppress a user ping; it must never erase evidence that the event arrived.

Self-notification loops are prevented structurally: events originating from the Companion’s own package/delivery ID carry an origin marker and are excluded before routing. Do not hard-code a profile display name such as “Ash” as the loop guard.

### 6.3 Call correctness contract

- `CallScreeningService` supplies incoming/outgoing screening context.
- `InCallService` plus default-dialer role is required for reliable answer/reject/end and active-call UI.
- CallLog reconciliation runs after calls finish so missed/rejected/blocked outcomes survive process death.
- Contacts lookup occurs locally first. Unknown callers use a configured lookup provider; the plan does not assume access to Truecaller’s private database.
- Every call event is durable and always routed to the granted profile(s), including contacts.

### 6.4 Event-processing correctness

A phone event is never handed directly to an untracked one-shot model call. The gateway first persists it, assigns it to an exact `(gateway, profile, node)` route, then creates an agent run with a durable run ID.

- Provider/model are resolved or explicitly pinned at execution time.
- Provider errors, quota exhaustion, and model drift keep the event pending and visible; they do not acknowledge or discard it.
- Retry uses bounded backoff and can fail over only through a configured Hermes model route.
- App Activity shows `captured → uploaded → acknowledged → judged → notified|suppressed|failed`.
- Nyx can open any suppressed item and inspect why it did not ping.
- A deterministic health test posts a local test notification, observes the listener callback, sends it through the broker, and verifies the profile receipt end to end.

## 7. Connection and pairing

### Gateway registry

Supported kinds:

- Local/LAN
- Remote HTTP(S) over LAN/Tailscale
- SSH tunnel
- Hermes Cloud/OAuth

Each connection stores a unique device label, normalized URL/SSH target, auth reference, last capabilities document, profile inventory, and last known health. Tokens live in Keystore-encrypted storage.

### Node pairing

1. User adds/tests a gateway.
2. App creates an Ed25519 node keypair in Android Keystore.
3. Gateway displays a short-lived QR/deep link containing gateway ID, pairing nonce, broker URL, and server fingerprint.
4. App proves possession of its private key and the nonce.
5. Gateway returns a node certificate/token bound to the public key and gateway.
6. User grants capabilities separately to profiles on that gateway.
7. Both sides show the same verification phrase before high-impact grants.

Use TLS even on Tailscale. Support certificate pinning as an opt-in, rotation-safe feature. Never put long-lived bearer tokens in QR codes.

## 8. Runtime data paths

### User message

```text
Compose UI
  → selected (gateway, profile, session)
  → POST /p/<profile>/api/sessions/<id>/chat/stream
  → SSE: assistant.delta / tool.started / tool.completed / run.completed
  → Room cache + UI
```

### Proactive agent message

```text
Hermes profile
  → companion platform adapter
  → encrypted push/wake + broker inbox
  → app receipt
  → correct gateway/profile/session unread lane
```

### Phone event

```text
Android service
  → local privacy policy + redaction
  → signed node.event
  → gateway broker dedupe
  → profile routing policy
  → agent event run or deterministic suppress
  → companion session/activity card
```

### Node action

```text
Agent or user action
  → canonical Hermes approval if required
  → broker command with capability grant
  → Android validates signature, grant, expiry, lock state
  → action
  → structured receipt rendered in the run
```

## 9. Reliability and offline behavior

- A notification/call callback is written to Room in the same local transaction that allocates its event ID and sequence; network dispatch comes later.
- Room-backed outbox with monotonic sequence per node and independent acknowledgement watermarks per gateway/profile route.
- At-least-once transport plus idempotent event/request IDs.
- Gateway acknowledges only after durable broker persistence, not after merely accepting a socket frame.
- Agent judgment acknowledges separately; a model/provider failure leaves the event in `failed/pending retry`, never `handled`.
- Explicit ack watermark from gateway; delete payload only after policy retention requirements are met.
- Reconnect resumes from last sequence and last run-event cursor.
- `NotificationListenerService.getActiveNotifications()` reconciles service restarts immediately; WorkManager is a watchdog for broken/unacked state, not the primary notification source.
- Detached runs remain visible via `/v1/runs/{id}` even if SSE drops.
- Reconcile checks only unacked/outstanding state and sends no “all clear.”
- Gateway outage does not merge routes. Each gateway shows its own stale timestamp and queue depth.
- The app may draft messages offline but labels them queued and shows the exact target `(gateway, profile, session)`.
- The diagnostics page can run an end-to-end canary per `(gateway, profile)` and reports the last successful notification callback, broker ack, model judgment, and Companion delivery.

## 10. UX information architecture

Primary mobile destinations:

1. **Chat** — transcript, streaming tool cards, attachments, voice, stop/steer.
2. **Activity** — calls, notifications, jobs, node events, receipts; filter by gateway/profile/node.
3. **Node** — live device state, capability grants, quick actions, privacy log.
4. **Agents** — union roster grouped by gateway; profile health/unread/busy.
5. **Settings** — gateways, auth, node pairing, delivery, privacy, diagnostics.

The active route is always visible as a compact gateway/profile capsule. Long-press or swipe opens the fleet switcher. Switching gateway restores that gateway’s last profile and session.

Approvals appear as structured sheets tied to a request digest, exact profile, gateway, and command. Choices are only those Hermes offered (`once`, `session`, `always`, `deny`).

## 11. Privacy model

- Event content is minimized on-device before transmission.
- Per-app notification policy: ignore, metadata only, redacted preview, full content.
- Sensitive categories (OTP, banking, health) default to metadata-only.
- Activity log shows who accessed which capability, under which profile, and why.
- Raw event bodies have bounded retention; receipts and audit metadata outlive payloads.
- Profile A cannot read Profile B’s node event queue without an explicit grant.
- A device can be revoked from one gateway without affecting another.
- Local biometric gate can protect app launch, approval, node controls, and secret-bearing views.

## 12. Proposed PoC scope

The first PoC should prove the interaction and routing model before any real device control:

- two gateways with multiple profiles;
- gateway → profile → session switching;
- state isolated per route;
- chat send and structured mock tool run;
- node actions and simulated notification/call events;
- request-bound approval decisions;
- gateway manager/test interaction;
- responsive mobile and desktop layouts.

The initial PoC should use a local mock server. It must not hold real Hermes tokens, invoke a production agent, or control the S22. Real Hermes API integration begins only after Nyx approves the interaction design and routing model.

## 13. Production slices

### Slice A — useful client (2–3 weeks)

- Compose shell, gateway registry, Keystore tokens
- profile discovery/multiplex routing
- sessions + chat/stream
- run cards, stop/steer, approvals
- images/files/voice

### Slice B — delivery channel (1–2 weeks)

- companion platform plugin
- proactive delivery, receipts, background notifications
- per-profile home channel
- deep links to exact route

### Slice C — Android node (3–5 weeks)

- pairing, node broker, Room outbox, receipts, and per-route ack watermarks
- Full Node Mode permission/role wizard and capability health matrix
- `NotificationListenerService` + active-notification restart reconciliation
- calls, contacts, call-log outcomes, and device status
- per-profile/per-gateway grants and audit log
- action receipts and model/provider failure queue
- deterministic end-to-end notification canary

### Slice D — high-impact control (incremental)

- screen capture/input, camera, microphone, file grants
- lock-state/biometric policies
- reliability, revocation, adversarial tests

## 14. Acceptance criteria for a production MVP

- Add at least two gateways and enumerate at least two profiles on one gateway.
- Same-name profiles across gateways are unambiguous.
- Stream a real Hermes run with tool progress, reconnect, stop, steer, and approve.
- Proactive delivery lands in the correct gateway/profile/session.
- Kill/restart the app, Node service, and gateway without losing a route or duplicating a user-visible message.
- Pair one Android node; enable Full Node Mode; show a truthful capability health matrix.
- Grant notifications to one profile and deny another; prove no cross-profile or cross-gateway leak.
- Receive real WhatsApp **and Cliq** notifications through `NotificationListenerService` at post time without using ADB/logcat.
- Restart `NotificationListenerService` with active notifications present; reconcile them immediately through `getActiveNotifications()` without a timed poll.
- Demonstrate calls from contacts and unknown callers, including missed/rejected/blocked outcomes after process death.
- Exhaust or disable the configured model/provider during an event: event remains pending/failed, is visible in Activity, and successfully retries after route recovery.
- Verify Companion’s own local notification cannot recursively create another agent event.
- Demonstrate one user-approved node action and one denied capability.
- Revoke the node and prove subsequent commands fail closed.
- No token, raw secret, or cross-profile event appears in renderer logs, crash logs, another profile, or another gateway.
