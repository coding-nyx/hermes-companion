# Module Graph

The single `:app` module of the PoC becomes a graph whose edges encode the guarantees in [README.md](./README.md). The point is not tidiness — it is that the token boundary, the "no ViewModel touches transport" rule, and "domain has no Android" are all compiler errors rather than review comments.

## Modules

```text
:app                      Compose host, navigation, DI wiring, manifest
:feature:agents           fleet roster, switcher, threads
:feature:chat             transcript, composer, approvals, questions
:feature:activity         event lanes, jobs, diagnostics
:feature:node             coverage matrix, grants, leases, stream rules
:feature:settings         gateways, delivery, biometric gates, files
:data:repo                repositories — the only thing features may talk to
:data:db                  Room entities, DAOs, migrations
:transport:hermes         HermesBackend over HTTP + SSE
:transport:broker         NodeBroker over WebSocket
:transport:discovery      mDNS/NSD and wide-area DNS-SD
:transport:auth           credentials; exposes signed requests only
:node:runtime             lease manager, redaction, rate limiter, dispatcher
:node:adapters            capability adapters over Android APIs
:core:domain              pure Kotlin — routes, capabilities, events, envelopes
:core:common              coroutine scopes, Clock, Result, logging, redacting log interceptor
```

## Dependency rules

| Rule | Enforced by |
| --- | --- |
| `:core:domain` has no Android dependency at all | JVM library plugin, not `com.android.library` |
| `:feature:*` may depend on `:data:repo`, `:core:*` — never on `:transport:*` or `:data:db` | dependency-check task in CI |
| Only `:transport:*` may depend on `:transport:auth` | dependency-check task |
| No public type in any module exposes a credential | `TokenEnvelope` and friends are `internal` to `:transport:auth` |
| `:node:adapters` depends on `:core:domain` and the Android SDK only | it must be testable without a gateway |
| `:data:db` is depended on only by `:data:repo` | dependency-check task |

The dependency-check task is a plain Gradle task that walks the resolved configurations and fails on a forbidden edge. Three rules above are unenforceable by visibility alone, which is why it exists rather than relying on discipline.

## Why features cannot see transport

In the PoC, `ChatViewModel` calls `backend.sendAndStream(...)` and collects it. That single edge causes the crash on an unreachable gateway, the duplicated assistant bubble, and the leaked socket, because the ViewModel is simultaneously the network client, the cache and the renderer. Cutting the edge forces the fix: transport writes to `:data:db`, features observe `:data:repo`. See [state.md](./state.md).

## Dependency injection

Hilt, replacing the `CompanionApp.get()` singleton and the hand-written `ViewModelProvider.Factory` in each ViewModel. What matters architecturally is the scoping, not the framework:

- **`@Singleton`** — Room, `TokenStore`, `LeaseManager`, `DiscoveryClient`, the adapter registry.
- **`GatewayScope`** — one `HermesBackend`, one `NodeBroker`, one supervisor and one `CoroutineScope` per gateway, created and torn down with the gateway registry entry. This is where per-gateway isolation is structurally guaranteed: two gateways cannot share a client because they cannot share a component.
- **`@ViewModelScoped`** — nothing that holds network or credential state.

## Keeping the PoC's shape where it was right

`domain/` moves to `:core:domain` unchanged — `ConversationRoute`, `NodeRoute`, `NodeCapability`, `CapabilityGrant` and the `RunEvent` hierarchy are already pure Kotlin and already carry the route. The `BackendRegistry` becomes the `GatewayScope` component set and keeps its isolation tests. What does not survive is `HermesBackend` as a single interface; see [transport.md](./transport.md).

## See also

- [state.md](./state.md)
- [security.md](./security.md)
- [migration.md](./migration.md)
