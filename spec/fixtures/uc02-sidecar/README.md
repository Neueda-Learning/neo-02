# Local sidecar overlay

This directory is mounted into the unmodified upstream sidecar through
`SCENARIOS_DIR`. It contains:

- ten `pol-demo-approve-*` seed-policy approval examples;
- five `pol-demo-reject-*` seed-policy rejection examples.

The policy demo payloads are documented in
[`docs/sidecar-policy-sample-posts.md`](../../../docs/sidecar-policy-sample-posts.md).
Restart the sidecar after adding or editing an overlay:

```bash
docker compose restart sidecar
```
