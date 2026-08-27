# Design Improvement Plan

## Context

The app is functionally broad but its UI was assembled screen-by-screen as features
landed (Node, Full Node Mode, Grants, Discover, Diagnostics, Stream Rules, …). Each
screen hand-rolls its own cards/rows, and the app is missing the `design/` concept's
signature elements — the always-visible **route capsule**, the **fleet switcher**, and
a **thread-centric chat window**. The dark-first Material 3 theme is a good base but:
status colours are dark-palette values reused in light mode (contrast risk), metadata
isn't consistently mono, there's no appearance setting, and the launcher icon is the
default. This plan raises the app to the artboards' bar via a shared design system plus
the missing signature pieces, then polishes each screen. Outcome: the app reads as one
coherent product, not a stack of feature screens.

## 1. Design-system foundations — `:app/ui/theme` + new `:app/ui/components`
- **Fonts:** bundle Roboto + **Roboto Mono** as font resources (the design specifies
  them); point `HermesMono` at real Roboto Mono and apply mono to ALL metadata —
  routes, `seq`, digests, node ids, timestamps, key ids. Fill the remaining M3 text roles.
- **Theme-aware status colours:** `StatusOk/Warn/Error/Dim` are currently single
  (dark) values used in both themes. Define light+dark variants behind a
  `HermesStatusColors` `CompositionLocal`; contrast-check to AA in both themes.
- **Component library** (the biggest win): `HermesCard`, `SectionHeader`, `StatusDot`,
  `TierChip`, `CoverageRow`, `RouteCapsule`, `MetaText` (mono), `ModeSegmented`
  (4-way), `EmptyState`, `LoadingRow`, `ErrorBanner`. Refactor Node / Setup / Grants /
  Discover / Diagnostics / StreamRules / Activity / Outbox onto these so spacing,
  radius, elevation and status semantics are identical everywhere.
- **Scale:** spacing 4/8/12/16/24, one card radius (from `Shapes`), list-item min
  height + all touch targets ≥48dp.
- **Appearance setting:** Light / Dark / System toggle persisted in DataStore, plus an
  optional Material You dynamic-colour opt-in on Android 12+ (brand palette stays default).
- **Brand identity:** adaptive launcher icon + splash (replace the stock icon).

## 2. Information architecture + navigation
- Adopt the design's IA: five destinations — **Chat, Activity, Node, Agents, Settings** —
  with the active route always visible as a capsule (today: 4 tabs, Chat is a detail).
- **Route capsule** on every primary screen: `gw › @profile › session`; tap → fleet
  switcher. Shared component; the single most identity-defining element.
- **Fleet switcher** (modal bottom sheet): gateway → profile → thread, each gateway
  remembering its last profile/thread; long-press for "steer this run". (Coordinates
  with the in-flight chat/threads research.)
- Per-tab saved back-stack state; deep links to an exact route; wide/tablet **3-pane**
  (rail + fleet/threads + chat + optional run/node/queue column) per `Wide.dc.html`.

## 3. Chat window — visual (pairs with the threads research)
- **Thread list** under a profile (`Threads.dc.html`): search, swipe pin/archive,
  unread badge, last-message preview, model-lock chip. "A thread is a route with a
  transcript."
- **New thread** (`NewThread.dc.html`): pick gateway first, name it, set model lock.
- **Transcript:** Markdown + fenced **code-block** rendering (mono + copy), tool-run
  cards with timings + status colour, node-origin event cards (`seq`, capability,
  "judged · worth a ping"), "stopped after 1 of 2 tools", **steer chips**, a streaming
  caret. (Today bubbles are plain `Text`.)
- **Approval sheet:** request-bound target + draft + only the offered options (done) +
  "ask me again"; digest in mono behind the secret-reveal gate.
- **Composer:** four input paths (type / on-device dictate / voice note / realtime
  Talk) with a clear mode switch; Listen (TTS) on the assistant message, not the composer.

## 4. Per-screen polish (to artboard fidelity)
- **Agents/Main:** collapsible gateway groups, health pills, unread badges, capsule.
- **Node/NodeLive:** grouped read/mutating coverage, filter chips, exclusive-lease card
  with holder + live countdown, "what left this phone" privacy log, reconcile/revoke.
- **Full Node Mode:** trust-ladder visual (rungs with connectors) + tier chips + progress.
- **Grants:** per-profile matrix (columns = profiles) with a 4-mode segmented control.
- **Stream Rules:** 4-mode segmented + live redaction preview (Full/Redacted/Metadata) +
  sensitive-category badges.
- **Activity:** the `captured → uploaded → acked → judged → outcome` pipeline bar,
  outcome pills, expandable "why suppressed".
- **Outbox:** sent / in-flight / queued / **no-answer** states with the idempotency note.
- **Diagnostics:** per-route canary checks as pass/fail rows.
- **Discover / Pair:** setup-ladder styling + the verification-phrase display.

## 5. States, motion, accessibility
- One treatment for loading / empty / error / offline (connectivity rendered as data).
- Motion: capsule→switcher transition, expand/collapse, pull-to-refresh, streaming.
- A11y: icon content descriptions, AA contrast both themes, dynamic type, TalkBack order,
  haptics on approval / grant changes.

## 6. Sequencing
1. Foundations (fonts, theme-aware status colours, component library, appearance setting, icon).
2. Route capsule + fleet switcher (+ saved per-tab state).
3. Chat/threads visual (with the threads research plan).
4. Refactor every screen onto the component library.
5. States / motion / a11y pass + wide 3-pane layout.

## Verification
- `adb exec-out screencap` each screen in light + dark on the S22; diff against the
  matching `design/*.dc.html` artboard; run a contrast check and a TalkBack pass.
