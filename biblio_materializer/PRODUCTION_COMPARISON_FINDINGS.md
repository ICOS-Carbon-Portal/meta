# Citation triple comparison: local vs. production (`meta.icos-cp.eu/sparql`)

Findings from running `mix compare_citations --endpoint https://meta.icos-cp.eu/sparql`
against the local populator run, followed by a deeper per-field analysis of the
mismatches (script: `scratchpad/analyze_mismatches.exs`, not checked in).

Of 2022 citable subjects: **1676 matched, 346 mismatched.**

## 1. Off-by-one end date — 272 subjects (79% of all mismatches)

The dominant category. `hasCitationString` differs by exactly one calendar day
in the temporal-coverage end date (e.g. local `2017-11-01` vs remote
`2017-10-31`), and the same date leaks into `hasBiblioInfo`'s `citationBibTex`
(330 subjects), `citationRis` (277), inner `citationString` (277) and
`temporalCoverageDisplay` (273) fields — that's why those four JSON fields
dominate the "which field differs" breakdown. Looks like a systematic
boundary/rounding difference (inclusive vs. exclusive end-of-day, or a
timezone truncation) between the Elixir port's temporal-coverage computation
and production's. Worth checking `Citation`/`Reader`'s handling of
`hasEndTime` against the Scala original.

## 2. Missing `dcterms:license` on collections — 14 subjects

All 14 are `.../collections/...` URIs where local has no license triple at
all (`nil`) but production returns `sitesLicence`. Root cause traced to
`References.build_collection/2` (`lib/biblio_materializer/references.ex:113`):
it never calls `Licence.resolve/6` — it only ever sets `"title"` in `refs`,
so `Writer.licence_triple/2` (which reads `refs["licence"]["url"]`) never
fires for collections. Production's live computation falls back to the ENVRI
default licence for collections; the port doesn't. **Confirmed bug**, not
data drift.

## 3. Two DOI-sharing collection pairs get swapped content — 4 subjects (subset of #2)

Two pairs of collections share one DOI each. Locally, each subject correctly
gets its own title (one has a "- collection of protocols" suffix, the other
doesn't). On production, both subjects under a shared DOI return identical
content — including a `url` field in the JSON pointing at the *other*
collection's URI. Production appears to cache/compute per-DOI rather than
per-subject here, so one of each pair gets the wrong subject's citation.
Local is arguably more correct than production in this case.

## 4. Author-list differences — 53 subjects (inside `hasBiblioInfo` only)

`citationString` matches but the `authors` array in `hasBiblioInfo` differs.
Not root-caused yet — isolated to the JSON structure only, not the plain-text
citation, so likely an author-list formatting/dedup/ordering difference in
the DOI metadata mapping. Candidate for a follow-up pass.

## 5. Stale DOI metadata — 8 subjects

The full embedded DataCite `doi` block differs (e.g. a `fundingReferences`
entry missing a `schemeURI` key locally that's present remotely). Looks like
ordinary drift — whichever side's cache was refreshed later reflects a newer
DataCite record — rather than a mapping bug, since the shape matches
DataCite's schema on both sides.

## 6. Fully missing locally — 3 subjects

Local has **no** `hasCitationString`/`hasBiblioInfo` at all for subjects
production successfully computes (example:
`https://meta.fieldsites.se/objects/_Eu76kOwEaReq39cluHGHGdG`, a DOI'd
"Description"-type document object). This means `References.build/4`
returned `:none` for these locally — a real coverage gap in the port for
this object/DOI-type combination. Worth investigating separately.

## Summary

| # | Category | Subjects | Status |
|---|---|---|---|
| 1 | Off-by-one end date | 272 | Root cause not yet located — needs investigation |
| 2 | Missing collection licence | 14 | **Confirmed bug** — `build_collection/2` never resolves licence |
| 3 | Swapped DOI-sharing collection pairs | 4 (⊂ #2) | Production-side issue, not a local bug |
| 4 | Author-list differences | 53 | Not root-caused |
| 5 | Stale DOI metadata | 8 | Likely benign drift |
| 6 | Fully missing locally | 3 | Coverage gap — `References.build/4` returns `:none` |

Fixing #1 and #2 first is the highest-leverage move: together they account
for ~83% of all mismatches and both have a concrete, confirmed cause (or a
clear code location to start from).
