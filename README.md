# Hermes Companion — Android

Android-first mobile companion for the Hermes agent fleet. Current product branch: **`feat/completion`** (v0.2). Looks like the [web companion](https://github.com/coding-nyx/hermes-companion-web).

Talks on **two planes**:

| Plane | What | Where |
| --- | --- | --- |
| Hermes chat | profiles, runs, SSE | Hermes gateway (`HttpHermesBackend`) |
| Companion node | pair, device caps, shade forward | [companion plugin](https://github.com/coding-nyx/hermes-companion-plugin) `:8642` |

On the phone: **Node → Pair as node** with `http://<magicdns>:8642` and the plugin setup code. Do not point that dialog at the chat mock (`:9120`).

The look is the web tokens (ink, cream type, caduceus). Navigation stays Chat / Agents / Activity / Node / Settings.

See `plan/` for the decomposed product and architecture plan. See `Hermes-Companion-Plan.md` for the consolidated original.


## What's in this PoC

Per `plan/08-delivery/poc-scope.md` — the first vertical slice:

- **Two gateways with multiple profiles** — `gw-home` (ash, misty) and `gw-cloud` (ash, work), exercised by `MockHermesBackend.defaultFleet()`
- **Gateway → profile → session switching** — `AgentsScreen` renders the full hierarchy
- **State isolated per route** — every backend call carries `ConversationRoute`; `MockHermesBackend.ensureRoute` rejects cross-gateway session ids
- **Chat send and structured mock tool run** — `ChatScreen` + `ToolRunCard`; `MockHermesBackend.sendAndStream` emits `ToolStarted → ToolCompleted → AssistantDelta* → RunCompleted`
- **Node actions and simulated notification/call events** — capability model + node screen scaffold (`plan/03-android/full-node-mode.md` covers the production checklist)
- **Request-bound approval decisions** — `ApprovalSheet` with the exact `once / session / always / deny` choices
- **Gateway manager/test interaction** — `SettingsScreen` + `AddGatewayDialog` adds/removes mock gateways at runtime
- **Responsive mobile and desktop layouts** — `Shell` switches between bottom `NavigationBar` (compact) and side `NavigationRail` (medium+) via `material3-window-size-class`

## What's deliberately out (PoC scope cuts)

- Room persistence / outbox / per-route ack watermarks (state lives in memory)
- Boot-time start of the connection service (needs a receiver; today it starts from a visible Activity)
- Transport privilege tiers and gateway discovery
- `NotificationListenerService`, `CallScreeningService`, `InCallService` (real Android node plumbing)
- `Hermes-companion` plugin and node broker WebSocket
- Hermes approval canonicalization, Ed25519 pairing, Keystore token envelopes
- iOS companion (out of project scope)

## Build

```bash
cd /Users/nyx/Projects/hermes-companion
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk` (~16 MB).

## Test

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest
```

48 tests across:

- `domain/RoutingKeyTest` — structural equality of conversation and node routes
- `transport/hermes` `MockHermesBackendTest` — capability discovery, profile seeding, session listing, run event sequence, approval **resumption** and denial, route validation
- `data/repo/BackendRegistryIsolationTest` — multi-gateway fleet, profile handle disambiguation, selection isolation across gateway removal
- `net/SseParserTest` — spec conformance: single-space stripping, multi-line data, comments and heartbeats, truncated streams, early stop
- `net/HttpHermesBackendStreamTest` — run mapping order, no dropped deltas under a slow collector, cancellation releases the HTTP call
- `data/repo/ConnectionSupervisorTest` — backoff grows and is capped, a dead gateway retries without busy-looping, a healthy gateway settles into one probe per interval, one gateway failing does not slow another, forgetting a gateway stops its loop, and a successful probe resumes a run left open
- `data/repo/RepositoryTest` — an unreachable gateway is recorded not thrown, a dead gateway neither hides a live one nor loses its cached roster, one assistant message per run, a gated run's approval survives in storage and resumes after a decision, and a run is collected even when nobody observes it

## Layout

```
hermes-companion/
├── plan/                           # product, architecture and parity plans (read these)
├── design/                         # 25-screen concept: *.dc.html + canvas.json
├── mock-server/server.mjs          # local double for the Hermes API
├── core/domain/                    # pure Kotlin: routes, capabilities, events (no Android)
├── core/common/                    # small shared helpers
├── transport/auth/                 # credentials; exposes signed requests only
├── transport/hermes/               # HermesBackend port + HTTP/SSE impl + mock double
├── data/db/                        # Room: entities, DAOs, the CompanionStore facade
├── data/repo/                      # repositories, run tracker, supervisors, registry
└── app/                            # Compose UI and navigation only
```

Module boundaries are load-bearing, not cosmetic: `core/domain` uses the JVM
plugin so an Android import there is a compile error, and `transport/hermes`
keeps okhttp as an `implementation` dependency behind `httpHermesBackend()` so
the app cannot reach an HTTP type. `:app` resolves zero okhttp, transport or
Room entries on its compile classpath — it talks only to `:data:repo`.
See `plan/10-architecture/modules.md`.

## Source pointers back to the plan

| File | Plan section |
| --- | --- |
| `domain/RoutingKey.kt` | §2 (object model) |
| `domain/GatewayConnection.kt`, `AgentProfile.kt`, `Session.kt`, `AndroidNode.kt` | §2 |
| `domain/CapabilityGrant.kt` | §5.3 |
| `backend/HermesBackend.kt`, `MockHermesBackend.kt` | §4–5 |
| `backend/BackendRegistry.kt` | §3 (fleet hierarchy, isolation, disambiguation) |
| `ui/agents/*` | §10 (Agents lane) |
| `ui/chat/*` | §10 (Chat lane), §6.4 (event-processing correctness) |
| `ui/chat/ApprovalSheet.kt` | §10 (Approval UX), §6.2 (request-bound approvals) |
| `ui/settings/*` | §10 (Settings lane), §7 (gateway registry) |
| `ui/shell/Shell.kt` | §10 (responsive IA) |

## Known follow-ups

Ordered in `plan/10-architecture/migration.md`. Next up:

- Step 5: the outbound outbox, including the unacknowledged state
- Step 6: discovery (mDNS, wide-area DNS-SD) and transport privilege tiers
- Step 8: first real capability end to end (`notifications.read` + `device.status`)
- Step 8: first real capability end to end (`notifications.read` + `device.status`)
