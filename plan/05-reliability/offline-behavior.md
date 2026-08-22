# Reliability and Offline Behavior

Source: `../../Hermes-Companion-Plan.md` §9

- A notification/call callback is written to Room in the same local transaction that allocates its event ID and sequence; network dispatch comes later.
- Room-backed outbox with monotonic sequence per node and independent acknowledgement watermarks per gateway/profile route.
- At-least-once transport plus idempotent event/request IDs.
- Gateway acknowledges only after durable broker persistence, not after merely accepting a socket frame.
- Agent judgment acknowledges separately; a model/provider failure leaves the event in `failed/pending retry`, never `handled`.
- Explicit ack watermark from gateway; delete payload only after policy retention requirements are met.
- Reconnect resumes from last sequence and last run-event cursor.
- `NotificationListenerService.getActiveNotifications()` reconciles service restarts immediately; WorkManager is a watchdog for broken/unacked state, not the primary notification source.
- Detached runs remain visible via `/v1/runs/{id}` even if SSE drops.
- Reconcile checks only unacked/outstanding state and sends no "all clear."
- Gateway outage does not merge routes. Each gateway shows its own stale timestamp and queue depth.
- The app may draft messages offline but labels them queued and shows the exact target `(gateway, profile, session)`.
- The diagnostics page can run an end-to-end canary per `(gateway, profile)` and reports the last successful notification callback, broker ack, model judgment, and Companion delivery.

## See also

- [03-android/notification-correctness.md](../03-android/notification-correctness.md)
- [03-android/event-processing.md](../03-android/event-processing.md)
- [04-connection/runtime-paths.md](../04-connection/runtime-paths.md)
- [08-delivery/acceptance-criteria.md](../08-delivery/acceptance-criteria.md)
