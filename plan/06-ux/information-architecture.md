# UX Information Architecture

Source: `../../Hermes-Companion-Plan.md` §10

## Primary mobile destinations

1. **Chat** — transcript, streaming tool cards, attachments, voice, stop/steer.
2. **Activity** — calls, notifications, jobs, node events, receipts; filter by gateway/profile/node.
3. **Node** — live device state, capability grants, quick actions, privacy log.
4. **Agents** — union roster grouped by gateway; profile health/unread/busy.
5. **Settings** — gateways, auth, node pairing, delivery, privacy, diagnostics.

## Active route indicator

The active route is always visible as a compact gateway/profile capsule. Long-press or swipe opens the fleet switcher. Switching gateway restores that gateway's last profile and session.

## Approval UX

Approvals appear as structured sheets tied to a request digest, exact profile, gateway, and command. Choices are only those Hermes offered (`once`, `session`, `always`, `deny`).

## See also

- [01-product/overview.md](../01-product/overview.md)
- [07-privacy/privacy-model.md](../07-privacy/privacy-model.md)
- [08-delivery/poc-scope.md](../08-delivery/poc-scope.md)
