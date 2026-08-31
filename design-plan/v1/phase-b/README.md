# Hermes Companion · Phase B design

Phase B turns the chat surface (from Phase A) into a **streaming-first
channel**. Telegram flattens everything into text; Phase B promotes
every run-state into its own first-class surface so the user can
supervise the agent without leaving the conversation.

This directory holds **screen + animation specs + HTML mocks** for the
seven sub-systems. No production Kotlin — the build subagent will
read this README and the seven `.dc.html` files as the visual spec.

## Artboards

| Mock                              | Panels | What it shows                                                                       |
|-----------------------------------|-------:|--------------------------------------------------------------------------------------|
| `streaming-text.dc.html`          | A B C  | Live token stream / long-press Stop overlay / scrolled-away "↓ new" pill             |
| `tool-cards.dc.html`              | A B    | Four card types (read-only) / mutating states (live / awaiting / failed)             |
| `background-work.dc.html`         | A B    | Persistent status bar (3 sub-states) / floating pill when user scrolled up            |
| `edit-regenerate.dc.html`         | A B    | Long-press action sheet / edit mode + branched-thread pill                           |
| `agent-handoff.dc.html`           | A B    | @knight mid-thread / HandoffChain visualization (top bar + Cast card)                 |
| `composer-upgrade.dc.html`        | A B    | Morph states (empty → text → recording) / "+" attachment sheet + ⌘K profile palette  |
| `inline-approvals.dc.html`        | A B    | Inline ApprovalCard in chat / Approvals queue in ContextPanel                        |

Every mock is phone-first (390 dp wide). On tablet the same surface
sits between the LeftRail (320 dp) and ContextPanel (320 dp) from
Phase A — the ContextPanel is where the HandoffChain Cast card and
Approvals queue live.

## Theme & conventions

- **Palette**: Indigo / Teal / Sand / Coral — same tokens as Phase A,
  read from `--indigo-80`, `--teal-40`, `--sand-80`, `--coral-40`.
- **Surfaces**: Night M3 ramp (`--night-bg` → `--night-surface-highest`).
- **Fonts**: Figtree (UI) · Instrument Serif (display) · IBM Plex Mono
  (handles / code) — same `<link>` as Phase A.
- **Status colors** (tool cards):
  - Live → Indigo (`var(--indigo-80)`)
  - Completed → Teal (`var(--teal-80)`)
  - Awaiting approval → Sand (`var(--sand-80)`)
  - Failed → Coral (`var(--coral-80)`)
- **Profile accent colors** (handoff): each profile is assigned a
  stable accent. Ash→Indigo, Knight→Magenta, Coder→Cyan, Research→Lime.
  New profiles inherit the next free accent from the palette.
- **Reuse first**: every spec reuses `HermesComponents.kt` before
  inventing anything new. New composables are listed below per spec.

---

## Specs

### 1. Streaming text

**Behavior.** When the model emits, each token appends to the last
streaming line. The cursor is a 1px-tall caret on the Indigo token
that just landed, blinking at 1 Hz (`step-end`, opacity 1 → 0.12).
When a paragraph break fires, the previous paragraph settles (no
caret) and the next paragraph fades in over 220 ms. Code blocks have
their own monospace caret at the end of the last visible line and
stream in chunks of 1–3 tokens.

**Cancel.** Long-press anywhere on the streaming assistant bubble →
popover anchored to the bubble's top-right corner: "Stop generating?"
with elapsed/tokens shown, two buttons (Keep streaming / Stop). Stop
fires at the next token boundary (<100 ms). Already-streamed text is
kept; the bubble settles with no caret.

**Auto-scroll.** Stream follows the bottom unless the user has
scrolled up by more than 80 dp. When they have, the scroll locks and
a "↓ N new" pill (Indigo, 36 dp tall) appears 92 dp above the
composer. Tap → smooth scroll to bottom, pill dismisses. While the
pill is visible the auto-scroll resumes on the *next* new message
(doesn't yank the user back from old context).

**Reuse / new.** No new composable — extend `SurfaceCard` and add
`AnimatedContent` around the streamed text node. New helper:
`StreamingCaret()` (1-line Composable, color/pos from
`BoxWithConstraints`).

**Edge cases.**
- Network drop mid-stream → bubble shows "● disconnected — tap to
  retry" instead of streaming forever.
- Empty assistant message (model produced zero tokens before stop) →
  the bubble auto-removes; the user sees only the "stopped" receipt.
- Token rate < 2 tokens/sec → caret fades to 0.4 opacity so the
  bubble doesn't feel "stuck".

### 2. Inline tool-run cards

Four card types — every card follows the same anatomy:

```
[icon · tool-verb · · · · · · · · elapsed · status]
[ one-line mono description (path / args) ]
[ optional expanded body — diff, stdout, results ]
[ optional action row — Approve/Deny | chevron | open in panel ]
```

| Card type    | Read-only? | Default action                  | Expanded body                            |
|--------------|-----------:|---------------------------------|------------------------------------------|
| `file.read`  | yes        | chevron → open in side panel    | inline syntax-coloured preview (140 dp)  |
| `bash.exec`  | **mutating** (default-once) | Approve/Deny OR live progress | stdout/stderr pane (scrollable, 140 dp)  |
| `search.query` | yes      | chevron → expand                | list of hits with snippets               |
| `git.*`      | **mutating** | Approve/Deny OR commit log     | commit log / diff                        |

Status colors: Live (Indigo, shimmer), Completed (Teal), Awaiting
(Sand, dot-pulse), Failed (Coral). Read-only cards show a chevron;
mutating cards show Approve/Deny inline (spec 7).

**Reuse / new.** `SurfaceCard` needs a `collapsible: Boolean` variant
so the build subagent doesn't reach for a third-party collapse API.
New composable: `ToolRunCard` (sealed class with `FileRead`, `BashExec`,
`SearchQuery`, `Git` variants). `StatusBadge` already covers the
status text but needs a "live" tone (currently `BadgeTone.Live` is
Teal — add an Indigo variant or repurpose).

**Edge cases.**
- `bash.exec` produces 1 MB of stdout → expanded body virtualises;
  the card itself never grows past 240 dp.
- `git.push` rejected by the remote → card flips to Failed (Coral)
  *with the rejection message* and surfaces a "Retry with --force?"
  chip — but **only** if the user has an explicit "force" lock-in.
- Tool name from a profile the user doesn't recognise → render the
  verb in muted mono with a "?" tooltip instead of crashing the card.

### 3. Background work indicator

**Behavior.** A 36-dp-tall status bar lives directly above the route
capsule row, always visible while an agent is mid-task. Three
sub-states share the same row, distinguishable by tone:

- **drafting** (Indigo) — model is producing tokens. Shimmer overlay
  across a 35% progress bar; "drafting · 4.2s · 612 tokens".
- **calling tools** (Teal) — model is in tool-loop. Shows the queue:
  "search.query ✓ · file.read ✓ · bash.exec ⏵ · 3 / 4 · 6.1s".
- **awaiting approval** (Sand) — model is blocked on a user decision.
  Inline "Review →" button opens the right ContextPanel and scrolls
  the queue to the offending item.

The bar collapses into a floating 44-dp pill ("● Agent working ·
drafting · 4.2s · ⊕ expand") 92 dp above the composer when the user
has scrolled away from the latest activity. Tap → expands
ContextPanel.

**Reuse / new.** `StatusBadge` covers the dot, but the bar itself is
new: `BackgroundWorkBar(state: WorkState)`. Reuses the same shimmer
animation as the streaming caret. The pill is a one-off — fits in
~30 lines.

**Edge cases.**
- Multiple agents in parallel (handoff chain) → bar shows the
  *active* agent; tap to expand a stacked list.
- Background work started by a notification (e.g. agent completed
  while the app was backgrounded) → bar appears on next foreground
  with "done · 12s ago · 4 new receipts" and auto-dismisses after 5s.

### 4. Edit + regenerate

**Long-press on a user bubble → action sheet.** A 220-dp-wide popover
anchored to the bubble's top-right corner (4 actions + 1 destructive):

1. **Edit & rerun** — primary; ⏎ keyboard hint
2. **Copy** — secondary
3. **Branch from here** — opens ProfileSwitcher, defaults to current profile
4. **Regenerate reply** — re-runs *from this message* with same agent
5. *(separator)*
6. **Delete** — destructive (Coral)

**Edit mode.** The bubble becomes a `HermesField`-style `TextField`
with Indigo border, original text pre-selected, footer actions
(Cancel / Save & rerun). Below the edit field, the *original* message
shows struck-through at 0.45 opacity, with a coral "truncated below"
divider. The thread truncates at this point on Save.

**Branch from here.** Forks the thread; original kept as `Triage · v1`,
fork gets `Triage · v2` chip in the route capsule. Same prefix, fresh
run, profile chosen via ProfileSwitcher (or current).

**Reuse / new.** `HermesField` covers the TextField. New composables:
`MessageActionSheet(items: List<ActionItem>)`, `MessageEditField()`
(text-field with inline save/cancel).

**Edge cases.**
- Edit a message whose agent reply contained a tool call → truncation
  also removes the tool card *and* any later messages that depended on
  its output. Show a "X turns will be removed" count before Save.
- Branch from the *first* message → route capsule still shows `v2`,
  but the thread is treated as a top-level new thread (same as
  `NewThreadDialog` initial mode).
- Branch when an approval is pending → the approval stays on the
  original (v1) thread; the fork starts fresh and may not need it.

### 5. Agent handoff

**Visual contract.** Every assistant bubble carries a profile chip
(top-right of the bubble): a 3-letter monogram in a 24-dp circle, the
profile handle in mono, and the elapsed time. The chip's accent color
is the profile's stable gateway color (see "Profile accent colors"
above). A user bubble that *addresses* a profile gets a recipient
chip above it (`to @knight`) in the same accent.

**HandoffChain visualization.** Two places show the chain:

1. **In the route capsule** (compact): the chain tail appended after
   the thread title, e.g. `› @ash › @knight › @coder › @research`.
   Horizontally scrollable when longer than the capsule.
2. **As a "Cast" card in ContextPanel** (rich): a horizontally-
   scrollable strip of profile tiles joined by `›` glyphs, each tile
   showing monogram + handle + turn count. Below the strip, a one-
   line caption summarises the chain: "coder → knight → coder →
   research". Tap any tile → filter the transcript to that profile's
   turns.

**Reuse / new.** New composables: `ProfileChip(handle, accent)`,
`HandoffChainStrip(profiles: List<Profile>)`. No existing
`HermesComponents` covers profile identity.

**Edge cases.**
- Profile handle not in the user's known profiles (orphan from an old
  thread) → render in muted grey with a "?" icon and a "Re-pair this
  profile" action in the chip's overflow.
- Handoff chain > 8 profiles → strip scrolls horizontally; route
  capsule collapses to "first › … › last".
- User explicitly hands off to themselves (rare) → chip is rendered
  in the user's own profile accent; no "to" recipient chip on the
  bubble (it's implicit).

### 6. Composer upgrade

**Three morph states for the send button** (right edge):

| Composer state      | Send button | Field height        | Notes                                       |
|---------------------|-------------|---------------------|---------------------------------------------|
| Empty               | mic         | 44 dp               | Tap = start dictation, hold = record        |
| Has text            | arrow       | 44 dp → up to 160 dp| Grows multi-line; arrow = send              |
| Recording (mid-press) | stop     | 44 dp               | Coral border, live waveform, "0:04" timer   |

The left "+" button opens the **attachment sheet** (file / photo /
camera / location, 4-up grid). Camera/photo request runtime
permissions; the build subagent should treat denied permissions as
the tile fading to 0.35 opacity with a tap-to-grant behaviour.

**Hold-to-talk.** Press-and-hold the mic:
- 0–300 ms — haptic tick; field border turns Coral
- 300 ms+ — recording starts; live 12-bar waveform animates in the
  field; elapsed timer ticks
- Release — transcript appears in the field; voice clip attaches as
  a media bubble above the field (not inline — the assistant can't
  read audio directly, it just sees the transcript)
- Slide up to cancel — clip discarded, no message sent

**⌘K / Ctrl+K** opens a **quick profile palette** above the composer
— a 3-row menu (search field + matched profiles + footer hint). ↑↓/⏎
to pick. Same palette is reachable by tapping the route capsule.

**Reuse / new.** `HermesField` for the text input (extend with
`singleLine: false` and `maxHeight`). New composables: `Composer`
(wraps the whole row), `AttachmentSheet`, `VoiceRecordingOverlay`,
`QuickProfilePalette`. No new deps — use `androidx.compose.material3`
exposed dropdown menu.

**Edge cases.**
- Field at 160 dp + new text → scroll within field, don't grow.
- Slide-up-to-cancel past the 44 dp threshold is forgiving (50 dp)
  to avoid accidental cancels on bumpy tablets.
- Camera permission denied → photo tile remains visible but tap
  re-prompts; location tile does the same.

### 7. Inline approvals (not modals)

**Inline ApprovalCard lives in the chat thread.** Same surface shape
as a tool-run card (spec 2) but the right-side action row is
`Approve / Deny` instead of a chevron. The card carries:

- Header: shield icon · tool-verb · `@profile · elapsed · awaiting`
- "It wants to" — Sand-bordered inner box with the call + risk
- Meta rows: Risk / Reversible / (varies)
- Two big buttons: Approve (Teal) / Deny (Coral outline)
- Lock-in strip: `Once · This session · Always for this verb ·
  Always deny` (4 chips — same pattern as the existing
  `Grants.dc.html` 4-option grill)

After a decision the card collapses into a one-line **receipt**
(Teal-bordered, "approved · 0.4s · you have now 1 active grant for
this verb") that lives where the approval was, so the thread tells a
clean story.

**Approvals queue (ContextPanel).** A vertical stack of mini-cards
(oldest first), each carrying verb + handle + 36-dp Approve/Deny pair.
Footer carries "Approve all (read-only)" — a batch action that's
**only enabled when every queued verb is read-only** (file.read,
search.query). Mutating verbs always require an explicit decision.

**Reuse / new.** `ApprovalCard` wraps `ToolRunCard` with
`mutating=true`. The lock-in strip is a new composable,
`ApprovalLockInChips(value: LockIn, onChange)`. The ContextPanel
queue is a `LazyColumn` of `ApprovalCard` (compact variant — height
≤ 100 dp).

**Edge cases.**
- Approve while offline → card flips to "queued — will apply when
  reconnected"; the queue in ContextPanel shows a tiny offline dot.
- "Always for this verb" applies across *all* profiles that share the
  verb (not per-profile) — confirm in the chip copy.
- Denial during a chained tool call (e.g. `bash.exec` denied after
  `file.read` succeeded) → the partial output is preserved in the
  thread and the agent gets a one-line "user denied: <reason>" reply.

---

## New composables needed

| Composable                | Spec | Purpose                                          |
|---------------------------|-----:|--------------------------------------------------|
| `StreamingCaret`          |  1   | 1-px Indigo caret, `step-end` blink              |
| `ToolRunCard`             |  2   | Sealed class; 4 verb variants + status enum      |
| `BackgroundWorkBar`       |  3   | 36 dp status row above route capsule             |
| `BackgroundWorkPill`      |  3   | 44 dp floating pill when scrolled up             |
| `MessageActionSheet`      |  4   | Long-press popover (4 actions + Delete)          |
| `MessageEditField`        |  4   | `HermesField`-shaped bubble with inline Save     |
| `ProfileChip`             |  5   | handle + accent + monogram                       |
| `HandoffChainStrip`       |  5   | Horizontal profile tiles joined by › glyphs      |
| `Composer`                |  6   | Full composer row (+, field, send/stop)          |
| `AttachmentSheet`         |  6   | 4-up grid: file/photo/camera/location            |
| `VoiceRecordingOverlay`   |  6   | Live waveform + elapsed + slide-up-to-cancel     |
| `QuickProfilePalette`     |  6   | ⌘K palette above composer                        |
| `ApprovalCard`            |  7   | Wraps `ToolRunCard` with Approve/Deny + locks    |
| `ApprovalLockInChips`     |  7   | 4 chips (Once/Session/Always/Deny)               |

## HermesComponents enhancements needed

| Existing composable         | Enhancement needed                                              |
|-----------------------------|-----------------------------------------------------------------|
| `SurfaceCard`               | Add `collapsible: Boolean` + `expanded: Boolean` API            |
| `StatusBadge`               | Add an Indigo "Live" tone (currently Live = Teal)               |
| `Chip`                      | Add `selected: Boolean` + `selectedBackground`                  |
| `HermesField`               | Add `maxHeight: Dp` + `multiLine: Boolean` for composer field   |
| `HermesButton`              | Add a `destructive: Boolean` variant (Coral text on outline)    |
| `SectionLabel`              | Add a `count: String?` slot so "Approvals (3)" renders inline   |

No new external dependencies. Every animation uses Compose's
built-in `animate*AsState` / `AnimatedContent` / `InfiniteTransition`.

---

## What this replaces from `design/*.dc.html`

- `design/Approval.dc.html` — replaced by **inline ApprovalCard** in
  the chat (spec 7) + the ContextPanel queue. The old bottom-sheet
  flow is gone; decisions happen where the user is already looking.
- `design/Composer.dc.html` — replaced by the new `Composer` with
  three morph states (spec 6). The old fixed two-row layout is gone.
- The "Tool: readFile /path/to/foo" plain-text line in
  `design/Chat.dc.html` — replaced by `ToolRunCard` (spec 2).

---

## Clarifying questions

The build subagent will need answers before Kotlin work starts. The
current mocks pick a default; flipping any of these changes the spec.

1. **Per-token vs per-paragraph streaming.** The mock streams
   per-token. Some models (Claude, Gemini Pro) deliver in larger
   chunks — should the caret pulse on each delta event, or only on
   visible-character boundaries?

2. **Inline approvals for *every* mutating tool?** The brief lists
   bash.exec / git.* as requiring approval. What about `file.write`
   on a file the user has explicitly granted write-access to in
   Grants? Should that auto-pass (no card) or still surface an
   inline card with a one-tap confirm?

3. **Profile accent color source.** The mock assigns colors
   manually. Should the build pull from the gateway's profile
   metadata (`profile.accent` field), let the user customise in
   Settings, or hard-code the 4-color palette?

4. **Handoff trigger surface.** Right now `@knight` in the composer
   is the only way to hand off. Do we also want a long-press on the
   profile chip in the route capsule as a second trigger?

5. **"Always for this verb" scope.** Across all profiles, or per
   profile? The chip copy says "Always for this verb" — ambiguous
   by design. Need an explicit decision.

6. **Voice clip retention.** When the user records voice, the
   transcript goes into the field but the audio file attaches as a
   media bubble. Where is the audio stored (companion cache vs node
   push) and for how long?

7. **Background-work bar collapse threshold.** 80 dp of scroll is the
   mock default. Should the threshold be larger on tablet (where the
   transcript is wider) so the bar stays visible while the user reads
   nearby context?

8. **Edit & rerun on an *assistant* message?** The mock only shows
   edit on user bubbles. Should the same action sheet appear on
   assistant bubbles (regenerate from this assistant turn)?

---

## Validation

- All seven mocks use the same `:root` vars and font `<link>` as
  Phase A → drop-in to the same design canvas.
- All seven mocks are 100% inline styles, no external CSS.
- All touch targets ≥ 36 dp (Compose default ≥ 44 dp where possible).
- Dark theme is the primary render; light-theme is a `prefers-color-
  scheme: light` swap in production.
- Every interactive surface has an `aria-label` on its icon button.
- Mono is used for handles, IDs, file paths, and elapsed-time captions.
- The 7 specs cover every behaviour described in
  `plan/01-product/product.md` for the streaming-first chat thesis.
