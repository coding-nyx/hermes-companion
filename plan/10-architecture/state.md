# State and UI Contract

The rule that removes most of the PoC's bugs at once: **transport writes Room, features observe Room.** No ViewModel collects a network flow, holds a backend reference, or throws a network exception into a coroutine the UI launched.

## Repositories

`:data:repo` is the only surface `:feature:*` may call. Four repositories, split by plane rather than by screen, so two screens showing the same route cannot disagree.

```kotlin
interface FleetRepository {
    fun fleet(): Flow<Fleet>                            // gateways, profiles, health, unread
    fun threads(profile: ProfileRoute): Flow<List<Session>>
    suspend fun addGateway(candidate: GatewayCandidate): Result<GatewayId>
    suspend fun forget(gateway: GatewayId): Result<Unit>
}

interface ConversationRepository {
    fun conversation(route: ConversationRoute): Flow<ConversationState>
    suspend fun submit(route: ConversationRoute, draft: Draft): Result<SubmissionId>
    suspend fun stop(route: ConversationRoute, runId: RunId): Result<Unit>
    suspend fun decide(route: ConversationRoute, requestId: RequestId, option: ApprovalOption): Result<Unit>
    suspend fun answer(route: ConversationRoute, requestId: RequestId, answer: Answer): Result<Unit>
}

interface ActivityRepository {
    fun events(filter: EventFilter): Flow<PagingData<ActivityItem>>
    fun queues(): Flow<List<QueueState>>                // per gateway: depth, watermark, stale-since
    fun outbound(): Flow<List<Submission>>              // includes the unacknowledged ones
}

interface NodeRepository {
    fun coverage(): Flow<List<CapabilityHealthRow>>
    fun leases(): Flow<List<Lease>>
    fun grants(scope: GrantScope): Flow<List<CapabilityGrant>>
    suspend fun setGrant(grant: CapabilityGrant): Result<Unit>
    suspend fun runCanary(route: ProfileRoute): Result<CanaryReport>
}
```

Reads are `Flow` off Room. One-shot commands return `Result` — never throw, never surface a raw `IOException`. The PoC has no `try` in any ViewModel, so an unreachable gateway takes the process down at launch; `Result` at this boundary is what makes that impossible rather than merely unlikely.

## Connectivity is data, not an exception

Every observed state carries its own health, per route:

```kotlin
data class ConversationState(
    val route: ConversationRoute,
    val messages: List<Message>,
    val run: RunState?,                  // streaming text, tool runs, pending approval or question
    val pendingApproval: ApprovalRequest?,
    val pendingQuestion: QuestionRequest?,
    val connectivity: Connectivity,      // Live | Degraded(since) | Down(reason) | Unknown
    val queued: List<Submission>,
)
```

A gateway being down renders a banner and the last cached transcript. There is no code path where it renders a crash, and no loading flag that never clears.

## Streaming without duplicating

Streamed text is a column on the `runs` row, updated by the supervisor and observed like anything else. `RunEvent`s mutate the database; the UI reads the result.

This kills the PoC's duplicate assistant bubble, where `ToolStarted` appended a placeholder message that was never removed and `RunCompleted` appended a second message with the same tool runs. With a single row per run there is nothing to duplicate. It also fixes the operator's own message not appearing until a refetch: `submit` writes an `outbound` row immediately, and the transcript query unions committed messages with pending submissions for that route.

## Per-route scoping

`ConversationRepository.conversation(route)` is backed by queries filtered on the composite route key. Nothing is keyed by session id alone — the PoC's mock server resolves a session by scanning each profile's first entry, which is why every session it creates is unreachable. Route-keyed queries make that class of bug unrepresentable.

Route selection lives in `FleetRepository`, persisted per gateway, so the switcher restores each gateway's own last profile and thread rather than a single global selection.

## ViewModels

Thin. A ViewModel maps one repository flow to one UI state and forwards intents. It owns no `Job` that outlives a screen, holds no client, and never sees a token. Given the repositories above, most are a `stateIn` and a handful of `suspend` calls — which is the point: the interesting behaviour moved to where it can be tested without a device.

## See also

- [data.md](./data.md)
- [modules.md](./modules.md)
- [../06-ux/information-architecture.md](../06-ux/information-architecture.md)
