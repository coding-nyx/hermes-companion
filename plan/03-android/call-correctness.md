# Call Correctness Contract

Source: `../../Hermes-Companion-Plan.md` §6.3

- `CallScreeningService` supplies incoming/outgoing screening context.
- `InCallService` plus default-dialer role is required for reliable answer/reject/end and active-call UI.
- CallLog reconciliation runs after calls finish so missed/rejected/blocked outcomes survive process death.
- Contacts lookup occurs locally first. Unknown callers use a configured lookup provider; the plan does not assume access to Truecaller's private database.
- Every call event is durable and always routed to the granted profile(s), including contacts.

## See also

- [02-contracts/capability-groups.md](../02-contracts/capability-groups.md)
- [03-android/full-node-mode.md](./full-node-mode.md)
- [03-android/event-processing.md](./event-processing.md)
