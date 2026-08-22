# Hermes Companion — Android PoC

Android-first mobile companion for the Hermes agent fleet. Status: **PoC scaffold**, buildable + tests passing. Not yet wired to a real Hermes gateway; uses an in-process mock backend per the plan's PoC scope.

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

- Real Hermes HTTP API client (only the in-process `MockHermesBackend`)
- Room persistence / outbox / per-route ack watermarks (state lives in memory)
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

18 tests across:

- `domain/RoutingKeyTest` — structural equality of conversation and node routes
- `backend/MockHermesBackendTest` — capability discovery, profile seeding, session listing, run event sequence, approval gating, route validation
- `backend/BackendRegistryIsolationTest` — multi-gateway fleet, profile handle disambiguation, selection isolation across gateway removal

## Layout

```
hermes-companion/
├── Hermes-Companion-Plan.md        # consolidated original
├── plan/                           # decomposed sub-plans (read these)
├── README.md                       # this file
├── settings.gradle.kts             # Gradle settings + repos
├── build.gradle.kts                # root build (AGP, Kotlin, serialization plugin)
├── gradle.properties               # JVM args, AndroidX flags
├── gradle/wrapper/                 # Gradle 8.7 wrapper
├── gradlew, gradlew.bat            # wrapper scripts
└── app/
    ├── build.gradle.kts            # AGP 8.5.2, Kotlin 1.9.24, Compose 2024.06
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/hermes/companion/
        │   │   ├── CompanionApp.kt
        │   │   ├── MainActivity.kt
        │   │   ├── domain/        # object model (§2)
        │   │   ├── backend/       # HermesBackend + MockHermesBackend + registry
        │   │   └── ui/
        │   │       ├── theme/     # Material3 colors/typography
        │   │       ├── nav/       # Route sealed class
        │   │       ├── shell/     # responsive bottom nav / nav rail
        │   │       ├── agents/    # gateway → profile → session list
        │   │       ├── chat/      # streaming + tool cards + approval sheet
        │   │       ├── settings/  # gateway manager
        │   │       └── node/      # PoC placeholder
        │   └── res/               # strings/themes/colors/icons
        └── test/java/com/hermes/companion/
            ├── domain/
            └── backend/
```

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

- Replace `MockHermesBackend` with an HTTP-backed `HermesBackend` once the real contract is pinned
- Add Room + per-route ack watermark
- Wire `NotificationListenerService` + `getActiveNotifications()` reconciliation
- Implement node pairing UI (Ed25519 Keystore keypair + QR/deep-link)
- Add a hermes-companion plugin to the OpenClaw/Hermes gateway side
