# Hermes Companion app — install + grant permissions

The Companion app installs as a normal Android app. It does **not** require root or ADB after the first install — only the NotificationListener permission and the battery-optimization exemption are needed for normal operation.

## Install (first time)

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or for a release build:
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Required permissions

The app declares the following in the manifest. Each one needs an explicit grant:

1. **`BIND_NOTIFICATION_LISTENER_SERVICE`** — system permission. The user grants this in:
   `Settings → Notifications → Device & app notifications → Special access → Notification access → Hermes Companion → ON`
   The app's first launch shows a deep-link to this screen if not already granted.

2. **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`** — the user must allow background work. Without it, Android may kill the NotificationListenerService within minutes of the screen going off.

3. **Notification policy permission** — required to dismiss notifications when the user asks Hermes to do so (via `/node/{id}/invoke` → `notifications.dismiss`).

## Pairing

1. Ensure the gateway daemon is running on the host (see gateway's `docs/daemon-install.md`).
2. Note the setup code: `curl http://<host>:9120/admin/setup-code` or open the admin console at `http://<host>:9120/admin/`.
3. In the app: **Node** tab → **Pair as node** → enter `http://<host>:9120` + the setup code.
4. The phone opens `ws://<host>:9120/ws/node?token=…` and stays connected.
5. Grant the requested capabilities at pairing time. The OTP feature needs `notifications.reply` granted.

## Switching the active gateway

Settings → Gateways tab → **Make active** on any paired gateway. The singleton row in `active_gateway` updates; the next event from any source routes to the new active gateway.

If you switch to a gateway whose daemon is offline, no events route — pick an online one.

## Notification routing

Settings → Notification Routing tab. The default action is `ImportantOnly`, which forwards notifications from the comms / phone / system packages. Per-package overrides win:

- `All` — forward every notification from this app.
- `Mute this app` — never forward.
- `Reply with rules` — forward only if title or text matches a regex.

The T5A `NotificationRouter` is the source of truth. Changes apply to the next notification — no restart.

## Sleep / wake behavior

While a node task is active (Hermes is controlling the device), the app holds a 10-minute screen-on wake-lock. The wake-lock auto-releases on task completion; a forgotten release is capped by the timeout, so battery drain is bounded.
