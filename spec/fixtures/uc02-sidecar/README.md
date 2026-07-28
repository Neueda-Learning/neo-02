# UC02 sidecar overlay

These four envelopes are mounted into the unmodified upstream sidecar through
`SCENARIOS_DIR`. Start the compose stack, then run:

```powershell
.\scripts\test-uc02-sidecar.ps1
```

The script resets this module's local case tables, sends the four exact checkpoints plus
17 clean fillers in durable acceptance order, waits for callbacks, and verifies that
`app-1287` is the 21st decision. The fixtures intentionally use the current template
`/api/v1/applications` contract; the v5 execute-contract migration is deferred to UC00.
