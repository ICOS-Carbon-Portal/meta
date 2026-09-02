# 25 — Unify rdfstore's instance-server configuration view

## Outcome

rdfstore reads the same `cpmeta.instanceServers` and `cpmeta.dataUploadService` configuration
shape as meta. There is no separate `rdfStore.citationGraphs` section.

Meta retains the complete master configuration and its complete case classes. rdfstore defines
narrow read-side case classes for only the fields used to build citation lenses:

- `writeContext` and optional `readContexts` from specific instance servers;
- `commonReadContexts`, `uriPrefix`, and data-object `label`/`format` definitions;
- collection and document instance-server mappings;
- handle `prefix` and `baseUrl`;
- RDF-log names, graph contexts, and optional replay offsets, plus the shared `cpmeta.rdfLog`
  database connection.

The JSON product readers ignore all other fields. Consequently, meta-only ingestion, metaflow,
upload, handle-client, and log-writer settings remain valid in the unified configuration without
becoming part of rdfstore's runtime model.

The former `rdfStore.rdfLogs`, `rdfStore.rdfLogRestoreFromId`, and `rdfStore.rdfLog` sections are
also removed. Restore bindings are derived from `logName`/`writeContext` on specific servers and
`label`/`uriPrefix`/`replayLogFrom` on data-object servers.

This keeps one graph-topology contract and prevents the two services from drifting while retaining
separate application types and dependencies.
