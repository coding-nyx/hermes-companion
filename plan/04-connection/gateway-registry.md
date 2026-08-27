# Gateway Registry

Source: `../../Hermes-Companion-Plan.md` §7 (Connection and pairing — gateway registry)

## Supported connection kinds

- Local/LAN
- Remote HTTP(S) over LAN/Tailscale
- SSH tunnel
- Hermes Cloud/OAuth

## Discovery

A gateway is found before it is trusted. Discovery proves only that something answered on an address.

- **mDNS/NSD** on the current network, for the same-LAN case with no configuration.
- **Wide-area DNS-SD** across a tailnet, so the phone finds the gateway from anywhere on it.
- **Manual host/port**, which stays the only route for SSH tunnels, Cloud, and anything on an unusual port.

Nothing discovered is added until its TLS identity is verified and, for a node, its pairing phrase matches on both screens.

## Transport privilege tiers

Reachability and privilege are separate questions. The transport caps what a connection may ever be granted.

- TLS (`https://` for the API, `wss://` for the broker), or cleartext to loopback, a `.local` host, or an emulator — eligible for full operator access and node capabilities.
- Cleartext to any other host — **limited access only**: chat and read-only inspection, never a node session, never a capability grant, never a token with write scope.
- The tier is a property of the connection and is re-evaluated on every reconnect. A gateway that silently drops from TLS to cleartext loses its node session rather than keeping it.

## Stored per connection

Each connection stores a unique device label, normalized URL/SSH target, auth reference, last capabilities document, profile inventory, last known health, and its current transport privilege tier. Tokens live in Keystore-encrypted storage.

## See also

- [04-connection/node-pairing.md](./node-pairing.md)
- [02-contracts/existing-api.md](../02-contracts/existing-api.md)
- [03-android/stack.md](../03-android/stack.md)
- [09-parity/openclaw-node-app.md](../09-parity/openclaw-node-app.md)
