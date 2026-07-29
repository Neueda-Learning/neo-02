# Implementation TODO

## Upstream blockers

- [ ] Confirm the authoritative Customer Registry HTTP contract with the instructor:
  method and path, request parameters, response JSON, error semantics, authentication, and
  timeout. The v5 brief requires orchestrator HTTP in integration but does not define these
  details; current `neo-00` and `neobank-sidecar` expose no Registry endpoint.
- [ ] Switch deployed integration/prod configuration to `REGISTRY_MODE=http` and provide
  `REGISTRY_LOOKUP_URL` after that contract is published. The client currently expects the
  minimal response `{"activeProductHeld": boolean}`.
- [ ] Ask upstream to publish a sidecar release containing the official v5 `app-*` corpus.
  The public sidecar currently has only `v1`, equal to `main`. This repo mounts local UC02
  overlays and tests them without copying sidecar source.

## Later use cases

- [x] UC03: `GET /cases/{applicationId}/applicant` is a live orchestrator proxy; Decision
  Detail hydrates its applicant sidebar independently, exposes retry on upstream failure, and
  stores or caches no applicant data.
- [ ] UC00 contract migration, deferred pending review: decide and implement the v5
  `/api/v1/policy/execute` path, `check-policy` command, `outputs` block, acknowledgement shape,
  sidecar/orchestrator compatibility, and OpenAPI tests as one coordinated change.
