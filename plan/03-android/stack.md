# Android Application — Stack

Source: `../../Hermes-Companion-Plan.md` §6 (Android application stack)

## Recommended production stack

- Kotlin + Jetpack Compose
- OkHttp/Ktor for HTTP, SSE, and WebSocket
- Room for gateway registry, session cache, **event outbox**, delivery receipts, and audit state
- WorkManager for bounded reconciliation/retry
- Android Keystore for device keys and encrypted token envelopes
- a user-visible foreground Node service for the persistent broker connection
- `NotificationListenerService` as the notification source of truth
- Telecom APIs, `CallScreeningService`, and `InCallService` with the required Android roles for complete call handling
- `ContactsContract` + `CallLog` providers under explicit permissions
- Storage Access Framework for explicit file grants
- MediaProjection for user-consented screen capture
- AccessibilityService only for the separately-enabled full-control capability
- FCM or UnifiedPush only as a wake hint; payloads remain encrypted and are fetched from the gateway

The renderer/UI process never receives raw gateway tokens. A connection service owns credentials and signs requests, mirroring Hermes Desktop's main-process token boundary.

## See also

- [03-android/full-node-mode.md](./full-node-mode.md)
- [03-android/notification-correctness.md](./notification-correctness.md)
- [03-android/call-correctness.md](./call-correctness.md)
- [04-connection/runtime-paths.md](../04-connection/runtime-paths.md)
