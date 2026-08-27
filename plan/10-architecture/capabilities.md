# Capability Runtime

How a granted capability becomes an executed command, and what stands between the two. Implements §5.3, §6.4 and §11.

## Adapters

One adapter per capability family, in `:node:adapters`, depending on `:core:domain` and the Android SDK only — no gateway, no transport, no Room. That constraint is what makes them testable.

```kotlin
interface CapabilityAdapter {
    val capability: NodeCapability
    val requires: Set<AndroidRequirement>      // permission, role, service, or system setting
    val exclusive: Boolean                     // needs a lease
    fun health(): CapabilityHealth             // Working | PermissionMissing | OsLimited | Unavailable
    suspend fun invoke(command: NodeCommand): Receipt
}
```

`health()` is what the Node coverage matrix renders. It is derived, never cached optimistically: a revoked notification-listener grant shows as `PermissionMissing` on the next read, and the node advertises capabilities from this function rather than from what it was once granted. §6.1's "it never claims a capability it cannot serve" is this method.

## Dispatch

```text
broker frame → validate signature, request_id, grant, expiry, lock state
             → grant lookup (gateway, profile, node, capability)
             → lease acquire if adapter.exclusive
             → adapter.invoke
             → receipt persisted, then emitted
```

Every gate refuses with a named reason, and the refusal is a receipt like any success. Validation happens before the adapter is reached, so an adapter never has to consider whether it was allowed to run.

## Exclusive leases

Per `../02-contracts/capability-groups.md`. The lease table's primary key is the capability, so mutual exclusion is a uniqueness constraint rather than a lock:

```kotlin
class LeaseManager(private val dao: LeaseDao, private val clock: Clock) {
    suspend fun acquire(cap: NodeCapability, route: NodeRoute, ttl: Duration): LeaseResult
    suspend fun release(cap: NodeCapability, requestId: RequestId)
    fun observe(): Flow<List<Lease>>       // drives the Node screen's holder column
}
```

`acquire` runs one transaction: purge expired, then `INSERT OR ABORT`. A conflict returns `Held(by = route, until = …)` — never a queue, never a preemption. Leases are released on expiry, on holder disconnect, and on device lock where the capability requires an unlocked screen.

Read-only families take no lease and fan out to every granted route simultaneously.

## Redaction

Runs on-device before the event reaches the outbox, so what is stored for transmission *is* what would be transmitted.

```text
raw event → policy lookup (stream_rules by source)
          → redaction level: full | redacted | metadata-only
          → payload written to node_events
          → raw body written to node_event_bodies with a short purge_after
```

Levels come from §11. Sensitive categories — OTP, banking, health — default to metadata-only regardless of the per-source rule, and a per-source rule cannot raise them. The Stream Rules screen previews the resulting payload at each level, which is only honest because this pipeline is the same code path.

## Rate limiting

A token bucket per `(node_id, source_key)` with the ceiling from `../03-android/notification-correctness.md`. Events over the ceiling are recorded and marked `throttled` — never dropped without a trace, because §6.4 forbids losing evidence. The bucket is consulted after durable write and before broker send.

## Permission mapping

A single table maps capability to Android requirement, and it is the only place that knowledge lives. It drives the Full Node Mode checklist, `health()`, and the setup screens simultaneously, so the three cannot disagree.

| Capability | Requirement |
| --- | --- |
| `notifications.read` / `.actions` / `.reply` | `NotificationListenerService` access |
| `calls.observe` | `CallScreeningService` role |
| `calls.answer` / `.reject` | `InCallService` + default-dialer role |
| `messages.sms.*` | SMS role, install channel permitting |
| `screen.capture` | MediaProjection consent per session |
| `screen.input` | AccessibilityService, separately enabled |
| `camera.snap` / `.clip` | camera permission, foreground only |
| `contacts.*` / `callLog.*` | contacts and call-log permissions |

## Self-loop guard

Companion's own notifications carry an origin marker applied at post time and are excluded before routing — structurally, not by matching a profile display name. §6.2 is explicit about this, and the Delivery screen exposes a test that proves it on demand.

## See also

- [../02-contracts/capability-groups.md](../02-contracts/capability-groups.md)
- [../03-android/full-node-mode.md](../03-android/full-node-mode.md)
- [../07-privacy/privacy-model.md](../07-privacy/privacy-model.md)
