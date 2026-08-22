# Product Overview

Source: `../../Hermes-Companion-Plan.md` §1–3 (Product thesis; Product object model; Fleet hierarchy)

## 1. Product thesis

Hermes Companion is not another chat transport. It is a first-party mobile surface for a fleet of Hermes agents and an optional Android node that gives explicitly-granted device capabilities to selected profiles.

Telegram remains useful as a fallback delivery channel. Companion removes its structural limits:

- first-class streaming tool/run UI instead of text-flattened progress;
- request-bound approval controls;
- persistent gateway → profile → session navigation;
- media/files/voice without bot-format compromises;
- structured jobs, node events, device state, and actions;
- reliable background delivery with acknowledgements and an offline outbox;
- explicit, inspectable device capability grants;
- many gateways and many profiles without sharing state.

## 2. Product object model

The app never addresses "Hermes" as one global singleton.

```text
GatewayConnection
  id, label, kind(local|remote|ssh|cloud), base_url, auth_ref, health
  └─ AgentProfile
       gateway_id, canonical_profile, display_name, handle, capabilities
       └─ Session
            session_id, title, model_lock, run_state, unread_count

AndroidNode
  node_id, device_name, key_id, state, capabilities
  └─ CapabilityGrant
       gateway_id, profile, capability, mode, expiry, policy
```

The routing key for conversation data is always:

```text
(gateway_id, profile_id, session_id)
```

The routing key for a node command adds the node and grant:

```text
(gateway_id, profile_id, node_id, capability, request_id)
```

A profile is not a workspace and not a sandbox. It is the `HERMES_HOME` state boundary. The Companion must preserve that boundary even when several profiles share one OS user or one gateway listener.

## 3. Fleet hierarchy

The UI follows the same hierarchy Hermes Desktop already documents:

```text
gateway → profile → session
```

- A gateway is a machine or hosted backend.
- A profile is an isolated Hermes agent on that gateway.
- A session is one transcript/run lineage under that profile.
- Same-name profiles across gateways receive a disambiguated handle such as `@ash-home`.
- Each `(gateway, profile)` has its own API client, run subscriptions, session cache, node grants, and unread count.
- Switching gateways cannot leave another gateway's sessions, jobs, approvals, files, or capabilities visible.
- Cross-gateway delegation is explicit. It is never inferred from selecting two agents in the same app.

## See also

- [02-contracts/existing-api.md](../02-contracts/existing-api.md)
- [02-contracts/edge-contract.md](../02-contracts/edge-contract.md)
- [02-contracts/capability-groups.md](../02-contracts/capability-groups.md)
