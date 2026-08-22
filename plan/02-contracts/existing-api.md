# Existing Hermes Contracts to Reuse

Source: `../../Hermes-Companion-Plan.md` §4

The current Hermes API already supplies most of the chat/control plane:

| Need | Existing Hermes contract |
|---|---|
| Feature discovery | `GET /v1/capabilities` |
| Health/readiness | `GET /health`, `GET /health/detailed` |
| Session list/history | `/api/sessions`, `/api/sessions/{id}/messages` |
| Session chat | `/api/sessions/{id}/chat/stream` (SSE) |
| New agent run | `POST /v1/runs` |
| Run lifecycle | `GET /v1/runs/{id}/events` (SSE) |
| Stop/steer | `/v1/runs/{id}/stop`, `/steer` |
| Human approval | `/v1/runs/{id}/approval` |
| Stateful responses | `/v1/responses` + `previous_response_id` |
| Jobs | `/api/jobs` CRUD + pause/resume/run |
| Models | `/api/model/options` |
| Skills/toolsets | `/v1/skills`, `/v1/toolsets` |
| Multiple profiles on one gateway | `/p/<profile>/…` when `gateway.multiplex_profiles` is enabled |

A mobile client should capability-detect every connection. Do not assume all gateways run the same Hermes version.

Profile inventory can use the same remote dashboard/desktop RPC (`profiles.list`) that Hermes Desktop uses. If a standalone HTTP-only gateway does not expose inventory, Companion stores user-pinned profile names and probes each `/p/<profile>/v1/capabilities` path. A small companion plugin endpoint can later expose a bounded profile manifest without exposing profile secrets or filesystem paths.

## See also

- [02-contracts/edge-contract.md](./edge-contract.md)
- [04-connection/runtime-paths.md](../04-connection/runtime-paths.md)
