# Event-Processing Correctness

Source: `../../Hermes-Companion-Plan.md` §6.4

A phone event is never handed directly to an untracked one-shot model call. The gateway first persists it, assigns it to an exact `(gateway, profile, node)` route, then creates an agent run with a durable run ID.

- Provider/model are resolved or explicitly pinned at execution time.
- Provider errors, quota exhaustion, and model drift keep the event pending and visible; they do not acknowledge or discard it.
- Retry uses bounded backoff and can fail over only through a configured Hermes model route.
- App Activity shows `captured → uploaded → acknowledged → judged → notified|suppressed|failed`.
- Nyx can open any suppressed item and inspect why it did not ping.
- A deterministic health test posts a local test notification, observes the listener callback, sends it through the broker, and verifies the profile receipt end to end.

## See also

- [02-contracts/edge-contract.md](../02-contracts/edge-contract.md)
- [03-android/notification-correctness.md](./notification-correctness.md)
- [05-reliability/offline-behavior.md](../05-reliability/offline-behavior.md)
- [06-ux/information-architecture.md](../06-ux/information-architecture.md)
