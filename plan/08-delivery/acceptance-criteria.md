# Acceptance Criteria for a Production MVP

Source: `../../Hermes-Companion-Plan.md` §14

- Add at least two gateways and enumerate at least two profiles on one gateway.
- Same-name profiles across gateways are unambiguous.
- Stream a real Hermes run with tool progress, reconnect, stop, steer, and approve.
- Proactive delivery lands in the correct gateway/profile/session.
- Kill/restart the app, Node service, and gateway without losing a route or duplicating a user-visible message.
- Pair one Android node; enable Full Node Mode; show a truthful capability health matrix.
- Grant notifications to one profile and deny another; prove no cross-profile or cross-gateway leak.
- Receive real WhatsApp **and Cliq** notifications through `NotificationListenerService` at post time without using ADB/logcat.
- Restart `NotificationListenerService` with active notifications present; reconcile them immediately through `getActiveNotifications()` without a timed poll.
- Demonstrate calls from contacts and unknown callers, including missed/rejected/blocked outcomes after process death.
- Exhaust or disable the configured model/provider during an event: event remains pending/failed, is visible in Activity, and successfully retries after route recovery.
- Verify Companion's own local notification cannot recursively create another agent event.
- Demonstrate one user-approved node action and one denied capability.
- Revoke the node and prove subsequent commands fail closed.
- No token, raw secret, or cross-profile event appears in renderer logs, crash logs, another profile, or another gateway.

## See also

- [08-delivery/production-slices.md](./production-slices.md)
- [05-reliability/offline-behavior.md](../05-reliability/offline-behavior.md)
