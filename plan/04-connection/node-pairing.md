# Node Pairing

Source: `../../Hermes-Companion-Plan.md` §7 (Connection and pairing — node pairing)

1. User adds/tests a gateway.
2. App creates an Ed25519 node keypair in Android Keystore.
3. Gateway displays a short-lived QR/deep link containing gateway ID, pairing nonce, broker URL, and server fingerprint.
4. App proves possession of its private key and the nonce.
5. Gateway returns a node certificate/token bound to the public key and gateway.
6. User grants capabilities separately to profiles on that gateway.
7. Both sides show the same verification phrase before high-impact grants.

Use TLS even on Tailscale. Support certificate pinning as an opt-in, rotation-safe feature. Never put long-lived bearer tokens in QR codes.

## See also

- [04-connection/gateway-registry.md](./gateway-registry.md)
- [02-contracts/edge-contract.md](../02-contracts/edge-contract.md)
- [03-android/full-node-mode.md](../03-android/full-node-mode.md)
