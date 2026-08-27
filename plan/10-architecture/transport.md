# Transport Contracts

Two planes, two interfaces, one per gateway. The PoC's single `HermesBackend` cannot express the node half — it has no way to push an event upward or receive a command — and its `sendAndStream` conflates two operations that must be separable.

## HermesBackend — chat and control

Built against `../02-contracts/existing-api.md`. Every call is scoped to one gateway; the route is a parameter, never implicit state.

```kotlin
interface HermesBackend {
    val gateway: GatewayId

    suspend fun capabilities(profile: ProfileId? = null): CapabilityDoc
    suspend fun profiles(): List<AgentProfile>
    suspend fun sessions(profile: ProfileId): List<Session>
    suspend fun messages(route: ConversationRoute, before: Cursor?): Page<Message>

    /** Enqueues and returns immediately. Does not stream. */
    suspend fun submit(route: ConversationRoute, submission: Submission): RunId

    /** Reattachable. Resumes from [from]; safe to call after process death. */
    fun runEvents(route: ConversationRoute, runId: RunId, from: Cursor?): Flow<RunEvent>

    suspend fun stop(route: ConversationRoute, runId: RunId)
    suspend fun steer(route: ConversationRoute, runId: RunId, text: String)
    suspend fun decideApproval(route: ConversationRoute, requestId: RequestId, option: ApprovalOption)
    suspend fun answerQuestion(route: ConversationRoute, requestId: RequestId, answer: Answer)

    suspend fun jobs(profile: ProfileId): List<Job>
    suspend fun setJobState(route: ConversationRoute, jobId: JobId, state: JobState)

    suspend fun workspaceList(route: ConversationRoute, path: String): List<WorkspaceEntry>
    suspend fun workspaceGet(route: ConversationRoute, path: String): WorkspaceBlob
}
```

**Splitting submit from runEvents is the important change.** `submit` writes the outbound row and returns a run id; the supervisor's `runs` job subscribes separately, keyed by run id and cursor. This is what makes §9's "detached runs remain visible via `/v1/runs/{id}` even if SSE drops" true, and it removes the PoC's failure mode where cancelling a screen cancels the run's only observer.

`decideApproval` takes the real `requestId` and route. The PoC posts to `/v1/runs/any/approval` with a literal `"any"`, so an approved run never resumes — the surface exists but the loop does not close.

## NodeBroker — the node plane

The envelope is `../02-contracts/edge-contract.md`, versioned and idempotent.

```kotlin
interface NodeBroker {
    val gateway: GatewayId
    /** Inbound: commands to execute, plus acks for what we sent. */
    fun frames(): Flow<BrokerFrame>
    suspend fun send(event: NodeEventFrame): SendOutcome   // Acked | Unacknowledged | Refused
    suspend fun ack(cursor: Seq)
}
```

`SendOutcome` has three cases on purpose. Dedupe is by `(node_id, event_id)` for events and `(node_id, request_id)` for commands, on both sides. Commands answer `command.accepted → command.progress* → command.completed|failed`, and every mutating command carries `request_id`, `grant_id` and an expiry, which the node validates against its own grant table before touching an adapter.

Until the `hermes-companion` plugin exists, the only implementations are a `FakeNodeBroker` for tests and one backed by the extended mock server.

## SSE

Replace the hand-rolled parser. The current one trims the whole `data:` line rather than a single leading space — harmless while payloads are JSON, wrong the moment they are not — and has no tests.

Three concrete requirements:

- **Cancellation must cancel the call.** Wrap the read in `suspendCancellableCoroutine` with `invokeOnCancellation { call.cancel() }`, or register cancellation *before* the blocking loop. The PoC places `awaitClose { call.cancel() }` after `close()`, so leaving a screen mid-run parks an OkHttp thread on a socket with an infinite read timeout.
- **No dropped deltas.** Use `channelFlow` with a suspending `send`, not `callbackFlow` with an ignored `trySend`. A 64-element buffer plus a slow collector silently corrupts assistant text.
- **Finite read timeout with heartbeats.** The server already emits a comment frame; treat its absence as death.

## Capability detection

`CapabilityDoc` is fetched per gateway on connect and stored in `gateways.caps_json`. Features read a derived `FeatureSet`, never raw booleans, and a missing feature hides its affordance — the Settings screen renders exactly this for a gateway on an older Hermes. A capability document that omits `steer` means no steer control, not a control that fails when tapped.

Profile inventory prefers the `profiles.list` RPC; where a gateway lacks it, the operator pins names and the client probes `/p/<name>/v1/capabilities`, per `existing-api.md`.

## Idempotency

Every outbound submission carries a client-generated key, stored before the request. Replay reuses it. A gateway that has already seen the key answers with the original run id, which is what turns the unacknowledged case from a guess into a resolvable question.

## See also

- [runtime.md](./runtime.md)
- [data.md](./data.md)
- [../02-contracts/existing-api.md](../02-contracts/existing-api.md)
- [../02-contracts/edge-contract.md](../02-contracts/edge-contract.md)
