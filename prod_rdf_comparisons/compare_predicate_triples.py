#!/usr/bin/env python3
"""Show the differing triples for a given predicate between two SPARQL endpoints.

Runs `SELECT ?s ?o WHERE { ?s <predicate> ?o }` against each endpoint and reports
the subject/object pairs present in one store but not the other.

Usage:
    ./compare_predicate_triples.py <endpoint_a> <endpoint_b> <predicate>
    ./compare_predicate_triples.py http://host-a:3030/ds/sparql http://host-b:3030/ds/sparql http://www.w3.org/2000/01/rdf-schema#label

Options:
    --timeout <secs>   Per-request timeout (default: very large).
    --limit <n>        Only print the first <n> differing triples per side.

Exit status: 0 if the triple sets match, 1 if any differ, 2 on error.
"""

import argparse
import sys
import urllib.parse
import urllib.request


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


def term_key(binding):
    """Build a comparable, hashable key for a SPARQL result term.

    Includes the value plus the discriminating attributes (type, datatype,
    language) so that, e.g., a plain literal and a typed literal with the same
    lexical form are not treated as equal.
    """
    if binding is None:
        return None
    return (
        binding.get("type"),
        binding.get("value"),
        binding.get("datatype"),
        binding.get("xml:lang"),
    )


def fetch_pairs(endpoint, predicate, timeout):
    """Return {(subject_key, object_key): (subject_binding, object_binding)}.

    The keys are used for set comparison; the bindings are kept for display.
    """
    query = "SELECT ?s ?o WHERE { ?s <%s> ?o }" % predicate
    payload = run_query(endpoint, query, timeout)

    pairs = {}
    for row in payload["results"]["bindings"]:
        if "s" not in row or "o" not in row:
            continue
        s = row["s"]
        o = row["o"]
        pairs[(term_key(s), term_key(o))] = (s, o)
    return pairs


def fmt_term(binding):
    """Render a SPARQL result term roughly as it would appear in Turtle."""
    if binding is None:
        return "-"
    t = binding.get("type")
    value = binding.get("value", "")
    if t == "uri":
        return "<%s>" % value
    if t in ("literal", "typed-literal"):
        lang = binding.get("xml:lang")
        datatype = binding.get("datatype")
        out = '"%s"' % value
        if lang:
            out += "@" + lang
        elif datatype:
            out += "^^<%s>" % datatype
        return out
    if t == "bnode":
        return "_:%s" % value
    return value


def main():
    parser = argparse.ArgumentParser(
        description="Show the differing triples for a given predicate between two SPARQL endpoints."
    )
    parser.add_argument("endpoint_a", help="SPARQL query URL of the first store")
    parser.add_argument("endpoint_b", help="SPARQL query URL of the second store")
    parser.add_argument("predicate", help="Predicate IRI to compare triples for")
    parser.add_argument("--timeout", type=int, default=9000000, help="Per-request timeout in seconds")
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        metavar="N",
        help="Only print the first N differing triples per side",
    )
    args = parser.parse_args()

    try:
        pairs_a = fetch_pairs(args.endpoint_a, args.predicate, args.timeout)
    except Exception as exc:
        print(f"error: failed to query endpoint A ({args.endpoint_a}): {exc}", file=sys.stderr)
        return 2
    try:
        pairs_b = fetch_pairs(args.endpoint_b, args.predicate, args.timeout)
    except Exception as exc:
        print(f"error: failed to query endpoint B ({args.endpoint_b}): {exc}", file=sys.stderr)
        return 2

    print(f"Predicate: {args.predicate}")
    print(f"Endpoint A: {len(pairs_a)} triples  ({args.endpoint_a})")
    print(f"Endpoint B: {len(pairs_b)} triples  ({args.endpoint_b})")
    print(f"delta(B-A): {len(pairs_b) - len(pairs_a):+d}")
    print()

    only_a_keys = set(pairs_a) - set(pairs_b)
    only_b_keys = set(pairs_b) - set(pairs_a)

    if not only_a_keys and not only_b_keys:
        print("MATCH: triple sets are identical.")
        return 0

    def print_section(title, keys, pairs):
        rows = sorted(
            (fmt_term(pairs[k][0]), fmt_term(pairs[k][1])) for k in keys
        )
        shown = rows if args.limit is None else rows[: args.limit]
        print(f"{title}: {len(rows)} triple(s)" + (
            f" (showing {len(shown)})" if len(shown) != len(rows) else ""
        ))
        for s, o in shown:
            print(f"  {s}  {o}")
        print()

    print_section("Only in A (missing from B)", only_a_keys, pairs_a)
    print_section("Only in B (missing from A)", only_b_keys, pairs_b)
    return 1


if __name__ == "__main__":
    sys.exit(main())
