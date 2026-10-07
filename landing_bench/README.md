# LandingBench

Measures how long data object landing pages take to load on an ICOS / SITES
data portal deployment.

It:

1. fetches the portal main page (`https://HOST/portal/`) and reads the
   `window.envri` / `window.envriConfig` the page embeds, to find the meta host;
2. runs the same SPARQL query the portal uses for its default search result list
   (no filters, deprecated objects hidden, newest submissions first, pages of 20)
   against `https://<metaHost>/sparql`;
3. fetches the landing page `https://<metaHost>/objects/<hash>` of each returned
   object, one at a time, timing each request and pausing between requests,
   paging further through the result list as needed until `--max` is reached;
4. optionally (`--secondary HOST`), fetches each landing page from a second meta
   host right after the primary one, times it, and compares the two responses
   (HTTP status and body; bodies are compared as served, except for a short list
   of known environment-specific differences, such as test host names and
   environment badges, listed in `lib/landing_bench/diff.ex`);
   with `--diff`, a unified diff of the bodies is printed for every mismatching
   landing page;
5. optionally (`--store DIR`), stores the run in a local directory, so that it
   can be viewed again later (see below).

## Build

```sh
mix deps.get
mix escript.build
```

## Run

```sh
./landing_bench data.icos-cp.eu --max 50 --delay 2000 --jitter 500
./landing_bench https://datalocal.icos-cp.eu --insecure --max 100 --csv results.csv
./landing_bench data.icos-cp.eu --secondary http://localhost:9094 --csv compare.csv
./landing_bench data.icos-cp.eu --secondary http://localhost:9094 --diff
./landing_bench data.icos-cp.eu --secondary http://localhost:9094 --store runs
./landing_bench --help
```

## Stored runs

With `--store DIR`, everything printed during the run is also recorded in `DIR`,
which can hold any number of runs:

```
DIR/index.json                     all runs: host, options, start/finish time, status, key figures
DIR/RUN_ID/run.json                the run's entry in the index
DIR/RUN_ID/events.jsonl            the run's events, one JSON object per line, written as they happen
DIR/RUN_ID/bodies/SHA256.html.gz   landing page bodies, stored once per distinct body
                                   (only for runs with --secondary)
```

Run IDs are the UTC start time, e.g. `20261007-142355`. Since events are written
as they happen, an interrupted run can be viewed too (it is listed as `unfinished`).

```sh
./landing_bench view runs                   # list the stored runs
./landing_bench view runs 20261007-142355   # print a run as it looked live, with its summary
./landing_bench view runs latest --diff     # ... with diffs of mismatching landing pages
./landing_bench view runs latest --recompare --diff
```

`--recompare` compares the stored bodies again using the current list of known
differences in `lib/landing_bench/diff.ex`, so a run can be re-evaluated after
that list has changed. `--csv PATH` works for stored runs as well.

Timings cover the whole request, including reading the full response body.
HTTP retries are disabled so every measurement is a single request.
