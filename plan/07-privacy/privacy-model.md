# Privacy Model

Source: `../../Hermes-Companion-Plan.md` §11

- Event content is minimized on-device before transmission.
- Per-app notification policy: ignore, metadata only, redacted preview, full content.
- Sensitive categories (OTP, banking, health) default to metadata-only.
- Activity log shows who accessed which capability, under which profile, and why.
- Raw event bodies have bounded retention; receipts and audit metadata outlive payloads.
- Profile A cannot read Profile B's node event queue without an explicit grant.
- A device can be revoked from one gateway without affecting another.
- Local biometric gate can protect app launch, approval, node controls, and secret-bearing views.

## See also

- [02-contracts/capability-groups.md](../02-contracts/capability-groups.md)
- [03-android/full-node-mode.md](../03-android/full-node-mode.md)
- [06-ux/information-architecture.md](../06-ux/information-architecture.md)
