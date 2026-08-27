# Migration From the PoC

**Status:** steps 0–4 are done. Step 5 is next.

Ordered steps from the tree as it stands to the architecture in this folder. Every step ends buildable with tests passing, and no step requires the next one to be useful. Nothing here is a rewrite: `domain/` and the Compose screens largely move rather than change.

## Step 0 — commit, then fix what is broken ✅

The working tree currently holds an uncommitted switch from the in-process mock to a live HTTP path. Commit it as a checkpoint first, then fix the four defects it introduced, because each is a few lines and each blocks demoing:

1. **Crash on an unreachable gateway.** `AgentsViewModel.init → refresh()` calls `listProfiles()`, which does `it.body!!` and `!!.jsonObject`; the `IOException` escapes `viewModelScope` and takes the process down at launch. Wrap now; Step 3 removes the need.
2. **Mock server cannot address a session it created.** It resolves a session by scanning each profile's first entry, so `POST /api/sessions` succeeds and every subsequent call 404s. Index sessions by id.
3. **Approval is a dead end.** `decideApproval` posts to `/v1/runs/any/approval` with a literal `"any"`; the server accepts and does nothing, and the stream already ended. Keep the pending run server-side, expose approval by request id, resume via `/v1/runs/{id}/events`.
4. **SSE cancellation leak and dropped deltas.** Cancel the call on cancellation; `channelFlow` with a suspending send; finite read timeout with the heartbeat the server already emits.

## Step 1 — extract the pure core ✅

Move `domain/` to `:core:domain` as a JVM module and add `:core:common`. A mechanical move — the types are already Android-free and already carry the route. The existing `RoutingKeyTest` moves with it. Add `:core:domain` types the design needs and the code lacks: `Lease`, `QuestionRequest`, `Submission`, `RedactionLevel`, `TransportTier`, `CapabilityHealth`.

## Step 2 — transport, split properly ✅

Create `:transport:auth` with `SignedRequestFactory` and an internal token store, then move `HttpHermesBackend` into `:transport:hermes` and split `sendAndStream` into `submit` + `runEvents`. Add the SSE tests from [testing.md](./testing.md). `MockHermesBackend` moves into `:transport:hermes` alongside the double it is, and stops being the only thing the suite covers.

Landed: `HttpHermesBackend` is `internal`, reached through `httpHermesBackend(gateway)`, so okhttp and `:transport:auth` stay off the app's compile classpath — verified, `:app` resolves zero okhttp entries. `submit` posts `/v1/runs` and returns a run id; `runEvents` observes it separately. The one thing deferred from this step: features still call backends directly, because they are not extracted into `:feature:*` modules yet, so the "features cannot see transport" rule is not yet enforceable. Step 3 cuts that edge.

## Step 3 — Room, and cut the ViewModel→transport edge ✅

The big one, and the one that pays for itself. Add `:data:db` with the schema in [data.md](./data.md) and `:data:repo` with the four repositories in [state.md](./state.md). Rewire `AgentsViewModel`, `ChatViewModel` and `SettingsViewModel` to observe repositories.

This structurally removes the crash-on-unreachable, the duplicate assistant bubble, the operator's own message not appearing, and the manual `refresh()` that leaves the Agents lane stale after adding a gateway.

Landed: five tables (gateways, profiles, sessions, messages, runs) — the ones with a real writer today. `:data:db` exposes only a `CompanionStore` of DAO interfaces, so Room never appears in a dependent module's API; `:data:repo` owns `BackendRegistry` and the transport dependency. `:app` now resolves **zero** okhttp, transport or Room entries on its compile classpath, so "features cannot see transport" is enforced rather than intended.

`RunTracker` collects a run into the database on an application-scoped coroutine, so leaving Chat no longer cancels it — a run also survives being re-entered, and a gated run's approval lives in its `runs` row rather than in a ViewModel field.

Deferred with the tables that have no writer yet: `node_events`, `ack_watermarks`, `grants`, `leases`, `receipts`, `questions`, `jobs`, `stream_rules`, `privacy_log`. `ActivityRepository` and `NodeRepository` wait for them. The outbound outbox is still step 5; a submitted message is written with `pending = true` in the meantime.

## Step 4 — the foreground service ✅

Add `CompanionConnectionService` and per-gateway supervisors from [runtime.md](./runtime.md). Connections move out of `viewModelScope` for good. The app now survives being swiped away, which is the precondition for everything in the node half.

Landed: `ConnectionSupervisor` in `:data:repo` runs one loop per gateway, added and dropped as gateway rows appear and disappear, each with its own consecutive-failure backoff (1s doubling to a 60s cap, full jitter, floored so a retry is never immediate) and a 30s steady poll once healthy. A successful probe re-observes any run still open, which is the reconciliation rule from §9 — it asks only about outstanding work and never sends an all-clear. `CompanionConnectionService` in `:app` owns the scope, posts a notification carrying counts rather than "running", and is `START_STICKY` so a system restart rehydrates from the database.

Three defects worth recording, all now covered by tests:

- `scope.launch(SupervisorJob())` **detaches** the coroutine from the scope, because the passed job becomes its parent. The service could have been destroyed with its supervision still running. Use `supervisorScope` inside `scope.launch` for sibling isolation without detaching.
- Feeding a **jittered** delay back in as the previous value lets one small random number reset the growth, so a dead gateway could be probed twice a second indefinitely. Derive the ceiling from the consecutive-failure count instead, and apply jitter inside it.
- A test that leaves a supervision loop alive on the test scheduler makes `runTest`'s end-of-test `advanceUntilIdle()` spin forever in virtual time — and because that spins on the test thread, `runTest`'s own real-time timeout can never fire. It wedged the build for 81 minutes. Every time-driven test must cancel its scope in a `finally`, not at the end of the happy path.

Two things `runtime.md` describes that are not here yet: the transport-tier check on reconnect (step 6, with discovery) and capability-document persistence, which needs a schema migration. Boot-time service start also waits for the node work, which needs a receiver anyway; today the service is started from a visible Activity because Android 12+ refuses a background foreground-service start.

## Step 5 — outbound outbox

Submissions written before the network, replayed under an idempotency key, with the unacknowledged state surfaced. Ships the Outbox screen. First point at which the app is honest about a message it cannot confirm.

## Step 6 — discovery and tiers

`:transport:discovery` (mDNS/NSD, wide-area DNS-SD) and tier evaluation on connect. Ships the Discover screen and the cleartext-limited warning. Small, and it makes adding a gateway pleasant rather than a typing exercise.

## Step 7 — broker interface and a fake

Define `NodeBroker`, implement `FakeNodeBroker`, and grow `mock-server/` to serve the envelope and a WebSocket. No Android capability yet — this proves the plumbing and the dedupe rules in tests.

## Step 8 — the first real capability, end to end

`notifications.read` plus `device.status`: `NotificationListenerService`, reconciliation on connect, durable event with transactional sequence, redaction, broker send, receipt, and the Activity lane rendering the state machine. This is the first step where the product exists. Then the Diagnostics canary, which is the same path with a synthetic event.

## Step 9 — leases, rate limiting, grants

`LeaseManager`, the token bucket, the never-forwarded list, and the grant matrix wired to real enforcement. Ships the Stream Rules and Node coverage screens against real state instead of sample data.

## Step 10 — pairing and gates

Ed25519 in the Keystore, the derived verification phrase, per-gateway revocation, and the four biometric gates at the repository boundary. Until this step, treat every gateway as trusted-by-configuration and say so in the UI.

## Ordering notes

- Steps 0–3 are worth doing before any new feature work; every later step is cheaper afterwards.
- Steps 6 and 7 are independent of each other and of 5. Reorder freely.
- Step 8 is the first step a person outside the project would notice.
- Slice mapping: 0–6 land Slice A, 7–9 land Slice C, and Slice B's proactive delivery needs Step 4 plus the gateway plugin.

## See also

- [README.md](./README.md)
- [testing.md](./testing.md)
- [../08-delivery/production-slices.md](../08-delivery/production-slices.md)
