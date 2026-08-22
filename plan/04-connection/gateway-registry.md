# Gateway Registry

Source: `../../Hermes-Companion-Plan.md` §7 (Connection and pairing — gateway registry)

## Supported connection kinds

- Local/LAN
- Remote HTTP(S) over LAN/Tailscale
- SSH tunnel
- Hermes Cloud/OAuth

Each connection stores a unique device label, normalized URL/SSH target, auth reference, last capabilities document, profile inventory, and last known health. Tokens live in Keystore-encrypted storage.

## See also

- [04-connection/node-pairing.md](./node-pairing.md)
- [02-contracts/existing-api.md](../02-contracts/existing-api.md)
- [03-android/stack.md](../03-android/stack.md)
