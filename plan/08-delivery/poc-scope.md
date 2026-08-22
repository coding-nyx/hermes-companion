# Proposed PoC Scope

Source: `../../Hermes-Companion-Plan.md` §12

The first PoC should prove the interaction and routing model before any real device control:

- two gateways with multiple profiles;
- gateway → profile → session switching;
- state isolated per route;
- chat send and structured mock tool run;
- node actions and simulated notification/call events;
- request-bound approval decisions;
- gateway manager/test interaction;
- responsive mobile and desktop layouts.

The initial PoC should use a local mock server. It must not hold real Hermes tokens, invoke a production agent, or control the S22. Real Hermes API integration begins only after Nyx approves the interaction design and routing model.

## See also

- [08-delivery/production-slices.md](./production-slices.md)
- [08-delivery/acceptance-criteria.md](./acceptance-criteria.md)
