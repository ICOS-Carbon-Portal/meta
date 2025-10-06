#!/usr/bin/env python3
"""
Script to process SPARQL log files: deduplicate and format.
Outputs formatted, deduplicated queries to stdout.
"""

import sys
import argparse
from difflib import SequenceMatcher


def calculate_similarity(a, b):
    """Calculate similarity ratio between two strings."""
    return SequenceMatcher(None, a, b).ratio()


def is_similar_to_any(query, seen_queries, threshold):
    """Check if query is similar to any previously seen query."""
    for seen_query in seen_queries:
        if calculate_similarity(query, seen_query) >= threshold:
            return True
    return False


def deduplicate_entries(entries, similarity_threshold=None, verbose=False):
    """Deduplicate a list of entries.

    Args:
        entries: List of log entries
        similarity_threshold: Float between 0 and 1 for similarity matching
        verbose: Show progress information

    Returns:
        Tuple of (unique_entries, stats_dict)
    """
    seen_exact = set()
    seen_queries = []
    unique_entries = []
    exact_duplicates = 0
    similar_duplicates = 0

    for i, content in enumerate(entries):
        if verbose and (i + 1) % 100 == 0:
            print(f"Processing entry {i + 1}/{len(entries)}...", file=sys.stderr)

        # Handle separator lines separately
        if content == '-':
            if '-' not in seen_exact:
                unique_entries.append(content)
                seen_exact.add('-')
            continue

        # Check for exact duplicates first
        if content in seen_exact:
            exact_duplicates += 1
            continue

        # Check for similar duplicates if threshold is set
        if similarity_threshold is not None:
            if is_similar_to_any(content, seen_queries, similarity_threshold):
                similar_duplicates += 1
                continue

        # This is a unique entry
        unique_entries.append(content)
        seen_exact.add(content)
        if similarity_threshold is not None:
            seen_queries.append(content)

    stats = {
        'total': len(entries),
        'unique': len(unique_entries),
        'exact_duplicates': exact_duplicates,
        'similar_duplicates': similar_duplicates
    }

    return unique_entries, stats


def format_entry(entry):
    """Format a single entry by replacing escape sequences.

    Args:
        entry: Raw log entry string

    Returns:
        Formatted entry string
    """
    # Handle separator lines
    if entry == '-':
        return '\n' + '='*80 + '\n'

    # Replace escape sequences
    formatted = entry.replace('\\x0A', '\n')
    formatted = formatted.replace('\\x09', '\t')
    formatted = formatted.replace('\\x22', '"')

    return formatted


def process_log(input_file, output_file=None, similarity_threshold=0.70, verbose=False):
    """Process log file: deduplicate and format, output to stdout or file.

    Args:
        input_file: Path to input log file
        output_file: Optional path to output file. If None, outputs to stdout
        similarity_threshold: Float between 0 and 1 for similarity matching
        verbose: Show progress information
    """
    # Read all entries
    print(f"Reading entries from {input_file}...", file=sys.stderr)
    entries = []
    with open(input_file, 'r') as f:
        for line in f:
            content = line.strip()
            if content:  # Skip empty lines
                entries.append(content)

    print(f"Read {len(entries)} entries", file=sys.stderr)

    # Deduplicate
    print("Deduplicating...", file=sys.stderr)
    unique_entries, stats = deduplicate_entries(entries, similarity_threshold, verbose)

    # Print statistics to stderr
    print(f"\n--- Deduplication Statistics ---", file=sys.stderr)
    print(f"Total entries: {stats['total']}", file=sys.stderr)
    print(f"Unique entries: {stats['unique']}", file=sys.stderr)
    print(f"Exact duplicates removed: {stats['exact_duplicates']}", file=sys.stderr)
    if similarity_threshold is not None:
        print(f"Similar duplicates removed (threshold={similarity_threshold}): {stats['similar_duplicates']}", file=sys.stderr)
    print(f"Total duplicates removed: {stats['exact_duplicates'] + stats['similar_duplicates']}", file=sys.stderr)
    print(f"--------------------------------\n", file=sys.stderr)

    # Determine output destination
    if output_file:
        print(f"Writing {stats['unique']} unique entries to {output_file}...", file=sys.stderr)
        out_f = open(output_file, 'w')
    else:
        print(f"Formatting and outputting {stats['unique']} unique entries to stdout...", file=sys.stderr)
        out_f = sys.stdout

    try:
        # Format and output
        for entry in unique_entries:
            formatted = format_entry(entry)
            print(formatted, file=out_f)
            if entry != '-':  # Don't add separator after separator line
                print('\n' + '-'*80 + '\n', file=out_f)
    finally:
        if output_file:
            out_f.close()
            print(f"Output written to {output_file}", file=sys.stderr)

    print("Done!", file=sys.stderr)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(
        description='Process SPARQL log: deduplicate and format to stdout'
    )
    parser.add_argument(
        'input_file',
        nargs='?',
        default='sparql_partial.log',
        help='Input log file (default: sparql_partial.log)'
    )
    parser.add_argument(
        '-s', '--similarity',
        type=float,
        default=0.70,
        help='Similarity threshold (0.0-1.0). Queries with similarity >= threshold are considered duplicates (default: 0.70)'
    )
    parser.add_argument(
        '--exact-only',
        action='store_true',
        help='Only remove exact duplicates, disable similarity matching'
    )
    parser.add_argument(
        '-o', '--output',
        type=str,
        help='Output file path. If not specified, outputs to stdout'
    )
    parser.add_argument(
        '-v', '--verbose',
        action='store_true',
        help='Show verbose progress information during processing'
    )

    args = parser.parse_args()

    # Set similarity threshold to None if exact-only mode
    similarity_threshold = None if args.exact_only else args.similarity

    process_log(args.input_file, args.output, similarity_threshold, args.verbose)
