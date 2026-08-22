# Scope

## In scope

- Android mobile companion (Kotlin + Jetpack Compose)
- Hermes plugin `hermes-companion`: platform adapter `companion`, approval transport, bounded node broker, optional commands and a bundled skill
- Pairing with Hermes gateways over LAN, Tailscale, SSH tunnel, or Hermes Cloud/OAuth
- Node capabilities granted per `(gateway, profile, node, capability)`
- Hermes `HERMES_HOME`-profile multiplexing
- Mock-server PoC that exercises the routing model without real Hermes tokens or production agent runs

## Out of scope

- **iOS companion** — dropped from this project. All Android-specific contracts (NotificationListenerService, CallScreeningService, InCallService, default-dialer role, AccessibilityService, etc.) remain because they are the only mobile target.
- macOS / Windows / Linux desktop companion — covered by Hermes Desktop
- Hermes Cloud web client — separate workstream
- Headless node host for `system.run` on non-mobile machines — covered by Hermes Desktop's node-host runtime
- WatchOS direct node, Tailscale-funnel public hosting, multi-tenant operator scoping — separate concerns

## Cross-references

- Original consolidated plan: `../Hermes-Companion-Plan.md`
- See also: [README.md](./README.md)
