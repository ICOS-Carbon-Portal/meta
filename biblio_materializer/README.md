# biblio_materializer

A standalone Elixir replacement for the Scala `citations` service's
materializer: it populates the derived biblio-info triples in the Virtuoso
triplestore, computing everything itself from raw SPARQL queries over HTTP
and the DataCite REST API. No triplestore driver, no RDF library; HTTP via
[Req](https://hexdocs.pm/req) (the only dependency), JSON via the built-in
`JSON` module (Elixir >= 1.18).

Subjects are processed concurrently (`MAX_CONCURRENCY` workers, default 16),
but each subject is still handled in the plainest possible way: a sequence
of SPARQL queries, DataCite GETs (rate-limited by the DataCite queue, one
request per 150 ms) and one SPARQL `INSERT DATA` — no caching, batching
or prefetching.

## What it does

It streams all `cpmeta:DataObject`, `cpmeta:DocumentObject` and
`cpmeta:Collection` subjects with a cursor-paged SPARQL query (counts come
from COUNT queries, so server result-set caps cannot truncate anything),
and for every subject that has no triples in the derived citations graph
yet (already-materialized subjects are kept, even if stale; the check is
per subject), it computes and inserts:

* `cpmeta:hasBiblioInfo` — the `References` JSON, in the exact shape meta's
  spray-json formats parse back (`MaterializedCitationInfoProvider`),
  including the full DataCite `DoiMeta` for DOI-minted subjects
* `cpmeta:hasCitationString` — the plain-text citation
* `dcterms:license` — the licence IRI

The computation is a port of the Scala citation stack:

| Scala | here |
| --- | --- |
| `CitationMaterializer` main loop | `BiblioMaterializer` |
| `LiveCitationMaker` / `CitationMaker` | `Citation`, `References` |
| `CitationClient` / doi-core `DoiClient` | `DataCite` |
| `AttributionProvider` | `Attribution` |
| `StructuredCitations` | `Structured` |
| `StaticObjectReader` / `CollectionReader` (citation-relevant parts) | `Reader`, `Agent`, `Columns`, `Licence` |
| `EnvriResolver` / envri + handle config | `Envri` |

Behavior notes, mirroring the Scala service:

* Subjects with a DOI are not handled inline: the worker writes their
  DataCite-independent triples (the licence) immediately and pushes a job to
  the `DataCiteQueue` GenServer, which fetches the citation strings
  (elsevier-harvard HTML, BibTeX, RIS) and DOI metadata from DataCite's
  public REST API on its own task pool and writes `hasBiblioInfo` +
  `hasCitationString` as the lookups complete, concurrently with the main
  pass. A run finishes when both the main pass and the queue are done. If a
  DataCite lookup fails, the subject's remaining triples are skipped for
  this run — never materialized with placeholder text (note: the
  already-written licence triple makes such a subject count as materialized
  on later runs). Transient DataCite errors (429/5xx) are retried.
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
| `MAX_CONCURRENCY` | `16` |

Queries go unauthenticated to `<host>/sparql`; updates go to
`<host>/sparql-auth` with Basic auth, falling back to Digest auth when
Virtuoso challenges with it.

## Running

Starting the application runs a population pass immediately and repeats it
every hour:

```sh
cd biblio_materializer
mix run --no-halt
```

The same happens for a release (`bin/biblio_materializer start`).

Pass `--single` to run one population pass and stop the VM, preserving the old
one-shot behavior (including a non-zero exit status if the pass aborts):

```sh
mix run --no-halt -- --single
```

`mix test` runs unit tests for the pure logic (temporal coverage display,
DOI/ORCID parsing, BibTeX/RIS assembly, the DataCite→DoiMeta JSON mapping,
ENVRI inference).

## Docker

The `Dockerfile` builds a self-contained mix release on Ubuntu 24.04 (both the
build and runtime stages). The container runs continuously, with one population
pass per hour:

```sh
docker build -t biblio_materializer .
docker run --rm \
  -e VIRTUOSO_HOST=http://virtuoso:8890 \
  -e VIRTUOSO_USERNAME=dba -e VIRTUOSO_PASSWORD=secret \
  biblio_materializer
```

(`localhost` inside the container is the container itself, so point
`VIRTUOSO_HOST` at the reachable Virtuoso address.)

## Not included

The Scala citations service also serves freshly-computed
`/citations/staticobject` & `/citations/staticcollection` JSON over HTTP for
meta's DOI-minting path, and citation-cache dump/drop endpoints. This tool
only replaces the materialization side.
