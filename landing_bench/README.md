# LandingBench

Measures how long data object landing pages take to load on an ICOS / SITES
meta host, optionally comparing them with those of a second meta host.

It:

1. fetches `/buildInfo` from PRIMARY (and SECONDARY, if given), and prints and
   records the response (also in the summary), so that a run shows which versions
   of meta it ran against;
2. runs the same SPARQL query the data portal uses for its default search result
   list (no filters, deprecated objects hidden, newest submissions first, pages of
   20) against `https://PRIMARY/sparql`, for the ENVRI given with `--envri`
   (default `ICOS`);
2. fetches the landing page `https://PRIMARY/objects/<hash>` of each returned
   object, one at a time, timing each request and pausing between requests,
   paging further through the result list as needed until `--max` is reached
   (with `--object ID`, only the landing page of that one data object, given by
   its hash or its URI / landing page URL, is fetched instead, and no SPARQL
   query is made);
4. optionally (with a second meta host, SECONDARY), fetches each landing page
   from SECONDARY right after PRIMARY, times it, and compares the two responses
   (HTTP status and body; bodies are compared as served, except for a short list
   of known environment-specific differences, such as test host names and
   environment badges, listed in `lib/landing_bench/diff.ex`);
   with `--diff`, a unified diff of the bodies is printed for every mismatching
   landing page;
5. optionally (`--store DIR`), stores the run in a local directory, so that it
   can be viewed again later (see below).

## Run

Run from this directory, as a Mix task (it is compiled as needed):

```sh
mix deps.get
mix bench meta.icos-cp.eu --max 50 --delay 2000 --jitter 500
mix bench https://metalocal.icos-cp.eu --insecure --max 100 --csv results.csv
mix bench meta.fieldsites.se --envri SITES
mix bench meta.icos-cp.eu http://localhost:9094 --csv compare.csv
mix bench meta.icos-cp.eu http://localhost:9094 --diff
mix bench meta.icos-cp.eu http://localhost:9094 --object https://meta.icos-cp.eu/objects/<hash> --diff
mix bench meta.icos-cp.eu http://localhost:9094 --store
mix bench meta.icos-cp.eu http://localhost:9094 --store runs
mix bench --help
```

## Stored runs

With `--store DIR`, everything printed during the run is also recorded in `DIR`,
which can hold any number of runs. A bare `--store` (last, or followed by another
option) stores in `./landing_bench_runs`. Do not put a bare `--store` directly
before PRIMARY or SECONDARY, as that host would then be taken as the directory.

```
DIR/index.json                     all runs: host, options, start/finish time, status, key figures
DIR/RUN_ID/run.json                the run's entry in the index
DIR/RUN_ID/events.jsonl            the run's events, one JSON object per line, written as they happen
DIR/RUN_ID/bodies/SHA256.html.gz   landing page bodies, stored once per distinct body
                                   (only for runs with SECONDARY)
```

Run IDs are the UTC start time, e.g. `20261007-142355`. Since events are written
as they happen, an interrupted run can be viewed too (it is listed as `unfinished`).

```sh
mix bench view                        # list the runs in ./landing_bench_runs
mix bench view latest --diff          # show a run from ./landing_bench_runs
mix bench view runs                   # list the stored runs in runs/
mix bench view runs 20261007-142355   # print a run as it looked live, with its summary
mix bench view runs latest --diff     # ... with diffs of mismatching landing pages
mix bench view runs latest --recompare --diff
```

`--recompare` compares the stored bodies again using the current list of known
differences in `lib/landing_bench/diff.ex`, so a run can be re-evaluated after
that list has changed. `--csv PATH` works for stored runs as well.

Timings cover the whole request, including reading the full response body.
HTTP retries are disabled so every measurement is a single request.
