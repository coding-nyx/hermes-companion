# Chat Window · Threads · Profile & Gateway Switching — Plan

## Context (grounded in live research of hub-11 + the app + the bridge)

**A thread IS a Hermes session.** On hub-11, each profile is its own `HERMES_HOME`
(`~/.hermes` for `default`, `~/.hermes/profiles/<name>/` for named — only `ash` exists
today). Threads and their history live in **`<HERMES_HOME>/state.db`** — the `sessions`
table (thread rows: `id, title, model, archived, pinned, message_count, run_state…`) and
the `messages` table (turns). There is **no per-thread JSON file**; the app reaches
threads through the API, never by reading `state.db`. So *"threads live in the Hermes
path"* = they live in that profile's `state.db`, addressed by `session_id`.

**Serve API** (`hermes serve`, JSON-RPC over WebSocket at `/api/ws`, port 9119): the
vocabulary is `session.*` — `session.list(profile,limit,include_hidden)`,
`session.create(title,profile,model,cwd,parent_session_id,…)`, `session.history(session_id,
profile)`, `session.resume`, `session.title`, `session.status`, `session.steer`,
`session.interrupt`, `session.delete`, `session.set_hidden`; `prompt.submit(session_id,text)`;
`profiles.list`; `model.options`. **Profile is a parameter on every call** (no `/p/<profile>/`
requirement, no per-profile switch RPC). There is no `thread.*` — thread == session.

**Operational note (blocker to exercise this):** on hub-11 `hermes serve` is **not
running** (only per-profile `gateway run` message adapters are). To back the chat window,
`hermes serve` must run headless on 9119, and to reach every profile through one endpoint,
`gateway.multiplex_profiles` must be **on** (currently off) — otherwise it's one serve per
profile.

**The app already models all of this.** A thread is a `ConversationRoute(gateway, profile,
session)`; per-gateway `HermesBackend` isolation, per-route Room queries, the outbox, and
`RunTracker` (streaming survives navigation) are built and tested. **The gap is the
chat-centric UI + two thin repository methods** — not the plumbing. The `chat_bridge.py`
already maps `session.list / session.create / session.history` + runs/stream/approval;
it's missing steer, multi-profile inventory, live title/unread/run_state, and tool/approval
fidelity in history.

## Design decisions
1. **Thread == Hermes `session_id`**, addressed by the existing `ConversationRoute`. No new
   domain type. The app reaches threads via the bridge's `session.*` API (never reads
   `state.db` directly — that bypasses auth and has no live run state).
2. **Profile switch** = target a different `profile` (a different thread namespace on the
   same gateway). **Gateway switch** = a different backend. Both are already isolated by
   `BackendRegistry`; per-gateway "last profile + last thread" is persisted client-side.
3. **Multi-profile topology (recommended):** run one `hermes serve` with
   `multiplex_profiles: true`, so hub-11 is a single gateway exposing `ash` + `default`.
   (Alternative: one serve per profile → model each as its own gateway. Recommend
   multiplex for a clean single "hub-11" gateway.) — *decision to confirm with the user.*

## Plan

### Phase A — thin data + selection layer (app; ~½ day)
- `ConversationRepository.threads(gatewayId, profileId): Flow<List<Session>>` backed by the
  already-present-but-unused `SessionDao.observeForProfile` (`Daos.kt:53`); + `refreshThreads`
  → `backend.listSessionsForProfile` → upsert.
- `FleetRepository.selectedRoute: Flow<ConversationRoute?>` + `select(route)` / `selectGateway`
  / `selectProfile`, backed by the already-present-but-unwired `BackendRegistry.selectRoute /
  selectedRoute` (`BackendRegistry.kt:31,77`). Persist per-gateway `(lastProfile, lastThread)`
  in DataStore; restore on gateway switch.
- Wire both through `di/AppModule.kt`; inject `FleetRepository` into `ChatViewModel`.

### Phase B — chat window + navigation (the visible change)
- **Route capsule** (shared component, see design-improvements.md §2): `gw › @profile ›
  thread` pinned on every screen; tap → fleet switcher. State from `selectedRoute`.
- **Fleet switcher** (bottom sheet): gateway (health dot) → profile (state chip, unread) →
  thread, each gateway showing "returns to <lastThread>" (per-gateway memory).
- **Threads list** (per profile): Active/Pinned/Archived tabs, search, last-message preview,
  unread badge, state chip (Streaming/Awaiting/Idle), model chip (locked vs profile default),
  swipe pin/archive.
- **New thread** (gateway-first): Gateway → Profile → Name → model-lock, with a live
  `gw › @profile › new` route preview (can't name before the route exists).
- **Chat screen**: thread selector + profile picker in the bar, back-to-threads, two-pane
  master/detail on wide; steer chips (needs bridge `/steer`); per-thread model lock.
- Promote Chat to a first-class destination (5-tab IA) OR keep it a detail but always
  reachable via the capsule + threads list.

### Phase C — transcript fidelity
- Markdown + fenced code rendering (mono + copy); tool-run cards, approval cards, node-origin
  event cards, streaming caret. Requires Phase D history fidelity so a reopened thread
  rebuilds tool/approval structure, not just role+text.

### Phase D — plugin / bridge gaps (`hermes-companion-gateway`)
- **Multi-profile inventory:** `GET /api/profiles` → `profiles.list` (real ash+default, not
  one synthetic); thread `session.*` inventory (create/list/messages/runs) profile-scoped.
- **Steer:** `POST /v1/runs/{id}/steer` → `session.steer` / `prompt` steer.
- **Live fields:** map real `title`/`unread`/`run_state` from `session.list`/`session.status`
  (today hardcoded idle/0).
- **History fidelity:** map `tool_calls`/approvals/`model_lock` from `session.history` so the
  transcript replays tool + approval cards.
- **Model-lock write:** set per-thread model on `session.create` (`model` param exists) + an
  update path for NewThread's toggle.

### Phase E — deploy + verify (end to end)
- On hub-11: start `hermes serve` headless on 9119 + `multiplex_profiles: true`; run the
  gateway plugin with `HERMES_COMPANION_CHAT=1`.
- In the app: add hub-11 as a gateway (`http://hub-11.<tailnet>.ts.net:<plugin-port>` or the
  tailnet IP → **full tier**, WireGuard-encrypted). Verify: `profiles.list` shows ash +
  default; per-profile thread list; open a thread → history; send → live stream; switch
  profile and gateway; per-gateway memory restores the right thread; steer works.

## Open decisions (for the user)
- **Multiplex vs per-profile serve** (Phase A.3 topology) — recommend multiplex (one hub-11
  gateway, many profiles).
- **IA:** promote Chat to a 5th tab, or keep it capsule/threads-reachable only?

See `design-improvements.md` for the shared component system (capsule, switcher, cards) this
builds on.
