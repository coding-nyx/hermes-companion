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
- camera.capture / microphone.record

## Grant model

Grants are per `(gateway, profile, node, capability)`, not global. High-impact capabilities can be `ask-every-time`, `allow-while-unlocked`, `allow-until`, or `deny`. Hermes approval policy remains authoritative; the app is a transport, not a policy bypass.

## See also

- [02-contracts/edge-contract.md](./edge-contract.md)
- [03-android/full-node-mode.md](../03-android/full-node-mode.md)
- [07-privacy/privacy-model.md](../07-privacy/privacy-model.md)
