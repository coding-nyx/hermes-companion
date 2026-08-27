# Changelog

## v0.2 (2026-08)

Multi-gateway pairing with one active gateway at a time. Profile + Chat + Notification Routing tabs in Settings. NotificationListenerService hooks the new 5-action router.

### Added

- `ActiveGatewayEntity` + `ActiveGatewayDao` — singleton-table pattern (`PK = 1`, `gatewayId`, `updatedAt`) in Room DB v7. `FleetRepository.observeActive()` / `setActive(gatewayId)`. Destructive migration from v6 (no MIGRATION_6_7).
- `NotificationAction` enum — 5 states: Off / All / ImportantOnly / Mute / ReplyWithRules.
- `NotificationRouter` — pure decision function. Per-package override wins over default. Default important-allowlist: WhatsApp, Telegram, Slack, Discord, Signal, Google Messages, Phone, android shell.
- `NotificationRuleRepository` — per-package override list + global regex list for ReplyWithRules. In-memory for v0.2; Room persistence deferred to v0.3.
- `SettingsScreen` — three horizontal tabs (Gateways / Profiles / Notification Routing) with `rememberSaveable` tab state.
- `GatewaysTab` — list paired gateways; "Make active" + Remove + Add gateway dialog.
- `ProfilesTab` — profiles for the active gateway.
- `NotificationRoutingTab` — per-package rules + reply-with-rules patterns.
- `ProfileTabStrip` — horizontal chip strip, one chip per `(gateway × profile)` pair.
- `ScreenWakeLock` — `PowerManager.SCREEN_BRIGHT_WAKE_LOCK` wrapper acquired while a node task is active; 10-minute watchdog timeout; idempotent `release()`.

### Tests

- `data/repo`: ActiveGatewayEntityTest + ActiveGatewayDaoTest + NotificationRuleRepositoryTest.
- `app`: ChatViewModelTest (bind / empty-send / trim / clear-draft).

### Migration

- DB schema bump v6 → v7. No migration SQL — devices on v6 wipe to v7 on first launch of this build (per the user's "wrecking ball" directive).

### Known gaps (deferred to v0.3)

- Compose UI tests for `ChatScreen` / `ProfileTabStrip` / `NotificationRoutingTab` — `:app` module needs Compose test deps added. Logged as T4.5.
- The HermesNotificationListenerService wires `NotificationRouter` results to the gateway POST (T6 follow-up). Router exists; NLS doesn't call it yet.
- NotificationRuleRepository is in-memory; rules reset on app restart.

## v0.1.x (2026-07)

Single gateway, single profile, single phone, manual setup. PoC debug build.
