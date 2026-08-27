# Runtime and Service Lifecycle

Implements §6's "user-visible foreground Node service for the persistent broker connection" and §9's reconnect and reconciliation rules. Nothing in the PoC survives here: today the connection lives in `viewModelScope` and dies with the screen.

## CompanionConnectionService

A single foreground service owns every connection. It is started when the first gateway is registered and stops when the last is forgotten.

- `FOREGROUND_SERVICE_CONNECTED_DEVICE`, promoted to also declare `FOREGROUND_SERVICE_MICROPHONE` for the duration of a Talk session and demoted after (Android 14+ requires the type at capture time, not at start time).
- One notification, honest about scope: *"Connected to Home · 2 profiles · 0 queued"*. It is the operator's only proof the node is alive, so it reports counts, not a bare "running".
- `START_STICKY`. On restart it rehydrates entirely from Room — gateway registry, ack watermarks, run cursors, outbox — and resumes. It never asks the UI for anything.
- The service holds no UI reference and no `Activity` context.

## Per-gateway supervisor

One `GatewaySupervisor` per registered gateway, in `GatewayScope`, each with its own `CoroutineScope` on a `SupervisorJob`. A gateway failing cannot cancel another's work — the structural version of §3's "switching gateways cannot leave another gateway's state visible".

Each supervisor runs four long-lived jobs:

| Job | Responsibility |
| --- | --- |
| `health` | `/health` poll, capability document refresh, stale-since clock |
| `runs` | reattach `GET /v1/runs/{id}/events` for every run this gateway has open, from its stored cursor |
| `broker` | node broker socket: inbound commands, outbound events, ack cursor |
| `pump` | drain the outbound submission queue for this gateway's routes |

## Reconnect

Per gateway, never global. Backoff is exponential with full jitter, capped at 60s, and reset only by a *successful authenticated exchange* — not by a socket opening.

On every reconnect, in order: evaluate the transport privilege tier ([security.md](./security.md)); re-fetch the capability document; resume the broker from `last_acked_seq`; resume each open run from `last_run_cursor`. A tier that dropped from TLS to cleartext ends the node session before anything else happens.

Streams use server heartbeats and a finite read timeout. The PoC sets `readTimeout(0)` and `callTimeout(0)`, which turns a silently dead socket into a permanently parked thread; the mock server already emits a `:ok` comment frame, so heartbeat-based liveness is available today.

## Surviving the device

The node is worthless if it dies overnight, which is the most likely failure on a Samsung device.

- Full Node Mode requests unrestricted battery and walks the operator through Samsung's autostart and sleeping-apps screens. The Node coverage matrix reports the real state, so "granted" is never assumed.
- **WorkManager is a watchdog, not a transport.** A periodic worker (15 min, the platform floor) checks only *outstanding* state: unacked events, open runs, expired leases, submissions past their retry window. It never sends an all-clear, because an all-clear could mark a pending event handled — §9's rule.
- `NotificationListenerService.onListenerConnected()` calls `getActiveNotifications()` and reconciles against durable receipts immediately. No timed poll.
- On backgrounding, a presence beacon `node.presence.alive` goes up; the gateway records last-seen. The beacon counts only when the gateway confirms it handled the frame.
- FCM or UnifiedPush, if used, is a **wake hint only**. Payloads are fetched from the gateway; nothing sensitive rides the push.

## Process death and cold start

Cold start does not wait for the UI. The service restores from Room and reconnects; the first Compose frame renders whatever the database already holds, with a per-gateway `stale since` if the connection has not yet come up. There is no loading state that blocks on the network — the PoC's `loading = true` with no error path is exactly the bug this removes.

## See also

- [data.md](./data.md)
- [transport.md](./transport.md)
- [../05-reliability/offline-behavior.md](../05-reliability/offline-behavior.md)
- [../03-android/notification-correctness.md](../03-android/notification-correctness.md)
