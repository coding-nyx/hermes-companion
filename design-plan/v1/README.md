# Hermes Companion · Phase A design

Phase A replaces the bot-style 5-tab bottom navigation with a
ChatGPT-style three-column shell. This directory holds the eight
HTML mocks the build subagent will use as a visual spec, plus a
canvas manifest and this README.

## Artboards

`canvas.json` lists every mock with its position on the design
canvas. Open the directory via the canvas launcher (`launch.view =
"canvas"`) to see them all at once.

| Mock                              | Width × Height | Variant                |
|-----------------------------------|---------------:|------------------------|
| `ChatSurface.dc.html`             | 390 × 844      | phone (center column)  |
| `LeftRail.dc.html`                | 320 × 844      | tablet (persistent)    |
| `LeftRail-phone.dc.html`          | 320 on 390     | phone drawer           |
| `ContextPanel.dc.html`            | 320 × 844      | tablet (persistent)    |
| `ProfileSwitcher.dc.html`         | 390 × 844      | bottom sheet           |
| `SettingsSheet.dc.html`           | 390 × 844      | full screen sheet      |
| `PairAsNodeFlow.dc.html`          | 390 × 844      | two-mode flow          |
| `NewThreadDialog.dc.html`         | 390 × 844      | bottom sheet           |

## Theme

Every screen uses the production palette from
`app/src/main/java/com/hermes/companion/ui/theme/Color.kt` and the
typography from `…/theme/Type.kt`. We render Figtree for UI, IBM
Plex Mono for handles and code, and Instrument Serif for the
display title on the major sheets. (Plex Mono replaces PlexMono
in the Google Fonts URL because the latter is not in the catalog.)

Dark variant is the primary render. The M3 night surface ramp
(`NightBg → NightSurfaceContainer → … → NightSurfaceContainerHighest`)
is reused as `--night-bg`, `--night-surface-cont`, etc.

## Component reuse

Every screen in v1 reuses the existing `HermesComponents.kt`
composables as a visual reference (their names are noted inline).
Nothing in v1 invents a new composable:

| Screen            | Reused composables                                                                |
|-------------------|------------------------------------------------------------------------------------|
| ChatSurface       | HermesMark, SurfaceCard, HermesButton, HermesField, Chip, StatusBadge              |
| LeftRail          | HermesMark, HermesButton, SurfaceCard, Chip, SectionLabel                          |
| ContextPanel      | HermesMark, SurfaceCard, StatusBadge (Live/Warn/Muted), HermesButton               |
| ProfileSwitcher   | HermesMark, SurfaceCard, StatusBadge, Chip                                         |
| SettingsSheet     | SurfaceCard, ToggleRow, HermesField, Chip                                          |
| PairAsNodeFlow    | HermesField, HermesButton, StatusBadge, SurfaceCard                                |
| NewThreadDialog   | HermesField, Chip, HermesButton, SurfaceCard                                       |

---

## Per-screen rationale

### 1. ChatSurface

The center column is the new "home". On a tablet it sits between
LeftRail (320dp) and ContextPanel (320dp); on a phone it takes the
full 390dp with the panels collapsed into drawers.

Three affordances that earn the top bar: hamburger (opens LeftRail
on phone; hidden on tablet), the route capsule (the existing
"gw › @profile › thread" pill, kept because every screen depends
on it), and an inbox indicator that counts unread activity items
rather than new messages, since the activity stream is the new
universal "new since you last looked" surface.

The hero is intentionally empty when there is no active thread:
Instrument Serif greeting, suggested-prompt tiles that match the
current gateway / profile, and the composer still wired at the
bottom so the user can type without an extra tap. The transcript
mock shows a real HermesComponents-shaped tool-run card (`file.read`)
and a syntax-coloured code block so the build subagent has a target
for those components.

### 2. LeftRail

Two variants — `LeftRail.dc.html` is the persistent tablet rail at
320dp; `LeftRail-phone.dc.html` is the same content rendered as a
drawer with a scrim and a Close button. The phone variant is what
the user actually sees; the tablet variant is what the build should
anchor to for layout decisions (rail width, row heights, padding
rhythm).

Thread grouping follows the existing convention: Today, Yesterday,
Previous 7 days, Earlier — but the row anatomy is new. Each row
shows a profile handle chip, the title, the last-message preview,
and either an unread pill, a relative time, or a voice-thread
glyph. The active row gets a 1dp Indigo border and a tinted fill.

The footer keeps the profile chip (which routes to
`ProfileSwitcher`) and the settings gear (which routes to
`SettingsSheet`) on one row, both at 44dp so they are accessible
without compromising the list's density above them.

### 3. ContextPanel

Right-side panel that surfaces everything the user needs *while*
the chat is mid-stream, without forcing them to leave the
conversation. Sectioned top-to-bottom so the eye lands on the most
urgent first: agent status banner (idle / streaming / awaiting
approval), active tool runs (one in flight, one awaiting approval
with Approve/Deny inline), recent files referenced, voice-thread
indicator with a tiny waveform, and an approvals queue.

The Approve/Deny pair lives in-context, not behind a tap — that's
deliberate, because approval latency is the friction point the user
already complains about.

### 4. ProfileSwitcher

A bottom sheet that lists every known profile grouped by gateway
so the user understands the topology ("ah, my cloud profile is
degraded, that's why @ash-cloud is stale"). Search field at top,
active row pinned with an Indigo check, inactive rows in the
neutral SurfaceCard. Footer hints that switching the profile
routes *future* messages but does not move existing threads.

### 5. SettingsSheet

One sectioned list, ten groups. Replaces the old four-tab Settings
because (a) deep links from notifications and diagnostics need a
single surface to land on, and (b) the section count is going to
grow with the appearance / voice / pair-as-node work that's already
queued. Each row follows the same anatomy: icon + label + current
value + chevron. The "Pair as node" row is a CTA card rather than
a chevron row because it opens a multi-step flow.

### 6. PairAsNodeFlow

Two-mode toggle at the top. Discover mode keeps the existing
behaviour (`design/Discover.dc.html`): a scanning dot, candidate
cards (with "scanning…" skeleton placeholders while busy), and the
"Where it looks" explanation panel that reassures the user nothing
is trusted until fingerprint check on the next screen.

Manual mode replaces `design/PairNode.dc.html`'s QR scan step — for
headless / SSH-tunnel / cloud cases where mDNS won't reach. URL +
setup code, with the same "What gets exchanged" trust panel from
the existing pair flow.

### 7. NewThreadDialog

Sheet that comes off the composer "+". Replaces the old
`design/NewThread.dc.html` (which had a lock toggle and a model
selector). Keeps the lock-to-model toggle and the route preview
capsule because those are the two pieces of feedback users have
actually asked for. Adds an **Initial mode** chip row
(Auto / Plan / Code / Research) so the first run is biased in the
right direction without making the user pick a model by hand. The
mode label flows into the route preview so the user can see it
before they commit.

---

## Clarifying questions

The build subagent will need answers on these before the Kotlin
work starts. The current mocks pick a default; flip any of these
and the affected screen needs a re-pass.

1. **Top-left affordance on phone.** Hamburger (current mock) vs.
   always-visible profile avatar (ChatGPT-style). The hamburger is
   faster to tap for power users; the avatar doubles as a one-tap
   route to ProfileSwitcher.

2. **Voice input as a button vs. press-and-hold slider.** The mock
   uses a single tap-to-dictate button + a dedicated mic icon. If
   we want walkie-talkie "realtime Talk", the composer needs to
   grow a second mode toggle.

3. **ContextPanel default visibility on phone.** Currently hidden
   behind a right-edge affordance; do we want it peekable (a small
   handle that slides in 40% on edge-swipe) or strictly modal?

4. **NewThreadDialog initial mode default.** Auto (mock) vs. the
   most-recently-used mode per profile. Auto is safer; MRU is
   faster.

5. **SettingsSheet entry point from ChatSurface.** Gear in the
   left-rail footer (current mock) vs. an overflow menu in the
   chat top bar. Both can coexist; we should pick one as primary.

6. **Pair-as-node default mode on first launch.** Discover (mock)
   vs. Manual with the setup code pre-filled from a QR the user
   scanned from the gateway. The QR path is faster for the
   common case but requires a camera permission.

7. **Activity inbox in the chat top bar.** Currently counts
   "3 new" activity items (the same items that show up in the
   Activity tab the new shell replaces). Should it count all
   unread items, or only items that need user action?

---

## Worth keeping / worth retiring

### Keep (from existing designs)

- The `gw › @profile › thread` capsule, in every screen that
  touches routing — it's already the canonical way to read a route.
- The Indigo / Teal / Sand / Coral palette split: Indigo for
  primary identity, Teal for "ok / connected", Sand for "needs
  attention", Coral for "failed / dangerous".
- Tool-run cards with the `[name · elapsed time]` header and a
  mono second line for the file path / arg signature — users
  already parse this shape fast.
- The `Notifications.read` / `notifications.reply` tool naming
  convention with the verb-then-resource shape.

### Retire (from existing designs)

- The 5-tab bottom navigation (`Chat / Agents / Activity / Node /
  Settings`) — Phase A collapses it into the left rail + the
  context panel.
- The separate "Agents" screen — the ProfileSwitcher sheet and the
  LeftRail's profile chips cover that surface area.
- The "Activity" screen as a stand-alone tab — its content moves
  into the activity inbox indicator in the chat top bar (count)
  and the ContextPanel (detail).
- The "Node" tab — its features split between SettingsSheet
  ("Pair as node") and ContextPanel (live node status / approvals).
- The "Pair this phone" full-screen wizard (`design/PairNode.dc.html`)
  for QR-scan-only flows — it stays as step 2 of PairAsNodeFlow,
  not as the entry point.
- The old Settings four-tab layout (`design/SettingsScreen.kt`
  shape: Account / Gateway / Profiles / Voice) — the new
  SettingsSheet absorbs all of those into one list.

---

## Validation

- All eight files use the existing `support.js` runtime hook.
- All eight files are 100% inline styles; no external CSS.
- All touch targets are 44dp or larger (Compose default).
- Dark theme is the primary render; light theme is a `prefers-
  color-scheme: light` swap in production — these mocks are dark
  only to match the existing `design/*.dc.html` precedent.
- Every screen includes a route capsule, an aria-label on every
  icon button, and a `mono` font for handles / IDs.
