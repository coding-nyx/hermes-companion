# Capability Groups

Source: `../../Hermes-Companion-Plan.md` §5.3

Capability grants on the Android node are split into read-only and mutating families. Target platform is Android only; iOS-specific capabilities are intentionally excluded (see [../SCOPE.md](../SCOPE.md)).

## Read-only capabilities

- notifications.read / notifications.active
- calls.observe / calls.log
- contacts.lookup
- messages.sms.read (only when Android role/permission permits)
- device.status (battery/network/thermal/storage)
- app.usage
- location.read
- calendar.events
- motion.activity / motion.pedometer
- photos.latest
- device.health / device.permissions / device.apps
- notifications.actions (enumerate a notification's own actions)
- screen.capture
- clipboard.read
- media.session.read

## Mutating capabilities

- notifications.dismiss/action/reply
- calls.answer/reject/dial
- messages.sms.send (only when Android role/permission permits)
- apps.launch
- intents.send
- clipboard.write
- media.session.control
- screen.input/accessibility
- files.read/write scoped through Android SAF grants
- camera.capture (still) / camera.clip (video)
- microphone.record
- calendar.add / contacts.add

## Grant model

Grants are per `(gateway, profile, node, capability)`, not global. High-impact capabilities can be `ask-every-time`, `allow-while-unlocked`, `allow-until`, or `deny`. Hermes approval policy remains authoritative; the app is a transport, not a policy bypass.

## Exclusive capability arbitration

Several gateways stay connected at once, each with its own grants. Observation fans out; hardware does not.

- **Shareable** — every read-only family above is delivered to every granted `(gateway, profile)` route simultaneously.
- **Exclusive** — `camera.*`, `microphone.record`, and `screen.capture`/`screen.input` are held by one requester at a time under a **lease**: `(gateway_id, profile_id, capability)` plus a short expiry.
- A second request while a lease is held fails closed with a receipt naming the current holder and the remaining time. It does not queue silently and it does not preempt.
- A lease expires on its own clock, on disconnect of its holder, and on device lock where the capability requires an unlocked screen.
- The Node surface shows every exclusive capability, its holder, and its remaining time. "Nobody is using the camera" is a state worth rendering.

This is deliberately not a single-focused-gateway model; see [09-parity/openclaw-node-app.md](../09-parity/openclaw-node-app.md) for the alternative that was rejected and why.

## See also

- [02-contracts/edge-contract.md](./edge-contract.md)
- [03-android/full-node-mode.md](../03-android/full-node-mode.md)
- [07-privacy/privacy-model.md](../07-privacy/privacy-model.md)
- [09-parity/openclaw-node-app.md](../09-parity/openclaw-node-app.md)
