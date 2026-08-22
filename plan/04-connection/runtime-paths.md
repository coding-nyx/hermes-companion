# Runtime Data Paths

Source: `../../Hermes-Companion-Plan.md` §8

## User message

```text
Compose UI
  → selected (gateway, profile, session)
  → POST /p/<profile>/api/sessions/<id>/chat/stream
  → SSE: assistant.delta / tool.started / tool.completed / run.completed
  → Room cache + UI
```

## Proactive agent message

```text
Hermes profile
  → companion platform adapter
  → encrypted push/wake + broker inbox
  → app receipt
  → correct gateway/profile/session unread lane
```

## Phone event

```text
Android service
  → local privacy policy + redaction
  → signed node.event
  → gateway broker dedupe
  → profile routing policy
  → agent event run or deterministic suppress
  → companion session/activity card
```

## Node action

```text
Agent or user action
  → canonical Hermes approval if required
  → broker command with capability grant
  → Android validates signature, grant, expiry, lock state
  → action
  → structured receipt rendered in the run
```

## See also

- [02-contracts/edge-contract.md](../02-contracts/edge-contract.md)
- [02-contracts/existing-api.md](../02-contracts/existing-api.md)
- [03-android/event-processing.md](../03-android/event-processing.md)
- [05-reliability/offline-behavior.md](../05-reliability/offline-behavior.md)
