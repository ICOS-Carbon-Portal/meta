# citation_populator

A standalone Elixir replacement for the Scala `citations` service's
materializer: it populates the derived citation triples in the Virtuoso
triplestore, computing everything itself from raw SPARQL queries over HTTP
and the DataCite REST API. No triplestore driver, no RDF library, no hex
dependencies (Elixir >= 1.18 for the built-in `JSON` module).

It is deliberately unoptimized: subjects are processed one at a time, each
step is a plain SPARQL query/update or HTTP GET, and there is no caching,
batching, prefetching or concurrency.

## What it does

For every `cpmeta:DataObject`, `cpmeta:DocumentObject` and `cpmeta:Collection`
that has no triples in the derived citations graph yet (already-materialized
subjects are kept, even if stale), it computes and inserts:

* `cpmeta:hasBiblioInfo` — the `References` JSON, in the exact shape meta's
  spray-json formats parse back (`MaterializedCitationInfoProvider`),
  including the full DataCite `DoiMeta` for DOI-minted subjects
* `cpmeta:hasCitationString` — the plain-text citation
* `dcterms:license` — the licence IRI

The computation is a port of the Scala citation stack:

| Scala | here |
| --- | --- |
| `CitationMaterializer` main loop | `CitationPopulator` |
| `LiveCitationMaker` / `CitationMaker` | `Citation`, `References` |
| `CitationClient` / doi-core `DoiClient` | `DataCite` |
| `AttributionProvider` | `Attribution` |
| `StructuredCitations` | `Structured` |
| `StaticObjectReader` / `CollectionReader` (citation-relevant parts) | `Reader`, `Agent`, `Columns`, `Licence` |
| `EnvriResolver` / envri + handle config | `Envri` |

Behavior notes, mirroring the Scala service:

* Subjects with a DOI get their citation strings (elsevier-harvard HTML,
  BibTeX, RIS) and DOI metadata from DataCite's public REST API; if any
  DataCite lookup fails the subject is skipped for this run (the Scala
  materializer's DataCite-queue failure behavior) — never materialized
  with placeholder text. Transient DataCite errors (429/5xx) are retried.
* Subjects without a DOI get structural citations (ICOS / SITES / ICOS
  Cities / document variants), attribution-based author lists, funding
  acknowledgements and BibTeX/RIS assembled from triplestore data.
* The licence chain is: own `dcterms:license` (ignoring the derived graph),
  spec-implied, project-implied, ENVRI default.
* Subjects outside the known object/collection URI prefixes get only the
  DOI citation string, if any.
* Where the Scala code hard-fails the whole subject on missing single-valued
  data (e.g. a licence without a label), this port is lenient and falls back
  to something sensible instead; such spots are commented in the code.

## Configuration

All via environment variables:

| Variable | Default |
| --- | --- |
| `VIRTUOSO_HOST` | `http://localhost:8890` |
| `VIRTUOSO_USERNAME` | `dba` |
| `VIRTUOSO_PASSWORD` | `dba` |
| `DERIVED_CITATIONS_GRAPH` | `http://meta.icos-cp.eu/derived/citations/` |

Queries go unauthenticated to `<host>/sparql`; updates go to
`<host>/sparql-auth` with Basic auth, falling back to Digest auth when
Virtuoso challenges with it.

## Running

```sh
cd citation_populator
mix citations.populate
```

or interactively:

```sh
iex -S mix
iex> CitationPopulator.run()
```

`mix test` runs unit tests for the pure logic (temporal coverage display,
DOI/ORCID parsing, BibTeX/RIS assembly, the DataCite→DoiMeta JSON mapping,
ENVRI inference).

## Not included

The Scala citations service also serves freshly-computed
`/citations/staticobject` & `/citations/staticcollection` JSON over HTTP for
meta's DOI-minting path, and citation-cache dump/drop endpoints. This tool
only replaces the materialization side.
