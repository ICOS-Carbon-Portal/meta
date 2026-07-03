#!/usr/bin/env python3
"""Batch per-predicate triple diff between two SPARQL endpoints.

Non-interactive counterpart to ``compare_browser.py``. It:

  1. Fetches per-predicate triple counts from both endpoints and reports the
     count difference for every predicate whose counts disagree.
  2. For each differing predicate, fetches the actual subject/object pairs and
     reports the triples present in only one store.
  3. Writes the whole result to a JSON file for later visualisation.

Usage:
    ./compare_batch.py <endpoint_a> <endpoint_b>
    ./compare_batch.py <endpoint_a> <endpoint_b> --output result.json

Options:
    --timeout <secs>            Per-request timeout (default: very large).
    --ignore-prefix <iri>       Ignore predicates whose IRI starts with this
                                prefix (repeatable).
    --ignore-triple-prefix <iri>
                                Ignore triples whose subject IRI starts with
                                this prefix (repeatable).
    --output <path>             JSON output file (default: compare_result.json).
    --max-triples <n>           Cap the differing triples stored/printed per side
                                for each predicate (default: no cap).

Exit status: 0 if all predicate counts match, 1 if any differ, 2 on error.
"""

import argparse
import json
import re
import sys

import compare_predicate_counts as counts_mod
import compare_predicate_triples as triples_mod


# Matches the fractional-seconds part of an ISO-8601-ish timestamp, e.g. the
# ".123456" in "2020-01-01T12:00:00.123456Z", so it can be stripped.
_FRACTIONAL_SECONDS_RE = re.compile(r"(T\d{2}:\d{2}:\d{2})\.\d+")


def _looks_like_timestamp(datatype, value):
    """Heuristically decide whether a literal is a date/time value."""
    return datatype and ("dateTime" in datatype or "dateTimeStamp" in datatype)


def _cmp_term_key(binding):
    """Comparison key for a term, truncating timestamps to whole seconds.

    Extends ``compare_predicate_triples.term_key`` so that timestamps that
    differ only in sub-second precision compare equal.
    """
    key = triples_mod.term_key(binding)
    if key is None:
        return None
    type_, value, datatype, lang = key
    if value and _looks_like_timestamp(datatype, value):
        value = _FRACTIONAL_SECONDS_RE.sub(r"\1", value)
    return (type_, value, datatype, lang)


def _normalize_pairs(pairs):
    """Re-key pairs with timestamp-truncated object keys (collapsing sub-second dupes).

    Only object values are truncated; subject keys are left as-is.
    """
    kept = {}
    for (sk, ok), (s, o) in pairs.items():
        kept[(sk, _cmp_term_key(o))] = (s, o)
    return kept


# Triple subject prefixes ignored by default in the per-predicate triple diff.
# Override/extend with --ignore-triple-prefix.
DEFAULT_IGNORE_TRIPLE_PREFIXES = [
    "http://www.w3.org/2002/07/owl",
    "http://localhost:8890/DAV",
    "https://www.w3.org/ns/activitystreams"
]


def fetch_predicate_diffs(endpoint_a, endpoint_b, timeout, ignore_prefixes):
    """Return (totals, diffs) where diffs is a list of (predicate, a, b)."""
    grand_total_a = counts_mod.fetch_total(endpoint_a, timeout)
    grand_total_b = counts_mod.fetch_total(endpoint_b, timeout)
    counts_a = counts_mod.fetch_counts(endpoint_a, timeout, ignore_prefixes)
    counts_b = counts_mod.fetch_counts(endpoint_b, timeout, ignore_prefixes)

    diffs = []
    for p in sorted(set(counts_a) | set(counts_b)):
        a = counts_a.get(p)
        b = counts_b.get(p)
        if a != b:
            diffs.append((p, a, b))

    totals = {
        "grand_a": grand_total_a,
        "grand_b": grand_total_b,
        "preds_a": len(counts_a),
        "preds_b": len(counts_b),
    }
    return totals, diffs


def _drop_ignored_subjects(pairs, ignore_triple_prefixes):
    """Drop pairs whose subject IRI starts with an ignored prefix."""
    if not ignore_triple_prefixes:
        return pairs
    kept = {}
    for key, (s, o) in pairs.items():
        subject = s.get("value", "")
        if any(subject.startswith(prefix) for prefix in ignore_triple_prefixes):
            continue
        kept[key] = (s, o)
    return kept


def differing_triples(endpoint_a, endpoint_b, predicate, timeout, max_triples,
                      ignore_triple_prefixes=()):
    """Return a dict describing the triples that differ for one predicate."""
    pairs_a = triples_mod.fetch_pairs(endpoint_a, predicate, timeout)
    pairs_b = triples_mod.fetch_pairs(endpoint_b, predicate, timeout)

    pairs_a = _drop_ignored_subjects(pairs_a, ignore_triple_prefixes)
    pairs_b = _drop_ignored_subjects(pairs_b, ignore_triple_prefixes)

    pairs_a = _normalize_pairs(pairs_a)
    pairs_b = _normalize_pairs(pairs_b)

    def collect(pairs, keys):
        rows = sorted(
            (triples_mod.fmt_term(pairs[k][0]), triples_mod.fmt_term(pairs[k][1]))
            for k in keys
        )
        total = len(rows)
        if max_triples is not None:
            rows = rows[:max_triples]
        return total, [{"subject": s, "object": o} for s, o in rows]

    only_a_total, only_a = collect(pairs_a, set(pairs_a) - set(pairs_b))
    only_b_total, only_b = collect(pairs_b, set(pairs_b) - set(pairs_a))

    return {
        "count_a": len(pairs_a),
        "count_b": len(pairs_b),
        "only_in_a_total": only_a_total,
        "only_in_b_total": only_b_total,
        "only_in_a": only_a,
        "only_in_b": only_b,
    }


def main():
    parser = argparse.ArgumentParser(
        description="Batch per-predicate triple diff between two SPARQL endpoints."
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
    parser.add_argument(
        "--ignore-triple-prefix",
        action="append",
        default=[],
        metavar="IRI",
        help="Ignore triples whose subject IRI starts with this prefix "
             "(repeatable). Default: " + ", ".join(DEFAULT_IGNORE_TRIPLE_PREFIXES),
    )
    parser.add_argument(
        "--output",
        default="compare_result.json",
        metavar="PATH",
        help="JSON output file (default: compare_result.json)",
    )
    parser.add_argument(
        "--max-triples",
        type=int,
        default=None,
        metavar="N",
        help="Cap differing triples stored/printed per side per predicate",
    )
    args = parser.parse_args()

    ignore_prefixes = counts_mod.DEFAULT_IGNORE_PREFIXES + args.ignore_prefix
    ignore_triple_prefixes = DEFAULT_IGNORE_TRIPLE_PREFIXES + args.ignore_triple_prefix

    # -- Step 1: per-predicate counts --------------------------------------
    print("Fetching predicate counts (this may take a while)...", file=sys.stderr)
    try:
        totals, diffs = fetch_predicate_diffs(
            args.endpoint_a, args.endpoint_b, args.timeout, ignore_prefixes
        )
    except Exception as exc:
        print(f"error: failed to fetch counts: {exc}", file=sys.stderr)
        return 2

    print(f"Total triples A: {totals['grand_a']}  ({args.endpoint_a})")
    print(f"Total triples B: {totals['grand_b']}  ({args.endpoint_b})")
    print(f"delta(B-A): {totals['grand_b'] - totals['grand_a']:+d}")
    print(f"Endpoint A: {totals['preds_a']} predicates")
    print(f"Endpoint B: {totals['preds_b']} predicates")
    print()

    result = {
        "endpoint_a": args.endpoint_a,
        "endpoint_b": args.endpoint_b,
        "totals": totals,
        "predicates": [],
    }

    if not diffs:
        print("MATCH: all predicate counts are identical.")
        with open(args.output, "w") as fh:
            json.dump(result, fh, indent=2)
        print(f"\nWrote {args.output}")
        return 0

    width = max(len(p) for p, _, _ in diffs)
    print(f"{len(diffs)} predicate(s) with differing counts:")
    print(f"{'predicate'.ljust(width)}  {'A':>12}  {'B':>12}  {'delta(B-A)':>12}")
    print("-" * (width + 42))
    for p, a, b in diffs:
        a_s = "-" if a is None else str(a)
        b_s = "-" if b is None else str(b)
        delta = "missing" if a is None or b is None else f"{b - a:+d}"
        print(f"{p.ljust(width)}  {a_s:>12}  {b_s:>12}  {delta:>12}")
    print()

    # -- Step 2: differing triples per predicate ---------------------------
    for i, (p, a, b) in enumerate(diffs, 1):
        print(f"[{i}/{len(diffs)}] fetching differing triples for {p} ...", file=sys.stderr)
        entry = {
            "predicate": p,
            "count_a": a,
            "count_b": b,
            "delta": (None if a is None or b is None else b - a),
        }
        try:
            entry["triples"] = differing_triples(
                args.endpoint_a, args.endpoint_b, p, args.timeout, args.max_triples,
                ignore_triple_prefixes,
            )
        except Exception as exc:
            entry["error"] = str(exc)
            print(f"  error: {exc}", file=sys.stderr)

        # Skip predicates whose only differences were ignored triples.
        tr = entry.get("triples")
        if tr and tr["only_in_a_total"] == 0 and tr["only_in_b_total"] == 0:
            print(f"    (no differing triples after ignored prefixes; skipped)",
                  file=sys.stderr)
            continue

        result["predicates"].append(entry)

        print(f"=== {p} ===")
        print(f"    A:{a}  B:{b}  delta(B-A):"
              + ("missing" if a is None or b is None else f"{b - a:+d}"))
        if tr:
            print(f"    Only in A (missing from B): {tr['only_in_a_total']} triple(s)"
                  + (f" (showing {len(tr['only_in_a'])})"
                     if len(tr['only_in_a']) != tr['only_in_a_total'] else ""))
            for row in tr["only_in_a"]:
                print(f"      {row['subject']}  {row['object']}")
            print(f"    Only in B (missing from A): {tr['only_in_b_total']} triple(s)"
                  + (f" (showing {len(tr['only_in_b'])})"
                     if len(tr['only_in_b']) != tr['only_in_b_total'] else ""))
            for row in tr["only_in_b"]:
                print(f"      {row['subject']}  {row['object']}")
        print()

    with open(args.output, "w") as fh:
        json.dump(result, fh, indent=2)
    print(f"Wrote {args.output}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
