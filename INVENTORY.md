# Hermes Companion — Inventory Report

**Scope:** Discovery only. No code, no design, no recommendations.
**Repo (Android app):** `/Users/nyx/Projects/hermes-companion` on mac-mini, branch `feat/voice-tab-and-visual-port` (tip `2f45035`).
**Repo (gateway):** `/home/nyx/.hermes/profiles/coder/home/Projects/hermes-companion-gateway`.
**App version:** `v0.2` (2026-08) per `CHANGELOG.md`. `v0.1.x` was single-gateway PoC.

The app is a Kotlin Multi-module Android project:
`:app`, `:core:domain`, `:core:common`, `:transport:auth`, `:transport:hermes`, `:transport:broker`, `:transport:discovery`, `:data:db`, `:data:repo`, `:node`. Compose Material 3 + Hilt + Room + OkHttp + aiohttp gateway on the other side.

---

## 1. UI — Tabs and screens

Bottom-nav `Shell` (5 tabs, in `ui/shell/Shell.kt`). Rail on wide layouts. Route capsule sits above tabs and opens the `FleetSwitcher`.

| Route | File | What it does |
|---|---|---|
| Chat (default) | `ui/chat/ChatHome.kt` + `ChatScreen.kt` | Empty state if no thread selected; otherwise renders `ChatScreen` for the active `(gateway, profile, session)` route. `RouteCapsule` opens the `FleetSwitcher`. |
| Agents | `ui/agents/AgentsScreen.kt` | Lists gateways → profiles → threads; new-thread dialog; "active" pill; opens chat. |
| Activity | `ui/activity/ActivityScreen.kt` | Filterable stream of `ActivityItem`s (Notification / Call / Job / ChatRun), `QueueSummary` per gateway. |
| Node | `ui/node/NodeScreen.kt` | This phone's node state: capabilities (filter chips by `CapabilityStatus`), battery mode, link type, leases, privacy log, canary. Routes into setup / grants / stream rules. |
| Settings | `ui/settings/SettingsScreen.kt` | 4-tab `PrimaryTabRow`: Gateways, Profiles, Routing, Voice. Banners above tabs route to Outbox, Discover, Diagnostics, Appearance. |

Sub-routes pushed from Settings / Node (full-screen Composables):

| Route | File | What it does |
|---|---|---|
| NodeSetup | `ui/setup/NodeSetupScreen.kt` | Full Node Mode setup ladder (`SetupRung`s) with requirements and deep-links into Android settings. |
| NodeGrants | `ui/node/NodeGrantsScreen.kt` | Per-`(gateway, profile, node, capability)` grants with mode (Ask / While unlocked / Until / Deny). |
| StreamRules | `ui/node/StreamRulesScreen.kt` | Per-source stream rules (likely for screen capture / accessibility streams). |
| Discover | `ui/discover/DiscoverScreen.kt` | Add gateway by URL / via NSD-mDNS; on add pops back. |
| Diagnostics | `ui/diagnostics/DiagnosticsScreen.kt` | Fleet + node state + pairings rendered; for support. |
| Appearance | `ui/settings/AppearanceScreen.kt` | Theme mode + dynamic color toggle (its own `AppearanceViewModel` over `ThemePrefs`). |
| Outbox | `ui/outbox/OutboxScreen.kt` | Inspect / retry / drop queued + in-flight + ack-pending outbound submissions. |
| Chat (deep) | `ui/nav/Route.kt:Chat` pattern `chat/{gatewayId}/{profileId}/{sessionId}` | The deep-link into a specific thread. |

Shell pieces worth knowing:
- `RouteCapsule` (`ui/shell/RouteCapsule.kt`, 61 lines) — the header that shows the current `(gateway → profile → thread)` chain and opens the `FleetSwitcher`.
- `FleetSwitcher` (`ui/shell/FleetSwitcher.kt`, 113 lines) — picker modal for `ConversationRoute`.
- `AskHermes.pending` (`ui/nav/AskHermes.kt`) — mutable singleton holding a pending prompt string for cross-screen hand-off.
- Chat sub-components: `ProfileTabStrip.kt` (chip strip of `(gateway × profile)`), `ApprovalSheet.kt` (request-bound approvals), `ToolRunCard.kt` (renders one `ToolRun`).

There are **placeholder top-level routes** declared in `Route.kt` (`Home`, `Hermes`, `Shade`, `Device`, `More`) matching "the master five-tab shell from the web-app". They are **not wired into `Shell.kt`** — the comment says they exist for future AskHermes deep-links and a Shade screen.

---

## 2. Domain model

All entities live in `core/domain/src/main/kotlin/com/hermes/companion/domain/`.

**Routing keys:**
- `ConversationRoute(gatewayId, profileId, sessionId)` — every chat / session op is keyed on this triple.
- `NodeRoute(conversation, nodeId, capability, requestId)` — node command routing extends it.
- `ProfileHandle(profileId, display)` — disambiguated `@ash-home` / `@ash-lab` for same-name profiles across gateways; display only, never used as a key.

**Identity / discovery:**
- `GatewayConnection(id, label, kind, baseUrl, authRef, health)` + `GatewayKind { Local, RemoteHttp, SshTunnel, CloudOAuth }` + `GatewayHealth { Unknown, Healthy, Degraded, Down }`.
- `AgentProfile(gatewayId, profileId, displayName, handle, capabilities, multiplexed)` — one isolated Hermes `HERMES_HOME` per `(gateway, profile)`.
- `AndroidNode(nodeId, deviceName, keyId, state, capabilities)` + `NodeState { Paired, Connected, Disconnected, Revoked }`.

**Sessions / chat:**
- `Session(sessionId, profileId, gatewayId, title, modelLock, runState, unreadCount)` + `RunState { Idle, Streaming, AwaitingApproval, Completed, Failed }`.
- `Message` sealed: `User` and `Assistant` (the latter carries `toolRuns: List<ToolRun>` and `isStreaming`). `ToolRun(id, name, status, input, output?, startedAt, completedAt?)` + `ToolStatus { Pending, Running, Completed, Failed }`.
- `RunEvent` sealed: `AssistantDelta`, `ToolStarted`, `ToolCompleted`, `ApprovalRequired`, `RunCompleted`, `RunFailed` — the SSE event surface.
- `Submission` — outbound submission (used by the outbox).
- `ApprovalRequest(requestId, runId, profileId, gatewayId, command, digest)` with the four locked options: `Once | Session | Always | Deny`.
- `QuestionRequest` — placeholder for conversational clarification questions.

**Node / capabilities:**
- `NodeCapability` enum (`AndroidNode.kt`) — the full id set: `NotificationsRead`, `NotificationsActive`, `NotificationsActions`, `CallsObserve`, `CallsLog`, `ContactsLookup`, `SmsRead`, `DeviceStatus`, `AppUsage`, `LocationRead`, `CalendarEvents`, `MotionActivity`, `MotionPedometer`, `PhotosLatest`, `DeviceHealth`, `DevicePermissions`, `DeviceApps`, `ClipboardRead`, `MediaSessionRead`, `ScreenCapture (exclusive)`, plus mutating: `NotificationsDismiss`, `NotificationsAction`, `NotificationsReply`, `CallsAnswer`, `CallsHangup`, `CallsPlace`, `ContactsCreate`, `SmsSend`, `LocationShare`, `AppLaunch`, `ShellExec`, `ClipboardWrite`, `MediaSessionControl`. Each has `family`, `mutating`, `exclusive` flags.
- `CapabilityGrant(gatewayId, profileId, nodeId, capability, mode, expiry?, policy?)` + `GrantMode { AskEveryTime, AllowWhileUnlocked, AllowUntil, Deny }` — scoped per `(gateway, profile, node, capability)`.
- `Lease(capability, gatewayId, profileId, requestId, acquiredAt, expiresAt)` — exclusive-capability lease; primary key IS the capability, so mutual exclusion is a uniqueness constraint, not a lock.
- `CapabilityHealth` — health report for a capability.

**Transport / wire:**
- `BrokerFrame` sealed: `NodeEventFrame (phone → gw)`, `CommandFrame (gw → phone)`, `CommandReceiptFrame (phone → gw)`, `PresenceFrame`. Versioned, deduped on both sides.
- `NodeCommand(requestId, capability, profile?, params, grantId?, expiresAt?)` + `Receipt(requestId, capability, status, detail, payload, at)` + `ReceiptStatus { Accepted, Progress, Completed, Failed, Refused }` + `SendOutcome { Acked, Unacknowledged, Refused }`.
- `TransportTier { Full, Limited }` — derived per connect from TLS / loopback / `.local` / cleartext-other; gates which endpoints a connection may ever use.
- `RedactionLevel` — output redaction levels.
- `NotificationAction` enum: `Off | All | ImportantOnly | Mute | ReplyWithRules` — five-value router decision (locked by v0.2 grill).

**Common / config (`core/common`):**
- `ActiveGatewayConfig` — file-backed active-gateway config (read by NLS).
- `VoiceConfig` — voice prefs (provider, voice, speed, language, etc.).
- `BiometricGate` — biometric wrapper.
- `Failures` — common error shapes.

**Persistence (`data/db/Entities.kt`):** `gateways`, `profiles (gatewayId, profileId)`, `sessions (gatewayId, profileId, sessionId)`, `messages`, `runs (gatewayId, profileId, sessionId, runId)`, `outbound` (the outbox), `node_identity`, `grants (gatewayId, profileId, nodeId, capability)`, `leases`, `stream_rules`, `active_gateway (singleton table)`.

**UI projection models (`data/repo/Models.kt`):** `Fleet`, `GatewayView`, `ProfileView`, `DiscoveredGatewayItem`, `DiscoveryUiState`, `RunView`, `ConversationState`, `ActivityItem` + `ActivityKind { Notification, Call, Job, ChatRun }` + `ActivityOutcome { Notified, Suppressed, Completed, AwaitingApproval, Failed, Streaming }`, `QueueSummary`, `ActivityState`, `NodeCapabilityItem` + `CapabilityStatus { Working, MissingPermission, OsLimited, Unavailable }`, `HardwareLease`, `PrivacyLogEntry`, `StreamRuleItem`, `NodeGrantItem`, `NodePairing`, `SetupRung`, `NodeState`, `OutboxItem`, `OutboxState`, `Connectivity`.

---

## 3. Wire protocols

Two protocols live side by side on the gateway.

### A. Companion v1 (long-poll / simple JSON)
Source: `hermes-companion-gateway/hermes_companion/companion_routes.py`. Mirrors the original `/tmp/hermes-companion-plugin/companion/adapter.py` format. Used by simple phone-side companions and for backwards compat. Routes are mounted via `mount_companion(app)`:
- `GET /companion/hello` — discovery: returns `{host, port, version, tailscaleIp?, brokerUrl?, setupCode?, ...}`.
- `POST /companion/pair` — exchange a one-time 6/8-char setup code for a session token.
- `POST /companion/inbox` — phone → Hermes: relay a message (with optional media / OTPs).
- `GET /companion/outbox` — Hermes → phone (long-poll, 8 s timeout).
- `OPTIONS /companion/{tail:.*}` — CORS preflight.

### B. Hermes-Companion native protocol (used by `:app`)
Source: `hermes-companion-gateway/hermes_companion/server.py` (aiohttp `web` routes mounted at line 490):
- `GET /health` — liveness.
- `POST /pair` — Ed25519 setup-code pairing (the Android `CompanionLink` exchanges a one-time setup code for a long-lived node identity keypair).
- `GET /ws/node` — bidirectional WebSocket broker carrying `WireFrame` JSON (`BrokerFrame`s above: `NodeEventFrame`, `CommandFrame`, `CommandReceiptFrame`, `PresenceFrame`). Heartbeat 30 s. Deduped by `(nodeId, eventId)` for events and `(nodeId, requestId)` for commands.
- `POST /node/{node_id}/invoke` — HTTP-side alias for sending a capability command.
- `GET /node/{node_id}/events` — HTTP-side stream of node events (alternative to WS).
- `GET /node/{node_id}/screen` — frame-grab for screen capture.
- `GET /status` — overall status.
- `GET /admin/setup-code`, `POST /admin/rotate-setup-code` — Ed25519 setup code admin.
- `GET /admin/companion-setup-code`, `POST /admin/rotate-companion-setup-code` — the companion-plugin 6/8-char setup code admin.
- `POST /admin/revoke/{node_id}` — revoke a node.
- `GET /admin/` — admin index.
- `POST /v1/notifications/incoming` — relay a notification from the phone to Hermes (NLS wires to this).
- `POST /v1/notifications/reply` — send an SMS / IM reply (T6 follow-up; OTP-reply pipeline).
- `POST /v1/voice/synthesize` — TTS.
- `POST /v1/voice/transcript` — STT.
- `GET /api/profiles` — agent list per gateway (multiplexed vs single-profile shape).
- `GET /api/profiles/{profile_id}/sessions` — sessions for a profile.
- `POST /admin/profiles` — admin override for the profile list.

### C. Hermes upstream (called *by* `:app` via `HttpHermesBackend`)
Paths visible in `transport/hermes/src/main/kotlin/com/hermes/companion/net/HttpHermesBackend.kt`:
- `GET /v1/capabilities` (or `/p/{profile}/v1/capabilities` when multiplexed).
- `GET /api/profiles` — same shape as the gateway admin route.
- `GET /api/sessions`, `POST /api/sessions`, `GET /api/sessions/{id}/messages`.
- `POST /v1/runs` — start a run, returns `{run_id}`.
- `GET /v1/runs/{id}/events` (SSE) — the run event stream.
- `POST /v1/runs/{id}/stop`.
- `POST /v1/runs/{id}/approval` — submit an approval decision.

There's a parallel `mock-server/server.mjs` (Node) that speaks the Hermes contract (`/v1/capabilities`, `/p/<profile>/v1/capabilities`, `/api/profiles`, `/api/sessions`, `/v1/runs`, `/v1/runs/<id>/events`, `/v1/runs/<id>/stop`, `/v1/runs/<id>/approval`, `/api/model/options`) for offline / CI testing.

---

## 4. Reusable assets — `HermesComponents.kt` and theme

### `ui/components/HermesComponents.kt` (Phase-2 visual port)
- `Caduceus(modifier, color)` — the Hermes staff-of-asclepius brand mark.
- `HermesMark(size)` — composed logo mark.
- `StatusBadge(text, tone)` — pill with one of `BadgeTone { Muted, Live, Warn, Danger, Solid }`.
- `SurfaceCard(content)` — branded card surface.
- `SectionLabel(text, action?)` — section header with optional action slot.
- `HermesButton(...)` — primary button (defaults to compact, supports icon + label).
- `HermesField(value, onChange, label, helper?, leading?, error?)` — themed text field.
- `ToggleRow(label, checked, onChange, ...)` — settings row with toggle.
- `LevelRow(label, value, onChange)` — slider row for levels.
- `QuickTile(title, subtitle, glyph, onClick)` — tile for command surfaces.
- `Chip(label, onClick)` — generic chip.
- `LinkText(label, onClick)` — text-button styled as link.

### `ui/components/Components.kt` (older, still in use)
- `HermesCard(content)` — base surface card.
- `SectionHeader(text)`.
- `MetaText(text, color?)` — small secondary text.
- `StatusDot(color, size?)` — colored dot (paired with `StatusOk / StatusWarn / StatusError / StatusDim`).
- `TierChip(label, color)` — connection-tier pill.
- `EmptyState(title, body)` — empty-state block.

### `ui/theme/`
- `Color.kt` — `Indigo`, `Teal`, `Sand`, `Coral` palettes (90/80/40/20 shades), full `Night…` and `Day…` Material 3 surface palette, status colors (`StatusOk / StatusWarn / StatusError / StatusDim`), `HermesColors` (Indigo20/40/80/90, etc.), `HermesStatusColors` (light + dark).
- `Theme.kt` — `HermesTheme` composable, `LocalHermesStatus` composition local for status colors.
- `Type.kt` — `HermesTypography` (Display/Headline/Title/Body/Label all sized), `HermesMono`, plus Figtree / InstrumentSerif / PlexMono `FontFamily`s, `HermesType` object, individual DisplayLarge/Medium exports.

These are theme-agnostic and ready to be reused in any new ChatGPT-style surface; the existing screens already lean on `HermesCard`, `HermesButton`, `StatusBadge`, `Caduceus`, `QuickTile`, `EmptyState`.

---

## 5. Skill files

No `SKILL.md` exists anywhere in the companion repo or the gateway repo. Project-level conventions are documented in `Hermes-Companion-Plan.md`, `plan/`, `docs/`, `CHANGELOG.md`, and inline docstrings on each module.

---

## 6. Constraints and gotchas spotted

**Architecture / scope:**
- Five-tab shell is **hardcoded** in `Shell.kt`. The web-app's "Home, Hermes, Shade, Device, More" tab set is *declared* in `Route.kt` but *not wired*. Any redesign that swaps the shell must touch both files.
- `ChatHome` and `ChatScreen` are *two composables with the same content*: `ChatHome` is the tab destination, `ChatScreen` is the deep-link target. `RouteCapsule` is shared chrome above them. A redesign that puts ChatGPT-style conversation at the root probably wants one of these, not both.
- `AskHermes.pending` is a **mutable global** (`object AskHermes { var pending: String? = null }`) for cross-screen prompt hand-off — fragile if more than one screen pushes prompts concurrently.

**Routing:**
- Routing key is always `(gatewayId, profileId, sessionId)`. The repo has *one active gateway at a time* and *one active profile per gateway* (`active_gateway` singleton table). Switching gateways **must** invalidate visible sessions/jobs/grants, per `Hermes-Companion-Plan.md §3`.
- `ConversationRepository` and `FleetRepository` are the only UI-facing data interfaces. They never throw — errors arrive as data on `ConversationState.activeRun.error` or `Connectivity.reasonOrNull`.

**Chat / runs:**
- A `Run` outlives the screen that started it (`HermesBackend.submit` returns run id; observation is `runEvents(route, runId)`). `ChatViewModel` does **not** own the run — leaving Chat no longer cancels it.
- SSE-driven streaming (`HttpHermesBackend` parses SSE via `SseParser.kt`). The ChatGPT-style redesign probably wants to keep SSE for backend parity, but a single persistent run-stream per session (not per screen) is the model.
- Approval requests have exactly the four options `Once / Session / Always / Deny` — the redesign must not introduce a fifth without a grill.

**Node / capability surface:**
- The node surface is large: ~40 `NodeCapability` values across read-only and mutating; ~10 capability *families*; exclusive capabilities (e.g. `ScreenCapture`) take a lease; mutating ones require a request-bound grant + approval. `HermesComponents.kt` has `QuickTile` which was clearly built for command/quick-action surfaces.
- Notification routing has **five locked actions** (`Off / All / ImportantOnly / Mute / ReplyWithRules`). Important-allowlist = WhatsApp, Telegram, Slack, Discord, Signal, Google Messages, Phone, android shell. Router is wired but the NLS doesn't call it yet (per CHANGELOG known gap).
- `HermesNotificationListenerService` exists; `NotificationForwarder` pushes to the gateway; the T6 follow-up (notifications → Hermes) is not yet live.

**Auth / pairing:**
- Two pairing flows coexist: (1) Ed25519 setup-code pairing (`/pair` + `/ws/node`) is what `:app` uses; (2) the legacy 6/8-char `/companion/pair` long-poll flow coexists for the original companion plugin. Both can be active on the same gateway.
- `:transport:auth` owns `KeystoreTokenStore`, `NodeIdentityKey`, `SignedRequestFactory` — OkHttp and `:transport:auth` are deliberately `implementation` deps of `:transport:hermes`, not exposed to `:app`.

**Persistence:**
- Room DB v7. v6 → v7 was destructive (no migration SQL). Anything that touches `CompanionDatabase` should preserve that.
- `NotificationRuleRepository` is **in-memory only** in v0.2 — rules reset on app restart. v0.3 will move to Room.

**Theming:**
- Two theme palettes (Night/Day), `HermesColors` exposes the branded Indigo / Teal / Sand / Coral tones. `HermesTheme` is already M3 and respects dynamic color (off by default; toggleable in Appearance). Reuse it for any new surface.

**Background services:**
- `HermesAccessibilityService` and `HermesNotificationListenerService` are wired. `ScreenWakeLock` is acquired while a node task is active (10-min watchdog). `NotificationForwarder` is the gateway-bound POST.
- Shizuku + elevated shell path lives in `node/elevated/` (`ShizukuGateway`, `ShellAllowlist`, `SilentGranter`, `RootDetector`, `ElevatedShell`) — only relevant if redesign exposes a "Full Node Mode" surface.

**Test posture:**
- `core:domain` has unit tests (`RoutingKeyTest`, `VoiceConfigTest`).
- `data:db` has DAO + migration tests; `data:repo` has BackendRegistry / ConnectionSupervisor / GatewayRowForPair / NotificationRuleRepository / Redactor / FakeStore / NodeConnectionManager / NodeGrants / Repository tests.
- `:app` has `ChatViewModelTest` (bind / empty-send / trim / clear-draft). No Compose UI tests yet — T4.5 in CHANGELOG.
- `transport:broker` has `WebSocketNodeBrokerTest`; `transport:discovery` has `TransportTiersTest`; `transport:auth` has `AuthTest`; `transport:hermes` has `MockHermesBackendTest`, `SseParserTest`, and live integration `HttpHermesBackendLiveTest` / `HttpHermesBackendStreamTest`.
- `node` tests cover `AdapterRegistry`, `CompanionDiscovery`, `CompanionLink`, `NotificationRouter`, `ScreenWakeLock`, `ShellAllowlist`, `CloudTtsClient`, `NotificationForwarder`.

---

## 7. Files of interest (copy-paste ready)

- `Hermes-Companion-Plan.md` — canonical product + architecture thesis.
- `plan/01-product`, `plan/02-contracts`, `plan/03-android`, `plan/04-connection`, `plan/05-reliability`, `plan/06-ux`, `plan/07-privacy`, `plan/08-delivery`, `plan/09-parity`, `plan/10-architecture` — full plan tree.
- `design/*.dc.html` + `design-plan/*.dc.html` — design-canvas HTML mocks for every screen (Chat, Main, NodeLive, NodeSetup, PairNode, AddGateway, Approval, Composer, Delivery, Diagnostics, Discover, Files, GatewaySettings, Grants, Jobs, NewThread, Outbox, Switcher, FullNodeMode, hermes-companion-concept, hermes-companion-blueprint).
- `docs/install.md`, `docs/voice-control-plan.md`, `docs/voice-control-design.html`.
- `transport/broker/Protocol.kt` (`WireFrame`), `transport/hermes/backend/HermesBackend.kt`, `core/domain/RoutingKey.kt`, `core/domain/BrokerFrame.kt`, `data/repo/Repositories.kt` — canonical wire / contract / UI-boundary specs.
- `hermes-companion-gateway/hermes_companion/server.py` (line 490 onward) — gateway route table.
- `hermes-companion-gateway/hermes_companion/companion_routes.py` — legacy companion-plugin routes.
