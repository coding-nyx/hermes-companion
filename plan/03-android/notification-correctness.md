# Notification Correctness Contract

Source: `../../Hermes-Companion-Plan.md` §6.2

ADB/logcat is not used for production notification ingestion. The Companion receives every app notification through `NotificationListenerService.onNotificationPosted()` and `onNotificationRemoved()`, including WhatsApp and Cliq, regardless of which Samsung log tag an app happens to emit.

On `onListenerConnected()` and every service restart, the app calls `getActiveNotifications()` and reconciles them against durable receipts. This closes the restart/race gap without waiting 30 minutes.

## Per-notification recording

For each notification the Node records, before any model decision:

- stable event ID derived from user/profile, package, notification key, post time, and content revision;
- package, channel, category, conversation/shortcut ID, `UserHandle`, group/summary state;
- title, redacted preview, actions, remote-input/reply affordance, and post time;
- local policy result and content sensitivity class;
- outbox sequence and delivery status.

## Judgment timing

Importance judgment happens **after** the event is durably stored and acknowledged by the gateway. "Not important" may suppress a user ping; it must never erase evidence that the event arrived.

## Forwarding limits

Ingestion is unconditional; forwarding is not. Both limits are local policy applied after the event is durably recorded, so a suppressed or throttled event is still evidence.

- **Rate ceiling.** A per-device events-per-minute cap bounds what any one package can push at a gateway. Events over the ceiling stay recorded and are marked throttled, never dropped without a trace.
- **Never forwarded.** Companion's own package, plus any app Hermes already reaches the operator through — Telegram today, per §1. Forwarding a channel Hermes already owns creates a second copy of the same conversation and invites a delivery loop.
- The never-forwarded list is exactly the set Hermes serves natively. It must not grow to cover WhatsApp or Cliq: §6.2 requires both, and they are the reason this contract exists.

## Self-loop prevention

Self-notification loops are prevented structurally: events originating from the Companion's own package/delivery ID carry an origin marker and are excluded before routing. Do not hard-code a profile display name such as "Ash" as the loop guard.

## See also

- [02-contracts/edge-contract.md](../02-contracts/edge-contract.md)
- [03-android/full-node-mode.md](./full-node-mode.md)
- [05-reliability/offline-behavior.md](../05-reliability/offline-behavior.md)
- [09-parity/openclaw-node-app.md](../09-parity/openclaw-node-app.md)
