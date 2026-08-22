# Full Node Mode

Source: `../../Hermes-Companion-Plan.md` §6.1

The app offers an explicit **Full Node Mode** onboarding path. "Full" means every capability Android exposes to an ordinary installed app after Nyx grants the required permissions, roles, and system settings; it does not pretend app sandboxing disappears.

## Setup checklist (live and testable)

- Notification access enabled
- battery optimization set to unrestricted and Samsung background/autostart guidance completed
- contacts and call-log permissions granted
- call-screening role granted; default dialer role offered when answer/reject/full in-call control is wanted
- SMS role offered only when SMS read/send is wanted and the install channel permits it
- accessibility enabled only for screen/input automation
- MediaProjection consent present for each capture session unless Android supplies a durable grant
- microphone, camera, location, nearby-device, and file grants shown separately
- Tailscale/network reachability and TLS identity verified
- per-gateway and per-profile capability grants reviewed

## Coverage matrix

The Node page displays a coverage matrix (`working`, `permission missing`, `OS-limited`, `temporarily unavailable`) rather than one misleading "connected" badge.

## Advanced adapter tiers

Optional advanced adapters are separate trust tiers:

- **Standard** — public Android APIs only
- **Accessibility** — UI inspection/input where Android permits
- **Shizuku/ADB** — elevated shell APIs with explicit setup
- **Device owner/root** — only on deliberately managed/rooted devices

No advanced tier is silently assumed or required for chat/notification reliability.

## See also

- [02-contracts/capability-groups.md](../02-contracts/capability-groups.md)
- [03-android/stack.md](./stack.md)
- [03-android/notification-correctness.md](./notification-correctness.md)
- [04-connection/node-pairing.md](../04-connection/node-pairing.md)
