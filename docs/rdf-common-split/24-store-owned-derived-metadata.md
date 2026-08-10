# 24 — Make rdfStore own derived metadata

## Goal

Move the runtime ownership of citation, bibliography and inferred licence values to `rdfStore`.
`meta` reads those values through a private, versioned batch route instead of constructing a
second citation provider and maintaining a second DOI cache.

## Checklist

- [x] Define a small, RDF4J-free wire contract for derived metadata.
- [x] Add a store-local service which is the single implementation used by both the SPARQL
  statement enricher and the internal HTTP route.
- [x] Add `POST /internal/derived/v1/resolve`, accepting a batch of resource URIs.
- [x] Add a meta-side HTTP client for that route.
- [x] Wire the route into the standalone rdfStore process.
- [x] Route DOI minting through rdfstore-derived references, including recursively contained
  collection members.
- [x] Route linked-data object and collection landing pages through rdfstore-derived references
  without blocking the HTTP dispatcher.
- [ ] Replace the remaining synchronous meta-side `CitationProvider` construction with the
  client-backed reader adapter. This is deliberately a follow-up: the old reader API returns
  `Validated` synchronously, whereas the process boundary is asynchronous. Converting the
  landing-page/DOI call chain to `Future` is required to do this without blocking Akka dispatchers.
- [ ] Once the async reader migration is complete, physically move the citation implementation
  from `rdf-common` to `rdfstore` and remove meta's cache bootstrapping.

## Contract

The endpoint returns a per-resource status rather than failing a whole batch. A missing or
temporarily unavailable DOI therefore cannot turn a successful RDF read into a 500 response.
The store's SPARQL magic predicates and this endpoint must be backed by the same
`DerivedMetadataService` result.

## Verification

- A route test covers ready, unknown and batch responses.
- A parity test must compare the three magic predicates with the HTTP representation for a
  seeded object before the old meta-side provider is removed.
- The endpoint must stay private to the service network/reverse proxy; it is not a public API.
