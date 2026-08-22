# Production Slices

Source: `../../Hermes-Companion-Plan.md` §13

## Slice A — useful client (2–3 weeks)

- Compose shell, gateway registry, Keystore tokens
- profile discovery/multiplex routing
- sessions + chat/stream
- run cards, stop/steer, approvals
- images/files/voice

## Slice B — delivery channel (1–2 weeks)

- companion platform plugin
- proactive delivery, receipts, background notifications
- per-profile home channel
- deep links to exact route

## Slice C — Android node (3–5 weeks)

- pairing, node broker, Room outbox, receipts, and per-route ack watermarks
- Full Node Mode permission/role wizard and capability health matrix
- `NotificationListenerService` + active-notification restart reconciliation
- calls, contacts, call-log outcomes, and device status
- per-profile/per-gateway grants and audit log
- action receipts and model/provider failure queue
- deterministic end-to-end notification canary

## Slice D — high-impact control (incremental)

- screen capture/input, camera, microphone, file grants
- lock-state/biometric policies
- reliability, revocation, adversarial tests

## See also

- [08-delivery/poc-scope.md](./poc-scope.md)
- [08-delivery/acceptance-criteria.md](./acceptance-criteria.md)
