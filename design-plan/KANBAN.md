# Hermes Companion — Completion Kanban  ✅ COMPLETE

## DONE
- Design canvas published (blueprint + 3 wireframes)
- Component library flexible; capsule + fleet switcher in Shell
- M1: ALL UI screens refactored onto shared components (HermesCard/SectionHeader/MetaText/StatusDot) via 4 parallel agents
- M2 fix: baseline build was RED (pipe masked exit code) — root-caused: WIP added required ruleRepo param to NotificationRoutingTab w/o wiring. Wired CompanionData→AppModule→SettingsViewModel→call site.
- M2 fix: first-run demo mock fleet seeded (Home: ash,misty · Cloud: ash,atlas; "ash" on both → disambiguation). mock:// scheme → in-process MockHermesBackend, no tokens, no network, empty-store-only.
- M3: :app:assembleDebug BUILD SUCCESSFUL; :data:repo + :app unit tests green
- M4: S22 (SM-S901E, Android 16) reconnected over tailscale adb; uninstalled stale, installed fresh APK = Success
- M4 verify: versionCode=1 (matches), versionName=0.1.0-poc-debug, launcher activity resolves (icon in drawer), app launches, process alive, no FATAL

## PoC-scope: all items satisfied
- two gateways w/ multiple profiles ✓ · gw→profile→session switching ✓ · state isolated per route ✓
- chat send + mock tool run ✓ · node actions (canary/grants/matrix) ✓
- simulated notif/call events: via canary round-trip + capability matrix + Activity kinds (no fabricated rows) ✓
- request-bound approvals ✓ · gateway manager/test ✓ · responsive mobile+desktop ✓ · local mock, no tokens ✓
