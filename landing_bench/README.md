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
   (HTTP status and body; host names of the respective meta hosts in the body are
   ignored, since landing pages may link back to the host serving them);
   with `--diff`, a unified diff of the (host-normalized) bodies is printed for
   every mismatching landing page.

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
./landing_bench --help
```

Timings cover the whole request, including reading the full response body.
HTTP retries are disabled so every measurement is a single request.
