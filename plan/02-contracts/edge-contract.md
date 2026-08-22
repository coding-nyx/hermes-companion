# Edge Contract: Companion Platform + Node RPC

Source: `../../Hermes-Companion-Plan.md` §5.1–5.2

Chat can use the API server directly. Proactive delivery and Android capabilities need one edge plugin, not core model tools.

## 5.1 Hermes plugin responsibilities

A user/profile plugin named `hermes-companion` should register:

1. a **platform adapter** named `companion` for proactive messages and cron delivery;
2. an **approval transport** for request-bound approve/deny UI;
3. a bounded **node broker** WebSocket/HTTP service;
4. optional commands (`/devices`, `/node`) and a bundled skill;
5. no broad core tool unless capability use genuinely needs model access.

The platform adapter normalizes app messages into ordinary Hermes `MessageEvent`s, preserving the gateway/profile/session routing key. Cron delivery can target `companion:<device>/<profile>/<session>`.

## 5.2 Node broker envelope

All frames are versioned and idempotent:

```json
{
  "v": 1,
  "type": "node.event",
  "event_id": "evt_01J…",
  "gateway_id": "gw_home",
  "profile": "ash",
  "node_id": "node_s22",
  "sequence": 8841,
  "sent_at": "2026-08-22T12:00:00+05:30",
  "capability": "notifications.read",
  "payload": {"package": "com.whatsapp", "title": "…", "preview": "…"}
}
```

Every mutating command carries `request_id`, `grant_id`, and an expiry. The node returns an acknowledgement and a terminal receipt:

```text
command.accepted → command.progress* → command.completed|failed
```

The broker deduplicates by `(node_id, event_id)` and `(node_id, request_id)`.

## See also

- [02-contracts/capability-groups.md](./capability-groups.md)
- [02-contracts/existing-api.md](./existing-api.md)
- [03-android/full-node-mode.md](../03-android/full-node-mode.md)
