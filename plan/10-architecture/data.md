# Data Layer

Room is the single source of truth. Transport writes it; the UI observes it. Implements §9's durability rules and the retention split in §11.

## Tables

Composite primary keys carry the route rather than a surrogate id wherever the route *is* the identity.

```text
gateways(id PK, label, kind, url, tier, auth_ref, caps_json, health,
         last_ok_at, stale_since)
profiles(gateway_id, profile_id) PK, display_name, handle, multiplexed
sessions(gateway_id, profile_id, session_id) PK, title, model_lock,
         run_state, unread_count, pinned, archived, updated_at
messages(id PK, gateway_id, profile_id, session_id, role, text,
         tool_runs_json, created_at)                 -- index on route + created_at
runs(gateway_id, profile_id, session_id, run_id) PK, state, cursor, updated_at

outbound(id PK, idempotency_key UNIQUE, gateway_id, profile_id, session_id,
         kind, body, attachment_uri, state, attempts, created_at, expires_at)

node_events(event_id PK, node_id, seq, gateway_id, profile_id, capability,
            payload_json, redaction_level, state, throttled, created_at)
node_event_bodies(event_id PK, raw_json, purge_after)  -- separate retention
ack_watermarks(gateway_id, profile_id) PK, last_acked_seq, last_run_cursor

grants(gateway_id, profile_id, node_id, capability) PK, mode, expiry, policy
leases(capability PK, gateway_id, profile_id, request_id, acquired_at, expires_at)
receipts(request_id PK, node_id, gateway_id, profile_id, state, detail, created_at)

approvals(request_id PK, gateway_id, profile_id, session_id, run_id,
          command, digest, grant_id, expires_at, decision, decided_at)
questions(request_id PK, gateway_id, profile_id, session_id, payload_json,
          expires_at, answer_json, answered_at)
jobs(gateway_id, profile_id, job_id) PK, name, schedule, state, last_outcome, next_at
stream_rules(gateway_id, source_key) PK, mode, target_profile, redaction_level
privacy_log(id PK, at, capability, gateway_id, profile_id, what_left, redaction)
```

## Rules the schema exists to enforce

- **Sequence allocation is transactional.** A `node_events` insert and its `seq` come from one transaction, per §9. The sequence is monotonic per `node_id`, not per gateway, so a single ordering survives fan-out to several routes.
- **Two retention classes.** `node_events` holds the redacted payload that would be transmitted and lives as long as audit requires. `node_event_bodies` holds the raw body under a short `purge_after` and is deleted independently. Receipts and audit metadata outlive payloads — §11.
- **Watermarks are per route, not per gateway.** `ack_watermarks` is keyed `(gateway_id, profile_id)`. A gateway outage therefore cannot merge two profiles' progress, and §9's "each gateway shows its own stale timestamp and queue depth" falls out of the query.
- **`leases` has one row per capability.** The uniqueness of the primary key *is* the mutual exclusion; acquisition is an `INSERT OR ABORT` inside a transaction that first deletes expired rows. No lock object, no race.
- **`outbound.idempotency_key` is unique.** Replay is an upsert that cannot duplicate. See [transport.md](./transport.md).
- **`state` columns are enums with a terminal set**, and nothing transitions to a terminal state on a timeout alone. An unanswered submission becomes `unacknowledged`, never `sent`.

## Migrations

Auto-migrations where the change is additive; hand-written `Migration` classes otherwise, each with a test that opens the previous schema, migrates, and asserts on real rows. Schemas are exported to `app/schemas/` and committed, so a diff shows any accidental change.

## Encryption at rest

Recommended, not decided: SQLCipher with a key held in the Keystore, so a rooted device cannot read notification bodies out of the database file. Tokens are never in the database regardless — they live in the Keystore under `:transport:auth`. Until this is decided, `node_event_bodies.purge_after` is the only thing limiting exposure, which is why its retention should be hours rather than days.

## See also

- [state.md](./state.md)
- [security.md](./security.md)
- [../05-reliability/offline-behavior.md](../05-reliability/offline-behavior.md)
- [../07-privacy/privacy-model.md](../07-privacy/privacy-model.md)
