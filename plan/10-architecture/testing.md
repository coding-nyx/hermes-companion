# Test Strategy

The PoC has 18 passing tests, all against `MockHermesBackend` — which the app no longer uses. The riskiest code in the tree (a hand-rolled SSE parser, JSON mapping, the mock server itself) has none. This document says what is tested where, and which acceptance criterion each layer proves.

## By layer

| Layer | Kind | What it must prove |
| --- | --- | --- |
| `:core:domain` | plain JUnit | route equality and disambiguation; lease state machine; token-bucket arithmetic; redaction level transitions; envelope encode/decode round-trip |
| `:data:db` | Room in-memory + migration tests | sequence and event id allocated in one transaction; lease uniqueness under concurrent acquire; watermarks stay per route; retention purges bodies but not receipts; every migration opens the prior schema and asserts on real rows |
| `:transport:hermes` | MockWebServer + golden files | SSE frames parsed per spec, including split frames, comments, multi-line `data:`, and a truncated stream; cancellation cancels the call; no delta dropped under a slow collector; capability detection hides a missing feature |
| `:transport:broker` | `FakeNodeBroker` | dedupe by `(node_id, event_id)` and `(node_id, request_id)`; the three `SendOutcome` cases; resume from cursor |
| `:node:adapters` | Robolectric where possible, instrumentation where not | `health()` reports the real permission state; an adapter refuses when its requirement is missing; listener reconciliation via `getActiveNotifications()` |
| `:data:repo` | fakes for transport, real Room | an unreachable gateway yields `Down`, never a thrown exception; a submission appears in the transcript before it is acked; approving resumes the run |
| `:feature:*` | Compose UI tests | the four coverage states render distinctly; an approval sheet cannot be dismissed into a granted state; a question locks after answering |

## Two tests that would have caught what shipped

- **A cancellation test on `runEvents`.** Collect three deltas, cancel, assert the OkHttp call is cancelled and the connection pool has no active socket. The PoC parks a thread forever because `readTimeout` is 0 and `awaitClose` runs after `close()`.
- **A route-keyed session test on the mock server.** Create a session, then fetch its messages. The current server resolves a session by scanning each profile's *first* entry, so every session it creates 404s — a one-line test finds it.

## The mock server is a tested double

`mock-server/` is part of the test surface, not a scratch file. It gets its own suite asserting it matches `../02-contracts/existing-api.md`: status codes, field names, SSE event names and ordering, and the approval lifecycle. A double that drifts from the contract is worse than no double, because it makes the client wrong with confidence.

## The canary is a test

`../03-android/event-processing.md`'s deterministic health test — post a local notification, observe the listener callback, send it through the broker, verify profile receipt — runs three ways: as an instrumentation test in CI against the mock server, as the Diagnostics button on device, and as the WorkManager watchdog's periodic check.

## Acceptance criteria as the test plan

`../08-delivery/acceptance-criteria.md` is already written as assertions. Each line maps to a test at the layer that can actually prove it — the cross-profile leak checks belong in `:data:repo` and Room, the notification and call ones in instrumentation, the revocation one end-to-end. A criterion with no test is not done.

## What is deliberately not tested

Streaming *content* (the model's words), OEM battery behaviour on a specific Samsung firmware, and real provider quota exhaustion. These are verified on device, once, and recorded — automating them would test the vendor, not us.

## See also

- [../08-delivery/acceptance-criteria.md](../08-delivery/acceptance-criteria.md)
- [transport.md](./transport.md)
- [data.md](./data.md)
