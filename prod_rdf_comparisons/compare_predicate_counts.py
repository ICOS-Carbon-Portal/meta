#!/usr/bin/env python3
"""Compare per-predicate triple counts between two SPARQL endpoints.

Runs `SELECT ?p (COUNT(*) AS ?c) WHERE { ?s ?p ?o } GROUP BY ?p` against each
endpoint and reports predicates whose counts differ (including predicates
present in one store but not the other).

Usage:
    ./compare_predicate_counts.py <endpoint_a> <endpoint_b>
    ./compare_predicate_counts.py http://host-a:3030/ds/sparql http://host-b:3030/ds/sparql

Options:
    --timeout <secs>        Per-request timeout (default: 300).
    --ignore-prefix <iri>   Ignore predicates whose IRI starts with this prefix
                            (repeatable).

Exit status: 0 if all predicate counts match, 1 if any differ, 2 on error.
"""

import argparse
import sys
import urllib.parse
import urllib.request


# Predicate prefixes ignored by default. Override/extend with --ignore-prefix.
DEFAULT_IGNORE_PREFIXES = [
        "http://www.openlinksw.com/schemas/virtrdf",
        "http://www.w3.org/1999/02/22-rdf-syntax-ns",
        "http://www.w3.org/2000/01/rdf-schema#subPropertyOf",
        "http://www.w3.org/2000/01/rdf-schema#subClassOf",
        "http://www.w3.org/2000/01/rdf-schema#sameAs",
        "http://www.w3.org/ns/sparql-service-description"
]


def run_query(endpoint, query, timeout):
    """POST a SPARQL query and return the parsed JSON results payload."""
    import json

    data = urllib.parse.urlencode({"query": query}).encode("utf-8")
    req = urllib.request.Request(endpoint, data=data)
    req.add_header("Accept", "application/sparql-results+json")
    req.add_header("Content-Type", "application/x-www-form-urlencoded")
    req.add_header("Cache-Control", "no-cache")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.load(resp)

def fetch_total(endpoint, timeout):
    """Return the total triple count from a SPARQL endpoint."""
    total_query = "SELECT (COUNT(*) AS ?c) WHERE { ?s ?p ?o }"
    payload = run_query(endpoint, total_query, timeout)
    rows = payload["results"]["bindings"]
    if not rows or "c" not in rows[0]:
        return 0
    return int(rows[0]["c"]["value"])


def fetch_counts(endpoint, timeout, ignore_prefixes=()):
    """Return {predicate_iri: count} from a SPARQL endpoint.

    Predicates whose IRI starts with any string in ``ignore_prefixes`` are
    excluded from the result.
    """
    query = "SELECT ?p (COUNT(*) AS ?c) WHERE { ?s ?p ?o } GROUP BY ?p"
    payload = run_query(endpoint, query, timeout)

    counts = {}
    for row in payload["results"]["bindings"]:
        # A predicate is always an IRI; guard against unbound just in case.
        if "p" not in row:
            continue
        predicate = row["p"]["value"]
        if any(predicate.startswith(prefix) for prefix in ignore_prefixes):
            continue
        counts[predicate] = int(row["c"]["value"])
    return counts


def main():
    parser = argparse.ArgumentParser(
        description="Compare per-predicate triple counts between two SPARQL endpoints."
    )
    parser.add_argument("endpoint_a", help="SPARQL query URL of the first store")
    parser.add_argument("endpoint_b", help="SPARQL query URL of the second store")
    parser.add_argument("--timeout", type=int, default=9000000, help="Per-request timeout in seconds")
    parser.add_argument(
        "--ignore-prefix",
        action="append",
        default=[],
        metavar="IRI",
        help="Ignore predicates whose IRI starts with this prefix (repeatable)",
    )
    args = parser.parse_args()

    ignore_prefixes = DEFAULT_IGNORE_PREFIXES + args.ignore_prefix

    # First, get the total triple count from each endpoint.
    try:
        grand_total_a = fetch_total(args.endpoint_a, args.timeout)
    except Exception as exc:
        print(f"error: failed to query endpoint A ({args.endpoint_a}): {exc}", file=sys.stderr)
        return 2
    try:
        grand_total_b = fetch_total(args.endpoint_b, args.timeout)
    except Exception as exc:
        print(f"error: failed to query endpoint B ({args.endpoint_b}): {exc}", file=sys.stderr)
        return 2

    print(f"Total triples A: {grand_total_a}  ({args.endpoint_a})")
    print(f"Total triples B: {grand_total_b}  ({args.endpoint_b})")
    print(f"delta(B-A): {grand_total_b - grand_total_a:+d}")
    print()

    try:
        counts_a = fetch_counts(args.endpoint_a, args.timeout, ignore_prefixes)
    except Exception as exc:
        print(f"error: failed to query endpoint A ({args.endpoint_a}): {exc}", file=sys.stderr)
        return 2
    try:
        counts_b = fetch_counts(args.endpoint_b, args.timeout, ignore_prefixes)
    except Exception as exc:
        print(f"error: failed to query endpoint B ({args.endpoint_b}): {exc}", file=sys.stderr)
        return 2

    all_predicates = sorted(set(counts_a) | set(counts_b))
    diffs = []
    for p in all_predicates:
        a = counts_a.get(p)
        b = counts_b.get(p)
        if a != b:
            diffs.append((p, a, b))

    total_a = sum(counts_a.values())
    total_b = sum(counts_b.values())
    print(f"Endpoint A: {len(counts_a)} predicates, {total_a} triples  ({args.endpoint_a})")
    print(f"Endpoint B: {len(counts_b)} predicates, {total_b} triples  ({args.endpoint_b})")
    print()

    if not diffs:
        print("MATCH: all predicate counts are identical.")
        return 0

    width = max(len(p) for p, _, _ in diffs)
    print(f"{len(diffs)} predicate(s) with differing counts:")
    print(f"{'predicate'.ljust(width)}  {'A':>12}  {'B':>12}  {'delta(B-A)':>12}")
    print("-" * (width + 42))
    for p, a, b in diffs:
        a_s = "-" if a is None else str(a)
        b_s = "-" if b is None else str(b)
        if a is None or b is None:
            delta = "missing"
        else:
            delta = f"{b - a:+d}"
        print(f"{p.ljust(width)}  {a_s:>12}  {b_s:>12}  {delta:>12}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
