# Implementation Architecture

Derived from `../03-android/stack.md` (§6), `../05-reliability/offline-behavior.md` (§9), `../02-contracts/` and the decisions in `../09-parity/openclaw-node-app.md`. Where those documents state a requirement, this folder states how it is built.

## Settled decisions

Two forks were decided before these documents were written.

1. **One process, module-enforced token boundary.** The app runs in a single process. Gateway credentials live in `:transport:auth`, whose public API exposes only signed requests — no type carrying a token is reachable from a UI module, enforced by module visibility rather than by an OS process boundary. Chat streaming stays in-process, so there is no IPC cost per delta and one Room instance. The guarantee is compile-time, not runtime; [security.md](./security.md) states exactly what that does and does not buy.
2. **Real Hermes API first, node broker second.** The chat and control plane is built against `../02-contracts/existing-api.md`, which we do not control, capability-detected per gateway. `mock-server/` grows to match that API exactly and stays a faithful double. The node broker stays behind an interface with a fake until the `hermes-companion` plugin exists on the gateway side.

## Principles

These are the load-bearing ones. Each is testable, and each is violated by the current PoC.

- **The route is the unit.** Every stored row, every request, every subscription carries `(gateway_id, profile_id, session_id)`. Nothing is keyed globally, including caches and error state.
- **Durable before network.** Anything that must survive — an observed device event, an operator submission, a receipt — is written locally, with its identity and sequence allocated in the same transaction, before a socket is touched.
- **Room is the single source of truth.** Transport writes to the database; the UI observes the database. No ViewModel ever collects a network flow. This is what makes an unreachable gateway a rendered state instead of a crash.
- **Fail closed, and say who.** A denied capability, a held lease, an unverified transport tier and an exhausted model all resolve to a refusal that names the reason. Silence and silent retry are both wrong.
- **Capability-detect, never assume.** Gateways run different Hermes versions. A missing feature is hidden, not discovered by a failed tap.
- **Three outcomes, not two.** Sent, queued, and *unacknowledged*. The third is a first-class state everywhere it can occur.

## Documents

| File | Covers |
| --- | --- |
| [modules.md](./modules.md) | Gradle module graph and the dependency rules that enforce the boundaries |
| [runtime.md](./runtime.md) | Foreground service, per-gateway supervisors, reconnect, Doze and OEM survival |
| [data.md](./data.md) | Room schema, both outboxes, sequences, ack watermarks, retention |
| [transport.md](./transport.md) | `HermesBackend` and `NodeBroker` contracts, SSE, idempotency, capability detection |
| [capabilities.md](./capabilities.md) | Adapter registry, exclusive leases, redaction, rate limiting, permission mapping |
| [state.md](./state.md) | Repositories, per-route scoping, the UI state contract, error surfaces |
| [security.md](./security.md) | Token boundary, Keystore, pairing crypto, biometric gates, transport tiers |
| [testing.md](./testing.md) | What is tested where, and which acceptance criteria each layer proves |
| [migration.md](./migration.md) | Ordered steps from today's PoC, each ending buildable |

## Still open

- Database encryption at rest (SQLCipher with a Keystore-held key) is recommended in [security.md](./security.md) but not decided.
- Whether `:node:adapters:*` are separate modules per family or one module with internal packages. Deferred until the second adapter exists.
- Wear OS and the App Actions entry point, both deferred in `../09-parity/openclaw-node-app.md`.

## See also

- [../03-android/stack.md](../03-android/stack.md)
- [../09-parity/openclaw-node-app.md](../09-parity/openclaw-node-app.md)
- [../08-delivery/production-slices.md](../08-delivery/production-slices.md)
