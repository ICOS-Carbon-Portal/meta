#!/usr/bin/env python3

import argparse
import json
import re
import requests
import sys
import time
import statistics
from dataclasses import dataclass
from typing import List, Set, Optional
from urllib.parse import unquote


@dataclass
class QueryResult:
    """Track individual query execution results"""
    index: int
    query: str
    success: bool
    elapsed_time: float
    result_count: int = 0
    error: Optional[str] = None


def rewrite_distinct_keywords(query):
    """Apply distinct_keywords rewrite"""
    return query.replace("select (cpmeta:distinct_keywords() as ?keywords)", "select ?spec")


def normalize_json_value(value):
    """
    Normalize a JSON field value by applying URL decoding and timestamp normalization.

    Args:
        value: String value from JSON binding

    Returns:
        Normalized string value
    """
    if value is None:
        return value

    # URL decode
    value = unquote(value)

    # Timestamp normalization: replace "+00:00" with "Z"
    value = value.replace("+00:00", "Z")

    # Timestamp normalization: remove milliseconds (e.g., "2023-01-01T12:00:00.123Z" -> "2023-01-01T12:00:00Z")
    value = re.sub(r'(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})\.\d+(Z)', r'\1\2', value)

    return value


def normalize_json_response(json_str):
    """
    Parse JSON response and normalize all field values.

    Args:
        json_str: JSON string from SPARQL endpoint

    Returns:
        Normalized JSON object, or None if parsing fails
    """
    try:
        data = json.loads(json_str)

        # SPARQL JSON format: {"head": {"vars": [...]}, "results": {"bindings": [...]}}
        if 'results' in data and 'bindings' in data['results']:
            for binding in data['results']['bindings']:
                for var, value_obj in binding.items():
                    if 'value' in value_obj:
                        value_obj['value'] = normalize_json_value(value_obj['value'])

        return data
    except json.JSONDecodeError as e:
        return None


def run_query(query, host='http://localhost:65432/sparql', accept_header='application/csv'):
    """
    Execute a SPARQL query against an endpoint.

    Args:
        query: SPARQL query string
        host: SPARQL endpoint URL
        accept_header: Accept header for response format

    Returns:
        Tuple of (success, response_text, error_message, elapsed_time)
    """
    headers = {
        'accept': accept_header,
        'content-type': 'application/sparql-query'
    }

    try:
        start_time = time.time()
        response = requests.post(host, data=query, headers=headers)
        elapsed_time = time.time() - start_time

        # Check if the request was successful (status code 2xx)
        if response.status_code >= 200 and response.status_code < 300:
            return True, response.text, None, elapsed_time
        else:
            return False, response.text, f"HTTP {response.status_code}", elapsed_time
    except Exception as e:
        return False, "", str(e), 0


def extract_queries(input_file):
    """
    Extract individual SPARQL queries from the log file.

    Queries are separated by lines of dashes (---) or equals signs (===).
    Returns a list of query strings.
    """
    queries = []
    current_query = []

    with open(input_file, 'r') as f:
        for line in f:
            stripped = line.rstrip()

            # Check if this is a separator line (all dashes or all equals)
            if stripped and (all(c == '-' for c in stripped) or all(c == '=' for c in stripped)):
                # If we have accumulated query lines, save the query
                if current_query:
                    query_text = '\n'.join(current_query)
                    if query_text.strip():  # Only add non-empty queries
                        queries.append(query_text)
                    current_query = []
            else:
                # Accumulate lines that are part of a query
                current_query.append(line.rstrip())

    # Don't forget the last query if file doesn't end with a separator
    if current_query:
        query_text = '\n'.join(current_query)
        if query_text.strip():
            queries.append(query_text)

    return queries


def print_query_status(result: QueryResult, verbose: bool = False):
    """Print single-line status for a query execution"""
    if verbose:
        print(f"\n{'='*80}")
        print(f"Query #{result.index}")
        print(f"{'='*80}")
        # Print first 3 lines of query
        query_lines = result.query.strip().split('\n')
        for line in query_lines[:3]:
            print(line)
        if len(query_lines) > 3:
            print("[query text truncated...]")
        print()

    if result.success:
        print(f"Query #{result.index}: ✓ Success ({result.elapsed_time:.3f}s, {result.result_count} results)")
    else:
        error_msg = result.error if result.error else "Unknown error"
        print(f"Query #{result.index}: ✗ Failed ({error_msg})")


def print_execution_summary(results: List[QueryResult], endpoint: str, total_queries: int, skipped_count: int):
    """Print overall execution statistics"""
    executed = len(results)
    successful = sum(1 for r in results if r.success)
    failed = sum(1 for r in results if not r.success)

    failed_indices = [r.index for r in results if not r.success]

    total_time = sum(r.elapsed_time for r in results)
    avg_time = total_time / executed if executed > 0 else 0

    print(f"\n{'='*80}")
    print("EXECUTION SUMMARY")
    print(f"{'='*80}")
    print(f"Endpoint: {endpoint}")
    print(f"Total queries: {total_queries}")
    print(f"Executed: {executed}")
    if skipped_count > 0:
        print(f"Skipped: {skipped_count}")
    print()
    print(f"Successful: {successful} ({100*successful/executed:.1f}%)" if executed > 0 else "Successful: 0")
    print(f"Failed: {failed} ({100*failed/executed:.1f}%)" if executed > 0 else "Failed: 0")

    if failed_indices:
        print(f"Failed query indices: {', '.join(map(str, failed_indices))}")

    print()
    print(f"Total execution time: {total_time:.3f}s")
    print(f"Average time per query: {avg_time:.3f}s")
    print(f"{'='*80}")


def print_runtime_summary(results: List[QueryResult]):
    """Print detailed runtime statistics and top 10 slowest queries"""
    if not results:
        print("\nNo queries were executed.")
        return

    # Filter only successful queries for runtime stats
    successful_results = [r for r in results if r.success]

    if not successful_results:
        print("\nNo successful queries to analyze.")
        return

    print(f"\n{'='*80}")
    print("RUNTIME STATISTICS")
    print(f"{'='*80}")

    # Calculate statistics
    runtimes = [r.elapsed_time for r in successful_results]
    min_result = min(successful_results, key=lambda x: x.elapsed_time)
    max_result = max(successful_results, key=lambda x: x.elapsed_time)
    avg_runtime = statistics.mean(runtimes)
    median_runtime = statistics.median(runtimes)

    # Calculate 95th percentile
    sorted_runtimes = sorted(runtimes)
    p95_index = int(len(sorted_runtimes) * 0.95)
    p95_runtime = sorted_runtimes[p95_index] if p95_index < len(sorted_runtimes) else sorted_runtimes[-1]

    print()
    print("Query Performance:")
    print(f"  Fastest: {min_result.elapsed_time:.3f}s (Query #{min_result.index})")
    print(f"  Slowest: {max_result.elapsed_time:.3f}s (Query #{max_result.index})")
    print(f"  Average: {avg_runtime:.3f}s")
    print(f"  Median:  {median_runtime:.3f}s")
    print(f"  95th percentile: {p95_runtime:.3f}s")

    # Top 10 slowest queries
    sorted_results = sorted(successful_results, key=lambda x: x.elapsed_time, reverse=True)
    top_n = min(10, len(sorted_results))

    print(f"\nTop {top_n} Slowest Queries:")
    for rank, result in enumerate(sorted_results[:top_n], start=1):
        print(f"  {rank:2d}. Query #{result.index}: {result.elapsed_time:.3f}s")

    # Result size distribution
    empty_results = sum(1 for r in successful_results if r.result_count == 0)
    small_results = sum(1 for r in successful_results if 1 <= r.result_count <= 10)
    medium_results = sum(1 for r in successful_results if 11 <= r.result_count <= 100)
    large_results = sum(1 for r in successful_results if r.result_count > 100)

    print("\nResult Size Distribution:")
    print(f"  Empty results (0 bindings): {empty_results} queries")
    print(f"  Small results (1-10 bindings): {small_results} queries")
    print(f"  Medium results (11-100 bindings): {medium_results} queries")
    print(f"  Large results (>100 bindings): {large_results} queries")
    print(f"{'='*80}")


def save_failed_queries(results: List[QueryResult], output_file: str):
    """Save failed queries in reusable format"""
    with open(output_file, 'w') as f:
        for result in results:
            # Write comment header with query number
            f.write(f"# Query #{result.index}\n")
            if result.error:
                f.write(f"# Error: {result.error}\n")
            # Write the query
            f.write(f"{result.query.strip()}\n")
            # Write separator
            f.write(f"{'='*80}\n")

    print(f"\n✓ Saved {len(results)} failed queries to: {output_file}")


def main(input_file, endpoint, skip_indexes=None, verbose=False, save_failed=None):
    """Main execution function"""
    if skip_indexes is None:
        skip_indexes = set()

    print(f"Reading queries from: {input_file}")

    try:
        queries = extract_queries(input_file)
    except FileNotFoundError:
        print(f"Error: Input file not found: {input_file}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"Error reading input file: {e}", file=sys.stderr)
        sys.exit(1)

    if not queries:
        print("Warning: No queries found in input file")
        return

    print(f"Found {len(queries)} queries")
    if skip_indexes:
        print(f"Skipping queries: {', '.join(map(str, sorted(skip_indexes)))}")
    print(f"Endpoint: {endpoint}")
    print()

    # Execute each query
    results = []
    skipped_count = 0

    for idx, query in enumerate(queries, start=1):
        if idx in skip_indexes:
            if verbose:
                print(f"Query #{idx}: SKIPPED")
            skipped_count += 1
            continue

        query = rewrite_distinct_keywords(query)
        success, response, error, elapsed = run_query(query, endpoint, 'application/sparql-results+json')

        # Parse result count
        result_count = 0
        if success:
            json_data = normalize_json_response(response)
            if json_data:
                result_count = len(json_data.get('results', {}).get('bindings', []))

        result = QueryResult(idx, query, success, elapsed, result_count, error)
        results.append(result)
        print_query_status(result, verbose)

    # Print summaries
    print_execution_summary(results, endpoint, len(queries), skipped_count)
    print_runtime_summary(results)

    # Save failed queries if requested
    if save_failed:
        failed = [r for r in results if not r.success]
        if failed:
            save_failed_queries(failed, save_failed)
        else:
            print(f"\n✓ No failed queries to save")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(
        description='Run SPARQL queries against a single endpoint and track performance'
    )

    parser.add_argument(
        'input_file',
        help='Path to file containing SPARQL queries (separated by --- or ===)'
    )

    parser.add_argument(
        '--endpoint', '-e',
        default='http://localhost:65432/sparql',
        help='SPARQL endpoint URL (default: http://localhost:65432/sparql)'
    )

    parser.add_argument(
        '--skip', '-s',
        default='',
        help='Comma-separated list of query indexes to skip (e.g., "1,3,5")'
    )

    parser.add_argument(
        '--verbose', '-v',
        action='store_true',
        help='Show query text preview for each execution'
    )

    parser.add_argument(
        '--save-failed',
        metavar='FILE',
        help='Save failed queries to FILE in reusable format'
    )

    args = parser.parse_args()

    # Parse skip list
    skip_indexes = set()
    if args.skip:
        try:
            skip_indexes = set(int(idx.strip()) for idx in args.skip.split(',') if idx.strip())
        except ValueError as e:
            print(f"Error: Invalid query index in skip list. Must be comma-separated integers.", file=sys.stderr)
            print(f"Example: --skip 1,3,5", file=sys.stderr)
            sys.exit(1)

    main(args.input_file, args.endpoint, skip_indexes, args.verbose, args.save_failed)
    print("\nDONE")
