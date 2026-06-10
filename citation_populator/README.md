# citation_populator

The simplest possible way of populating the derived citation triples in the
Virtuoso triplestore, written in Elixir with no dependencies. It is the
unoptimized counterpart of the `CitationMaterializer` in the Scala
`citations` service: no batching, no caching, no prefetching, no
concurrency — just one subject at a time, using raw SPARQL queries and
updates over HTTP.

## What it does

1. Lists all citable subjects (`cpmeta:DataObject`, `cpmeta:DocumentObject`,
   `cpmeta:Collection`) with a SPARQL `SELECT` against `<virtuoso>/sparql`.
2. Lists the subjects that already have triples in the derived citations
   graph and skips them (they are kept, even if stale).
3. For each remaining subject, fetches its freshly computed citation
   metadata from the citations service HTTP API
   (`GET /citations/staticobject?uri=…` or `/citations/staticcollection?uri=…`)
   and writes up to three triples with a SPARQL `INSERT DATA` against
   `<virtuoso>/sparql-auth`:
   * `cpmeta:hasBiblioInfo` — the `References` JSON
   * `cpmeta:hasCitationString` — the plain-text citation
   * `dcterms:license` — the licence IRI

Subjects whose DOI citation is still being fetched from DataCite (the
citations service returns a `Fetching...` placeholder) are retried a few
times and then skipped; failures on individual subjects are logged and
skipped, so a run always processes the whole list.

## Configuration

All via environment variables:

| Variable | Default |
| --- | --- |
| `VIRTUOSO_HOST` | `http://localhost:8890` |
| `VIRTUOSO_USERNAME` | `dba` |
| `VIRTUOSO_PASSWORD` | `dba` |
| `CITATIONS_SERVICE_URL` | `http://127.0.0.1:9095` |
| `DERIVED_CITATIONS_GRAPH` | `http://meta.icos-cp.eu/derived/citations/` |

Updates are sent with Basic auth and fall back to Digest auth if Virtuoso
challenges with it.

## Running

Requires Elixir >= 1.18 (uses the built-in `JSON` module) and a running
citations service.

```sh
cd citation_populator
mix citations.populate
```

or from IEx:

```sh
iex -S mix
iex> CitationPopulator.run()
```
