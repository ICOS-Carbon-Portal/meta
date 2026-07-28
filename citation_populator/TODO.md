# TODO

## Run-scoped lifecycle

- [x] Introduce a dynamically-created population run supervisor.
  - The run owns the cache and DataCite queue; the queue owns its task supervisor.
  - All run children stop when `CitationPopulator.run/0` finishes.
  - A later run receives fresh state.
- [x] Make the cache run-local instead of using a globally named ETS table.
  - A run context carries the cache table through the citation-building path.
  - There is no cache reset API or persistent cache lifetime.
- [x] Make the DataCite queue run-local as part of the same context.
  - Queue calls use the queue owned by the current run.
  - Queue insertion is synchronous, so every deferred job is registered before its worker completes.
- [x] Associate the queue task supervisor with the run-owned queue lifecycle.

## Simplify citation assembly

- [x] Extract the shared structured-reference construction from
  `References.build_data/3` and `References.build_doc/3`.
  - Centralize BibTeX/RIS generation, keywords, authors, title, licence, and citation text.
  - Preserve the existing document/data-specific fields and output shape.
- [x] Consolidate repeated RDF parsing helpers where this improves readability.
  - Keep malformed-value behavior unchanged (`nil` rather than raising).
- [x] Review the DataCite attribute mapping for a small shared helper for
  repeated optional-field/list transformations.
  - Keep the final doi-core-compatible field names explicit.

## Configuration and startup correctness

- [x] Resolve the `MAX_CONCURRENCY` default mismatch between `README.md`, module documentation, and `config/runtime.exs`.
- [x] Update `Application.run_and_stop/0` to rescue ordinary exceptions as well as caught exits/throws.
  - Preserve logging and non-zero shutdown status on failure.
- [x] Verify that the run supervisor shuts down cleanly on normal and abnormal shutdown, and that failed queue tasks are drained.

## Verification

- [x] Add tests proving two sequential caches do not share reference data.
- [x] Add tests proving queued jobs are drained before the queue returns.
- [x] Add tests for run cleanup after normal and abnormal shutdown.
- [x] Run `mix test` after the refactor.
- [ ] Run `mix format --check-formatted` once the repository's existing formatter drift is resolved.
