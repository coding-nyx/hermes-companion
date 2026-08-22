# Hermes Companion — Project Plans

Status: proposed product architecture and phased PoC plan; no implementation yet.

This folder is the decomposed view of the consolidated `../Hermes-Companion-Plan.md` (single document). The plan is split across sub-directories by domain so each section can be reviewed and updated independently.

## Scope

Android-first mobile companion for the Hermes agent fleet and an optional Android node that gives explicitly-granted device capabilities to selected profiles. **iOS is out of scope** for this project. Desktop, web, and headless node hosts are also out of scope; the Hermes Desktop app already covers the gateway/profile/session surface.

See [SCOPE.md](./SCOPE.md) for the full in-scope / out-of-scope list.

## Structure

| Directory | Contents |
| --- | --- |
| [01-product/](./01-product/) | Product thesis, object model, fleet hierarchy |
| [02-contracts/](./02-contracts/) | Existing Hermes API contracts; new edge contract; node broker envelope; capability groups |
| [03-android/](./03-android/) | Android stack, Full Node Mode, notification/call/event correctness contracts |
| [04-connection/](./04-connection/) | Gateway registry, node pairing, runtime data paths |
| [05-reliability/](./05-reliability/) | Reliability and offline behavior |
| [06-ux/](./06-ux/) | Mobile information architecture |
| [07-privacy/](./07-privacy/) | Privacy model |
| [08-delivery/](./08-delivery/) | Proposed PoC scope, production slices, acceptance criteria |

## Source of truth

Each sub-file cites the section number in `../Hermes-Companion-Plan.md` it was extracted from. The original document is retained alongside this folder as the consolidated view; treat the sub-files as authoritative once edits begin.
